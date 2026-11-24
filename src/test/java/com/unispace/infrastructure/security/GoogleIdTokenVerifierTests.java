package com.unispace.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.unispace.identity.application.GoogleIdentityVerifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenVerifierTests {
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");
    private static RSAKey key;
    private static RSAKey other;
    private static GoogleIdTokenVerifier verifier;

    @BeforeAll
    static void setup() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("google-fixture").generate();
        other = new RSAKeyGenerator(2048).generate();
        verifier = new GoogleIdTokenVerifier(GoogleIdTokenVerifier.configure(
                NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build()),
                "server-client", Set.of("android-client"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acceptsVerifiedSubjectAndExactNonceWithoutRequiringAnEmailClaim() throws Exception {
        var proof = verifier.verify(token(claims -> claims.claim("azp", "android-client"), key, JWSAlgorithm.RS256));
        assertThat(proof.subject()).isEqualTo("google-subject");
        assertThat(proof.nonce()).isEqualTo("server-nonce");
        assertThat(proof.firstName()).isEqualTo("Ada");
    }

    @Test
    void acceptsGoogleLegacyIssuerAndIgnoresEmailClaims() throws Exception {
        var proof = verifier.verify(token(claims -> claims.issuer("accounts.google.com")
                .claim("email", "someone@example.edu").claim("email_verified", false), key, JWSAlgorithm.RS256));
        assertThat(proof.subject()).isEqualTo("google-subject");
    }

    @Test
    void rejectsUntrustedSignatureAndWrongAlgorithm() throws Exception {
        assertInvalid(token(claims -> {}, other, JWSAlgorithm.RS256));
        assertInvalid(token(claims -> {}, key, JWSAlgorithm.RS512));
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "mixedAudience", "presenter", "missingNonce", "emptyNonce",
            "expired", "futureIssued", "missingIssued", "missingExpiry", "missingSubject", "badSubject"})
    void rejectsInvalidClaims(String fault) throws Exception {
        assertInvalid(token(claims -> {
            switch (fault) {
                case "issuer" -> claims.issuer("https://other.example");
                case "audience" -> claims.audience("other-client");
                case "mixedAudience" -> claims.audience(List.of("server-client", "other-client"));
                case "presenter" -> claims.claim("azp", "other-client");
                case "missingNonce" -> claims.claim("nonce", null);
                case "emptyNonce" -> claims.claim("nonce", "");
                case "expired" -> claims.expirationTime(Date.from(NOW));
                case "futureIssued" -> claims.issueTime(Date.from(NOW.plusSeconds(31)));
                case "missingIssued" -> claims.issueTime(null);
                case "missingExpiry" -> claims.expirationTime(null);
                case "missingSubject" -> claims.subject(null);
                case "badSubject" -> claims.subject("non-ascii-\u2603");
                default -> throw new AssertionError(fault);
            }
        }, key, JWSAlgorithm.RS256));
    }

    private static String token(Consumer<JWTClaimsSet.Builder> customize, RSAKey signer, JWSAlgorithm alg)
            throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer("https://accounts.google.com").audience("server-client")
                .subject("google-subject").issueTime(Date.from(NOW.minusSeconds(1)))
                .expirationTime(Date.from(NOW.plusSeconds(300))).claim("nonce", "server-nonce")
                .claim("given_name", "Ada");
        customize.accept(claims);
        var jwt = new SignedJWT(new JWSHeader.Builder(alg).keyID("google-fixture").build(), claims.build());
        jwt.sign(new RSASSASigner(signer));
        return jwt.serialize();
    }

    private static void assertInvalid(String token) {
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(GoogleIdentityVerifier.InvalidProof.class);
    }
}
