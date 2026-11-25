package com.unispace.identity.application;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
class RegistrationCode {
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();

    RegistrationCode(Environment environment) {
        this.environment = environment;
    }

    SecretKeySpec key() {
        try {
            String configured = environment.getProperty("unispace.auth.otp.hmac.key", "");
            byte[] key = Base64.getDecoder().decode(configured);
            if (key.length >= 32) return new SecretKeySpec(key, "HmacSHA256");
        } catch (IllegalArgumentException invalid) {
            // Configuration errors must not include the key.
        }
        throw new OnboardingService.Rejected(OnboardingService.Failure.EMAIL_SERVICE_UNAVAILABLE);
    }

    String generate() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    byte[] digest(SecretKeySpec key, AuthenticationStateService.RegistrationFlow flow,
                  BigInteger challengeId, String email, String code) {
        return digest(key, "unispace:registration-otp:v1", flow, challengeId, email, code);
    }

    byte[] deviceDigest(SecretKeySpec key, AuthenticationStateService.RegistrationFlow flow,
                        BigInteger challengeId, String email, String code) {
        return digest(key, "unispace:new-device-otp:v1", flow, challengeId, email, code);
    }

    private byte[] digest(SecretKeySpec key, String purpose, AuthenticationStateService.RegistrationFlow flow,
                          BigInteger challengeId, String email, String code) {
        String binding = String.join("\0", purpose, flow.userId().toString(),
                flow.flowId().toString(), challengeId.toString(), email, code);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(binding.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Registration code verification unavailable");
        }
    }
}
