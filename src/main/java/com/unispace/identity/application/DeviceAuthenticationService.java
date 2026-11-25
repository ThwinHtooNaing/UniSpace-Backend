package com.unispace.identity.application;

import com.unispace.identity.application.AuthenticationStateService.FlowStep;
import com.unispace.identity.application.AuthenticationStateService.RegistrationFlow;
import com.unispace.identity.persistence.DeviceAuthenticationRepository;
import com.unispace.identity.persistence.DeviceAuthenticationRepository.ActiveFlow;
import com.unispace.identity.persistence.DeviceAuthenticationRepository.Enrollment;
import com.unispace.identity.persistence.DeviceAuthenticationRepository.TrustedDevice;
import com.unispace.identity.persistence.OnboardingRepository;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DeviceAuthenticationService {
    private static final String PURPOSE = "NEW_DEVICE";
    private static final String REGISTER_KEY = "DEVICE_KEY_REGISTRATION_REQUIRED";
    private static final String VERIFY_OTP = "DEVICE_OTP_REQUIRED";
    private static final String VERIFY_PROOF = "DEVICE_PROOF_REQUIRED";
    private static final HexFormat HEX = HexFormat.of();

    public enum Failure {
        AUTH_FLOW_EXPIRED, ACCESS_DENIED, DEVICE_CHALLENGE_INVALID, DEVICE_CHALLENGE_INVALID_OR_EXPIRED,
        DEVICE_SIGNATURE_INVALID, DEVICE_TRUST_REVOKED, DEVICE_KEY_INVALID, DEVICE_KEY_CONFLICT,
        DEVICE_VERIFICATION_REQUIRED, OTP_RATE_LIMITED, OTP_INVALID, OTP_EXPIRED, OTP_ATTEMPTS_EXCEEDED,
        EMAIL_SERVICE_UNAVAILABLE, AUTH_STATE_UNAVAILABLE
    }

    public static final class Rejected extends RuntimeException {
        private final Failure failure;
        public Rejected(Failure failure) { this.failure = failure; }
        public Failure failure() { return failure; }
    }

    private record PendingMail(BigInteger enrollment, BigInteger challenge, String email, String code) {
        @Override
        public String toString() { return "Device OTP delivery [redacted]"; }
    }
    private record Start(Map<String, Object> response, PendingMail mail) {}
    private final SecureRandom random = new SecureRandom();
    private final DeviceAuthenticationRepository devices;
    private final OnboardingRepository email;
    private final AuthenticationStateService auth;
    private final RegistrationCode codes;
    private final ObjectProvider<RegistrationEmailSender> sender;
    private final AccessTokenIssuer access;
    private final RefreshTokens refreshTokens;
    private final TransactionTemplate transaction;

    public DeviceAuthenticationService(DeviceAuthenticationRepository devices, OnboardingRepository email,
            AuthenticationStateService auth, RegistrationCode codes, ObjectProvider<RegistrationEmailSender> sender,
            AccessTokenIssuer access, RefreshTokens refreshTokens, PlatformTransactionManager transactions) {
        this.devices = devices;
        this.email = email;
        this.auth = auth;
        this.codes = codes;
        this.sender = sender;
        this.access = access;
        this.refreshTokens = refreshTokens;
        this.transaction = new TransactionTemplate(transactions);
    }

    public Map<String, Object> start(RegistrationFlow flow, String handle) {
        String hash = handle == null ? null : deviceHash(handle);
        Start step = transaction.execute(status -> {
            var current = lockFlow(flow);
            var enrollment = devices.enrollment(flow);
            if (enrollment.filter(Enrollment::verified).isPresent()) {
                return new Start(Map.of("challengeId", "e_" + enrollment.orElseThrow().id(), "nextAction", REGISTER_KEY), null);
            }
            if (hash != null) {
                var trusted = devices.trusted(flow, null, hash);
                if (trusted.isPresent()) {
                    var device = trusted.orElseThrow();
                    var key = trustedKey(device);
                    if (key != null) {
                        return new Start(proofChallenge(flow, current, null, device.id(), key), null);
                    }
                }
            }

            if (sender.getIfAvailable() == null) throw new Rejected(Failure.EMAIL_SERVICE_UNAVAILABLE);
            var otpKey = codes.key();
            var now = email.now();
            if (email.coolingDown(flow.userId(), PURPOSE, now)) throw new Rejected(Failure.OTP_RATE_LIMITED);
            BigInteger enrollmentId;
            try {
                enrollmentId = enrollment.map(Enrollment::id)
                        .orElseGet(() -> devices.createEnrollment(flow, now, current.expiresAt()));
            } catch (DuplicateKeyException staleEnrollment) {
                throw new Rejected(Failure.DEVICE_CHALLENGE_INVALID);
            }
            String code = freshOtp(flow, otpKey);
            var challenge = email.reserve(flow, PURPOSE, current.email(), now);
            return new Start(null, new PendingMail(enrollmentId, challenge, current.email(), code));
        });
        if (step.mail() == null) return step.response();
        return deliver(flow, step.mail());
    }

    private String freshOtp(RegistrationFlow flow, SecretKeySpec key) {
        // The enrollment ID stays the same on resend, so avoid reissuing any code already sent for that flow.
        var previous = email.deliveredCodes(flow, PURPOSE);
        for (int attempt = 0; attempt < 8; attempt++) {
            String code = codes.generate();
            if (previous.stream().noneMatch(old -> MessageDigest.isEqual(old.digest(),
                    codes.deviceDigest(key, flow, old.id(), old.email(), code)))) return code;
        }
        throw new Rejected(Failure.EMAIL_SERVICE_UNAVAILABLE);
    }

    private Map<String, Object> deliver(RegistrationFlow flow, PendingMail mail) {
        try {
            sender.getObject().send(mail.email(), mail.code());
        } catch (RegistrationEmailSender.DeliveryFailed failure) {
            transaction.executeWithoutResult(status -> {
                email.lock(flow);
                email.failedDelivery(flow, PURPOSE, mail.challenge());
            });
            throw new Rejected(Failure.EMAIL_SERVICE_UNAVAILABLE);
        }
        transaction.executeWithoutResult(status -> {
            var current = lockFlow(flow);
            var enrollment = requireEnrollment(flow, mail.enrollment());
            if (enrollment.verified() || !mail.email().equals(current.email()) || !email.delivered(flow, PURPOSE,
                    mail.challenge(), codes.deviceDigest(codes.key(), flow, mail.challenge(), mail.email(), mail.code()))) {
                throw new Rejected(Failure.EMAIL_SERVICE_UNAVAILABLE);
            }
        });
        return Map.of("challengeId", "e_" + mail.enrollment(), "nextAction", VERIFY_OTP,
                "maskedEmail", "***" + mail.email().substring(mail.email().lastIndexOf('@')),
                "otpExpiresInSeconds", 600, "resendAfterSeconds", 60);
    }

    public Map<String, Object> verifyOtp(RegistrationFlow flow, String enrollmentId, String code) {
        var id = identifier(enrollmentId, "e_", Failure.DEVICE_CHALLENGE_INVALID);
        Failure failure = transaction.execute(status -> {
            var current = lockFlow(flow);
            if (requireEnrollment(flow, id).verified()) return Failure.DEVICE_CHALLENGE_INVALID;
            var otpId = email.latestChallenge(flow, PURPOSE).orElseThrow(() -> new Rejected(Failure.OTP_INVALID));
            var otp = email.challenge(flow, PURPOSE, otpId).orElseThrow(() -> new Rejected(Failure.OTP_INVALID));
            if (otp.attempts() >= 5) return Failure.OTP_ATTEMPTS_EXCEEDED;
            if (otp.consumed() || otp.invalidated() || otp.digest().length != 32 || !otp.email().equals(current.email())) {
                return Failure.OTP_INVALID;
            }
            var now = email.now();
            if (now.isBefore(otp.createdAt()) || !now.isBefore(otp.expiresAt())
                    || !now.isBefore(otp.createdAt().plusMinutes(10))) return Failure.OTP_EXPIRED;
            if (!MessageDigest.isEqual(otp.digest(), codes.deviceDigest(codes.key(), flow, otpId, otp.email(), code))) {
                int attempts = otp.attempts() + 1;
                email.recordFailure(otpId, attempts, now);
                return attempts == 5 ? Failure.OTP_ATTEMPTS_EXCEEDED : Failure.OTP_INVALID;
            }
            email.consume(otpId, now);
            devices.authorizeEnrollment(id, now);
            return null;
        });
        if (failure != null) throw new Rejected(failure);
        return Map.of("nextAction", REGISTER_KEY);
    }

    public Map<String, Object> register(RegistrationFlow flow, String enrollmentId, Map<String, Object> jwk) {
        var id = identifier(enrollmentId, "e_", Failure.DEVICE_CHALLENGE_INVALID);
        DeviceCryptography.Key key;
        try {
            key = DeviceCryptography.submittedKey(jwk);
        } catch (IllegalArgumentException invalid) {
            throw new Rejected(Failure.DEVICE_KEY_INVALID);
        }
        return transaction.execute(status -> {
            var current = lockFlow(flow);
            var enrollment = requireEnrollment(flow, id);
            if (!enrollment.verified()) throw new Rejected(Failure.DEVICE_VERIFICATION_REQUIRED);
            if (devices.knownKey(flow.userId(), HEX.formatHex(key.thumbprint()))) {
                throw new Rejected(Failure.DEVICE_KEY_CONFLICT);
            }
            if (enrollment.jwk() == null) {
                devices.registerKey(id, key.json(), key.thumbprint());
            } else if (!MessageDigest.isEqual(key.thumbprint(), enrollment.thumbprint())) {
                throw new Rejected(Failure.DEVICE_KEY_CONFLICT);
            }
            return proofChallenge(flow, current, id, null, key);
        });
    }

    public AuthenticatedSession verifyProof(RegistrationFlow flow, String proofId, String signature) {
        var id = identifier(proofId, "p_", Failure.DEVICE_CHALLENGE_INVALID_OR_EXPIRED);
        return transaction.execute(status -> {
            lockFlow(flow);
            var proof = devices.liveProof(flow, id)
                    .orElseThrow(() -> new Rejected(Failure.DEVICE_CHALLENGE_INVALID_OR_EXPIRED));
            boolean enrolling = proof.enrollmentId() != null;
            var key = enrolling ? enrollmentKey(flow, proof.enrollmentId()) : returningKey(flow, proof.deviceId());
            var target = enrolling ? proof.enrollmentId() : proof.deviceId();
            byte[] payload = DeviceCryptography.payload(enrolling, flow.userId(), flow.flowId(), id, target,
                    key.thumbprint(), proof.nonce());
            if (!DeviceCryptography.verify(key, payload, signature)) throw new Rejected(Failure.DEVICE_SIGNATURE_INVALID);
            String refresh = refreshTokens.generate();
            String digest = HEX.formatHex(refreshTokens.digest(refresh));
            if (!devices.consumeProof(id)) throw new Rejected(Failure.DEVICE_CHALLENGE_INVALID_OR_EXPIRED);
            var now = email.now();
            String hash = HEX.formatHex(key.thumbprint());
            BigInteger device;
            if (enrolling) {
                try {
                    device = devices.createTrust(flow, target, hash, key.json(), now);
                } catch (DuplicateKeyException e) {
                    throw new Rejected(Failure.DEVICE_KEY_CONFLICT);
                }
            } else {
                device = target;
                devices.recordSeen(device, now);
            }
            var expires = now.plusDays(30);
            var session = devices.createSession(flow, device, digest, now, expires);
            return AuthenticatedSession.ready(DeviceCryptography.handle(key.thumbprint()), access.issueAccess(flow.userId(), session),
                    refresh, expires.toInstant(ZoneOffset.UTC));
        });
    }

    private Map<String, Object> proofChallenge(RegistrationFlow flow, ActiveFlow current, BigInteger enrollment,
                                                BigInteger device, DeviceCryptography.Key key) {
        var now = email.now();
        var expiry = now.plusMinutes(2);
        if (current.expiresAt().isBefore(expiry)) expiry = current.expiresAt();
        if (!expiry.isAfter(now)) throw new Rejected(Failure.AUTH_FLOW_EXPIRED);
        byte[] nonce = randomBytes();
        var id = devices.createProof(flow, enrollment, device, nonce, now, expiry);
        var payload = DeviceCryptography.payload(enrollment != null, flow.userId(), flow.flowId(), id,
                enrollment == null ? device : enrollment, key.thumbprint(), nonce);
        return Map.of("challengeId", "p_" + id, "nextAction", VERIFY_PROOF,
                "payloadToSign", DeviceCryptography.encode(payload), "expiresAt", expiry.toInstant(ZoneOffset.UTC));
    }

    private ActiveFlow lockFlow(RegistrationFlow flow) {
        email.lock(flow);
        var step = auth.flowStep(flow.userId(), flow.flowId(), flow.jtiDigest())
                .orElseThrow(() -> new Rejected(Failure.AUTH_FLOW_EXPIRED));
        if (step != FlowStep.BEGIN_DEVICE_VERIFICATION) throw new Rejected(Failure.ACCESS_DENIED);
        return devices.activeFlow(flow).orElseThrow(() -> new Rejected(Failure.ACCESS_DENIED));
    }

    private Enrollment requireEnrollment(RegistrationFlow flow, BigInteger id) {
        return devices.enrollment(flow).filter(candidate -> candidate.id().equals(id))
                .orElseThrow(() -> new Rejected(Failure.DEVICE_CHALLENGE_INVALID));
    }

    private DeviceCryptography.Key enrollmentKey(RegistrationFlow flow, BigInteger id) {
        var enrollment = requireEnrollment(flow, id);
        if (!enrollment.verified() || enrollment.thumbprint() == null || enrollment.jwk() == null) {
            throw new Rejected(Failure.DEVICE_VERIFICATION_REQUIRED);
        }
        try {
            var key = DeviceCryptography.storedKey(enrollment.jwk());
            if (MessageDigest.isEqual(key.thumbprint(), enrollment.thumbprint())) return key;
        } catch (IllegalArgumentException invalid) {
            // Report only the public error code.
        }
        throw new Rejected(Failure.DEVICE_KEY_INVALID);
    }

    private DeviceCryptography.Key returningKey(RegistrationFlow flow, BigInteger id) {
        var device = devices.trusted(flow, id, null).orElseThrow(() -> new Rejected(Failure.DEVICE_TRUST_REVOKED));
        var key = trustedKey(device);
        if (key == null) throw new Rejected(Failure.DEVICE_TRUST_REVOKED);
        return key;
    }

    private static DeviceCryptography.Key trustedKey(TrustedDevice device) {
        try {
            var key = DeviceCryptography.storedKey(device.jwk());
            if (MessageDigest.isEqual(key.thumbprint(), HEX.parseHex(device.hash()))) return key;
        } catch (IllegalArgumentException invalid) {
            // Invalid or legacy devices can only use the fresh-enrollment path.
        }
        return null;
    }

    private byte[] randomBytes() {
        byte[] result = new byte[32];
        random.nextBytes(result);
        return result;
    }

    private static BigInteger identifier(String value, String prefix, Failure failure) {
        if (!value.startsWith(prefix)) throw new Rejected(failure);
        String digits = value.substring(prefix.length());
        if (!digits.matches("[1-9][0-9]{0,19}")) throw new Rejected(failure);
        var id = new BigInteger(digits);
        if (id.bitLength() > 64) throw new Rejected(failure);
        return id;
    }

    private static String deviceHash(String handle) {
        if (!handle.startsWith("d_") || handle.length() != 45) throw new Rejected(Failure.DEVICE_CHALLENGE_INVALID);
        try {
            byte[] thumb = DeviceCryptography.decode(handle.substring(2));
            if (thumb.length == 32) return HEX.formatHex(thumb);
        } catch (IllegalArgumentException invalid) {
            // Reject malformed input before querying device records.
        }
        throw new Rejected(Failure.DEVICE_CHALLENGE_INVALID);
    }
}
