package com.unispace.infrastructure.security;

import com.unispace.identity.application.RestrictedFlowTokens;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class UniSpaceJwtService implements RestrictedFlowTokens {
    enum Kind {
        ACCESS("access", "unispace-access+jwt", "urn:unispace:api", "sid", 1200),
        FLOW("auth_flow", "unispace-flow+jwt", "urn:unispace:auth-flow", "fid", 900);

        final String purpose, type, audience, binding;
        final int seconds;

        Kind(String purpose, String type, String audience, String binding, int seconds) {
            this.purpose = purpose;
            this.type = type;
            this.audience = audience;
            this.binding = binding;
            this.seconds = seconds;
        }
    }

    record Verified(Kind kind, BigInteger userId, BigInteger bindingId, String jti) {}
    /** Allows the owner to insert flow_jti_digest before MySQL assigns the flow ID. */
    public static final class FlowTokenId {
        private final String jti;

        private FlowTokenId(String jti) {
            this.jti = jti;
        }

        public byte[] digest() {
            return UniSpaceJwtService.digest(jti);
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() {};
    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
    private static final int SKEW_SECONDS = 30;
    private final SecureRandom random = new SecureRandom();
    private final String issuer;
    private final String kid;
    private final RSAPrivateKey signingKey;
    private final Map<String, RSAPublicKey> verificationKeys;
    private final Clock clock;

    UniSpaceJwtService(String issuer, String kid, RSAPrivateKey signingKey,
                       Map<String, RSAPublicKey> verificationKeys, Clock clock) {
        this.issuer = issuer;
        this.kid = kid;
        this.signingKey = signingKey;
        this.verificationKeys = verificationKeys;
        this.clock = clock;
    }

    public String issueAccess(BigInteger userId, BigInteger sessionId) {
        return issue(Kind.ACCESS, userId, sessionId, newJti());
    }

    public FlowTokenId newFlowTokenId() {
        return new FlowTokenId(newJti());
    }

    @Override
    public Pending prepareFlow() {
        var tokenId = newFlowTokenId();
        return new Pending(tokenId.digest(), (userId, flowId) -> issueFlow(userId, flowId, tokenId));
    }

    public String issueFlow(BigInteger userId, BigInteger flowId, FlowTokenId tokenId) {
        return issue(Kind.FLOW, userId, flowId, tokenId.jti);
    }

    static byte[] digest(String jti) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(jti.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String newJti() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return URL.encodeToString(bytes);
    }

    private String issue(Kind kind, BigInteger userId, BigInteger bindingId, String jti) {
        requireId(userId);
        requireId(bindingId);
        long now = clock.instant().getEpochSecond();
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).type(new JOSEObjectType(kind.type)).build();
        var claims = Map.of("iss", issuer, "sub", userId.toString(), "aud", List.of(kind.audience),
                "iat", now, "nbf", now, "exp", now + kind.seconds, "jti", jti, "purpose", kind.purpose,
                kind.binding, bindingId.toString());
        var token = new JWSObject(header, new Payload(claims));
        try {
            token.sign(new RSASSASigner(signingKey));
            return token.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT signing unavailable");
        }
    }

    Verified verify(String compact) {
        try {
            var parts = compact.split("\\.", 4);
            require(parts.length == 3);
            var header = JSON.readValue(decodeCompactPart(parts[0]), OBJECT);
            require(header.keySet().equals(Set.of("alg", "kid", "typ")) && "RS256".equals(header.get("alg")));
            var key = verificationKeys.get(header.get("kid"));
            require(key != null && decodeCompactPart(parts[2]).length == 3072 / Byte.SIZE);
            var token = JWSObject.parse(compact);
            require(token.verify(new RSASSAVerifier(key)));
            Kind kind = Kind.ACCESS.type.equals(header.get("typ")) ? Kind.ACCESS : Kind.FLOW;
            require(kind.type.equals(header.get("typ")));
            var claims = JSON.readValue(decodeCompactPart(parts[1]), OBJECT);
            require(issuer.equals(claims.get("iss")) && kind.purpose.equals(claims.get("purpose")));
            Object audience = claims.get("aud");
            require(kind.audience.equals(audience) || List.of(kind.audience).equals(audience));
            require(!claims.containsKey(kind == Kind.ACCESS ? "fid" : "sid"));
            var user = id(claims.get("sub"));
            var binding = id(claims.get(kind.binding));
            var jti = (String) claims.get("jti");
            require(jti != null && jti.length() == 43 && URL.encodeToString(Base64.getUrlDecoder().decode(jti)).equals(jti));
            checkTimes(kind, claims);
            return new Verified(kind, user, binding, jti);
        } catch (ParseException | JOSEException | RuntimeException e) {
            throw new InvalidBearerTokenException("Invalid bearer token");
        }
    }

    private static byte[] decodeCompactPart(String part) {
        byte[] bytes = Base64.getUrlDecoder().decode(part);
        require(bytes.length > 0 && URL.encodeToString(bytes).equals(part));
        return bytes;
    }

    private void checkTimes(Kind kind, Map<String, Object> claims) {
        Instant issued = timestamp(claims.get("iat"));
        Instant start = timestamp(claims.get("nbf"));
        Instant end = timestamp(claims.get("exp"));
        Instant now = clock.instant();
        require(!start.isBefore(issued) && start.isBefore(end) && !end.isAfter(issued.plusSeconds(kind.seconds))
                && !start.isAfter(now.plusSeconds(SKEW_SECONDS)) && now.isBefore(end.plusSeconds(SKEW_SECONDS)));
    }

    private static Instant timestamp(Object value) {
        require(value instanceof Long || value instanceof Integer);
        return Instant.ofEpochSecond(((Number) value).longValue());
    }

    private static BigInteger id(Object claim) {
        require(claim instanceof String s && s.matches("[1-9][0-9]{0,19}"));
        var value = new BigInteger((String) claim);
        requireId(value);
        return value;
    }

    private static void requireId(BigInteger value) {
        require(value != null && value.signum() > 0 && value.bitLength() <= 64);
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid JWT field");
    }
}
