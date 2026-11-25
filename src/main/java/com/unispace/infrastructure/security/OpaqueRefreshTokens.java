package com.unispace.infrastructure.security;

import com.unispace.identity.application.RefreshTokens;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
final class OpaqueRefreshTokens implements RefreshTokens {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final SecureRandom random = new SecureRandom();
    private final Environment settings;

    OpaqueRefreshTokens(Environment settings) {
        this.settings = settings;
    }

    @Override
    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    @Override
    public byte[] digest(String token) {
        if (token == null || token.length() != 43) throw new IllegalArgumentException("Invalid refresh credential");
        byte[] bytes = Base64.getUrlDecoder().decode(token);
        if (!ENCODER.encodeToString(bytes).equals(token) || bytes.length != 32) {
            throw new IllegalArgumentException("Invalid refresh credential");
        }

        try {
            byte[] key = Base64.getDecoder().decode(settings.getProperty("unispace.auth.refresh.hmac.key", ""));
            if (key.length < 32) throw new IllegalArgumentException();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(("unispace:refresh:v1\0" + token).getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException | IllegalArgumentException unavailable) {
            throw new IllegalStateException("Refresh credential verification unavailable");
        }
    }
}
