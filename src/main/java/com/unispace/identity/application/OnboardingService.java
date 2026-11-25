package com.unispace.identity.application;

import com.unispace.administration.application.InstitutionalRegistrationPolicy;
import com.unispace.administration.application.InstitutionalRegistrationPolicy.Rules;
import com.unispace.identity.application.AuthenticationStateService.FlowStep;
import com.unispace.identity.application.AuthenticationStateService.RegistrationFlow;
import com.unispace.identity.persistence.OnboardingRepository;
import com.unispace.identity.persistence.OnboardingRepository.Profile;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OnboardingService {
    public record Progress(String accountStatus, String nextAction, Profile profile, String universityEmailMasked) {}
    public record IssuedCode(String challengeId, String maskedEmail, int expiresInSeconds, int resendAfterSeconds) {}
    public record VerifiedEmail(String accountStatus, boolean emailVerified, String nextAction) {}

    public enum Failure {
        ACCESS_DENIED, AUTH_FLOW_EXPIRED, VALIDATION_FAILED, STUDENT_ID_INVALID, STUDENT_ID_CONFLICT,
        EMAIL_DOMAIN_NOT_ALLOWED, EMAIL_CONFLICT, OTP_RATE_LIMITED, OTP_INVALID, OTP_EXPIRED,
        OTP_ATTEMPTS_EXCEEDED, EMAIL_SERVICE_UNAVAILABLE, AUTH_STATE_UNAVAILABLE
    }

    public static final class Rejected extends RuntimeException {
        private final Failure failure;
        public Rejected(Failure failure) { this.failure = failure; }
        public Failure failure() { return failure; }
    }

    private static final String PURPOSE = "REGISTRATION";
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@"
            + "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
            + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+");
    private final OnboardingRepository store;
    private final AuthenticationStateService auth;
    private final InstitutionalRegistrationPolicy policy;
    private final RegistrationCode codes;
    private final ObjectProvider<RegistrationEmailSender> sender;
    private final TransactionTemplate transaction;

    public OnboardingService(OnboardingRepository store, AuthenticationStateService auth,
                             InstitutionalRegistrationPolicy policy, RegistrationCode codes,
                             ObjectProvider<RegistrationEmailSender> sender, PlatformTransactionManager transactions) {
        this.store = store;
        this.auth = auth;
        this.policy = policy;
        this.codes = codes;
        this.sender = sender;
        this.transaction = new TransactionTemplate(transactions);
    }

    public Progress progress(RegistrationFlow flow) {
        return transaction.execute(status -> progress(flow, lockStep(flow)));
    }

    public Progress saveProfile(RegistrationFlow flow, String firstName, String lastName, String studentId) {
        var profile = new Profile(clean(firstName, 100, Failure.VALIDATION_FAILED),
                clean(lastName, 100, Failure.VALIDATION_FAILED), clean(studentId, 64, Failure.STUDENT_ID_INVALID));
        return transaction.execute(status -> {
            var step = lockStep(flow);
            checkStudentId(profile.studentId(), rules());
            try {
                store.saveProfile(flow.userId(), profile);
            } catch (DuplicateKeyException e) {
                throw new Rejected(Failure.STUDENT_ID_CONFLICT);
            }
            return progress(flow, step == FlowStep.PROFILE_REQUIRED ? FlowStep.EMAIL_REQUIRED : step);
        });
    }

    public IssuedCode issue(RegistrationFlow flow, String input) {
        String email = clean(input, 254, Failure.VALIDATION_FAILED);
        if (!EMAIL.matcher(email).matches() || email.indexOf('@') > 64) throw new Rejected(Failure.VALIDATION_FAILED);
        var mail = sender.getIfAvailable();
        if (mail == null) throw new Rejected(Failure.EMAIL_SERVICE_UNAVAILABLE);
        var key = codes.key();
        String code = codes.generate();
        var id = transaction.execute(status -> {
            requireEmailStep(lockStep(flow));
            checkEmailDomain(email, rules());
            var now = store.now();
            if (store.coolingDown(flow.userId(), PURPOSE, now)) throw new Rejected(Failure.OTP_RATE_LIMITED);
            return store.reserve(flow, PURPOSE, email, now);
        });
        try {
            // External delivery never runs inside the reservation or confirmation transaction.
            mail.send(email, code);
        } catch (RegistrationEmailSender.DeliveryFailed failure) {
            transaction.executeWithoutResult(status -> {
                store.lock(flow);
                store.failedDelivery(flow, PURPOSE, id);
            });
            throw new Rejected(Failure.EMAIL_SERVICE_UNAVAILABLE);
        }
        transaction.executeWithoutResult(status -> {
            requireEmailStep(lockStep(flow));
            if (!store.delivered(flow, PURPOSE, id, codes.digest(key, flow, id, email, code))) {
                throw new Rejected(Failure.EMAIL_SERVICE_UNAVAILABLE);
            }
        });
        return new IssuedCode(id.toString(), mask(email), 600, 60);
    }

    public VerifiedEmail verify(RegistrationFlow flow, String challengeId, String code) {
        if (!challengeId.matches("[1-9][0-9]{0,19}")) throw new Rejected(Failure.OTP_INVALID);
        BigInteger id = new BigInteger(challengeId);
        if (id.bitLength() > 64) throw new Rejected(Failure.OTP_INVALID);
        var key = codes.key();
        // Returning a verification failure commits the attempt counter. Throw only after the transaction returns.
        Failure failure = transaction.execute(status -> {
            requireEmailStep(lockStep(flow));
            var challenge = store.challenge(flow, PURPOSE, id).orElseThrow(() -> new Rejected(Failure.OTP_INVALID));
            if (challenge.attempts() >= 5) return Failure.OTP_ATTEMPTS_EXCEEDED;
            if (challenge.invalidated() || challenge.consumed() || challenge.digest().length != 32) {
                return Failure.OTP_INVALID;
            }
            var now = store.now();
            if (challenge.createdAt().isAfter(now) || !challenge.expiresAt().isAfter(now)) return Failure.OTP_EXPIRED;
            if (!MessageDigest.isEqual(challenge.digest(), codes.digest(key, flow, id, challenge.email(), code))) {
                int attempts = challenge.attempts() + 1;
                store.recordFailure(id, attempts, now);
                return attempts == 5 ? Failure.OTP_ATTEMPTS_EXCEEDED : Failure.OTP_INVALID;
            }

            var rules = rules();
            var profile = store.profile(flow.userId());
            clean(profile.firstName(), 100, Failure.VALIDATION_FAILED);
            clean(profile.lastName(), 100, Failure.VALIDATION_FAILED);
            checkStudentId(profile.studentId(), rules);
            checkEmailDomain(challenge.email(), rules);
            var verifiedAt = store.now();
            if (!challenge.expiresAt().isAfter(verifiedAt)) return Failure.OTP_EXPIRED;
            try {
                store.activate(flow, id, challenge.email(), challenge.email().toLowerCase(Locale.ROOT), verifiedAt);
            } catch (DuplicateKeyException e) {
                throw new Rejected(Failure.EMAIL_CONFLICT);
            }
            store.establishEnrollment(flow);
            return null;
        });
        if (failure != null) throw new Rejected(failure);
        return new VerifiedEmail("ACTIVE", true, "DEVICE_KEY_REGISTRATION_REQUIRED");
    }

    private FlowStep lockStep(RegistrationFlow flow) {
        store.lock(flow);
        var step = auth.flowStep(flow.userId(), flow.flowId(), flow.jtiDigest())
                .orElseThrow(() -> new Rejected(Failure.AUTH_FLOW_EXPIRED));
        if (step == FlowStep.BEGIN_DEVICE_VERIFICATION) throw new Rejected(Failure.ACCESS_DENIED);
        return step;
    }

    private Progress progress(RegistrationFlow flow, FlowStep step) {
        String accountStatus = step == FlowStep.PROFILE_REQUIRED ? "PENDING_PROFILE" : "PENDING_EMAIL_VERIFICATION";
        return new Progress(accountStatus, step.name(), store.profile(flow.userId()),
                store.pendingEmail(flow.userId()).map(OnboardingService::mask).orElse(null));
    }

    private Rules rules() {
        return policy.current().orElseThrow(() -> new Rejected(Failure.AUTH_STATE_UNAVAILABLE));
    }

    private static void requireEmailStep(FlowStep step) {
        if (step != FlowStep.EMAIL_REQUIRED && step != FlowStep.EMAIL_VERIFICATION_REQUIRED) {
            throw new Rejected(Failure.ACCESS_DENIED);
        }
    }

    private static void checkStudentId(String id, Rules rules) {
        if (id == null || !rules.studentIdPattern().matcher(id).matches()) throw new Rejected(Failure.STUDENT_ID_INVALID);
    }

    private static void checkEmailDomain(String email, Rules rules) {
        String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        if (!rules.emailDomains().contains(domain)) throw new Rejected(Failure.EMAIL_DOMAIN_NOT_ALLOWED);
    }

    private static String clean(String value, int maxLength, Failure failure) {
        if (value == null) throw new Rejected(failure);
        String cleaned = value.strip();
        if (cleaned.isEmpty() || cleaned.codePointCount(0, cleaned.length()) > maxLength
                || cleaned.codePoints().anyMatch(Character::isISOControl)) throw new Rejected(failure);
        return cleaned;
    }

    private static String mask(String email) {
        return "***" + email.substring(email.lastIndexOf('@'));
    }
}
