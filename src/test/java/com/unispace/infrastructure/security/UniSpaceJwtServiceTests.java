package com.unispace.infrastructure.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.*;

class UniSpaceJwtServiceTests {
    static final Instant NOW = Instant.parse("2026-11-24T08:00:00Z");
    static RSAKey key;
    static RSAKey other;
    static JwtProperties properties;
    static UniSpaceJwtService service;
    static String token;
    @TempDir static Path directory;

    @BeforeAll static void prepare() throws Exception {
        key = JwtKeyConfigurationTests.key("test", 3072);
        other = JwtKeyConfigurationTests.key("other", 3072);
        properties = JwtKeyConfigurationTests.writeKeys(directory, key, key.toPublicJWK());
        service = at(NOW);
        token = service.issueAccess(BigInteger.ONE, BigInteger.TWO);
    }

    static UniSpaceJwtService at(Instant time) {
        return JwtKeyConfiguration.load(properties, Clock.fixed(time, ZoneOffset.UTC));
    }

    @Test void issuesBoundAndDistinctCredentials() throws Exception {
        var flowId = service.newFlowTokenId();
        var flow = service.issueFlow(BigInteger.ONE, BigInteger.TEN, flowId);
        var a = SignedJWT.parse(token);
        var f = SignedJWT.parse(flow);
        assertThat(a.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(a.getHeader().getKeyID()).isEqualTo("test");
        assertThat(a.getHeader().getType().toString()).isEqualTo("unispace-access+jwt");
        assertThat(f.getHeader().getType().toString()).isEqualTo("unispace-flow+jwt");
        assertThat(a.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(NOW.plusSeconds(1200));
        assertThat(f.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(NOW.plusSeconds(900));
        assertThat(a.getJWTClaimsSet().getAudience()).containsExactly("urn:unispace:api");
        assertThat(f.getJWTClaimsSet().getAudience()).containsExactly("urn:unispace:auth-flow");
        assertThat(a.getJWTClaimsSet().getClaim("fid")).isNull();
        assertThat(f.getJWTClaimsSet().getClaim("sid")).isNull();
        assertThat(service.verify(token).bindingId()).isEqualTo(BigInteger.TWO);
        assertThat(service.verify(flow).bindingId()).isEqualTo(BigInteger.TEN);
        assertThat(flowId.digest()).hasSize(32).containsExactly(java.security.MessageDigest.getInstance("SHA-256")
                .digest(f.getJWTClaimsSet().getJWTID().getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertThat(f.getJWTClaimsSet().getJWTID()).isNotEqualTo(a.getJWTClaimsSet().getJWTID());
    }

    @Test void enforcesThirtySecondClockBoundaries() {
        assertThat(at(NOW.minusSeconds(30)).verify(token)).isNotNull();
        assertThatThrownBy(() -> at(NOW.minusSeconds(31)).verify(token)).isInstanceOf(InvalidBearerTokenException.class);
        assertThat(at(NOW.plusSeconds(1229)).verify(token)).isNotNull();
        assertThatThrownBy(() -> at(NOW.plusSeconds(1230)).verify(token)).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test void requiresUnpaddedCompactSerialization() {
        assertThatThrownBy(() -> service.verify(token + "=")).isInstanceOf(InvalidBearerTokenException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer","audience","mixedAudience","purpose","type","signature","kid","algorithm",
            "mixedBinding","missingBinding","subject","bindingType","jti","iat","nbf","lifetime","missingExp","jku"})
    void rejectsInvalidCredential(String fault) throws Exception {
        var signed = SignedJWT.parse(token);
        var claims = new HashMap<>(signed.getPayload().toJSONObject());
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").type(signed.getHeader().getType());
        var signingKey = key;
        switch (fault) {
            case "issuer" -> claims.put("iss", "other");
            case "audience" -> claims.put("aud", "urn:unispace:auth-flow");
            case "mixedAudience" -> claims.put("aud", List.of("urn:unispace:api","urn:unispace:auth-flow"));
            case "purpose" -> claims.put("purpose","auth_flow");
            case "type" -> header.type(new JOSEObjectType("unispace-flow+jwt"));
            case "signature" -> signingKey = other;
            case "kid" -> header.keyID("unknown");
            case "algorithm" -> header = new JWSHeader.Builder(JWSAlgorithm.RS512).keyID("test").type(signed.getHeader().getType());
            case "mixedBinding" -> claims.put("fid","2");
            case "missingBinding" -> claims.remove("sid");
            case "subject" -> claims.put("sub","18446744073709551616");
            case "bindingType" -> claims.put("sid",2);
            case "jti" -> claims.put("jti","x");
            case "iat" -> claims.put("iat",NOW.plusSeconds(31).getEpochSecond());
            case "nbf" -> claims.put("nbf",NOW.plusSeconds(31).getEpochSecond());
            case "lifetime" -> claims.put("exp",NOW.plusSeconds(1201).getEpochSecond());
            case "missingExp" -> claims.remove("exp");
            case "jku" -> header.jwkURL(java.net.URI.create("https://example.invalid/keys"));
            default -> throw new AssertionError(fault);
        }
        var jws = new JWSObject(header.build(), new Payload(claims));
        jws.sign(new RSASSASigner(signingKey));
        assertThatThrownBy(() -> service.verify(jws.serialize())).isInstanceOf(InvalidBearerTokenException.class);
    }
}
