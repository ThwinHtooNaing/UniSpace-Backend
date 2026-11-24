package com.unispace.infrastructure.security;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

final class JwtKeyConfiguration {
    private static final Pattern PRIVATE_PEM = Pattern.compile(
            "-----BEGIN PRIVATE KEY-----\\s+([A-Za-z0-9+/=\\s]+)\\s+-----END PRIVATE KEY-----");
    // Reject private JWK members, duplicate JSON keys, and extra documents.
    private static final JsonMapper JWKS = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private record VerificationKeys(List<PublicJwk> keys) {}
    private record PublicJwk(String kty, String kid, String n, String e, String alg, String use,
                             @JsonProperty("key_ops") List<String> operations) {
        RSAPublicKey verificationKey() throws GeneralSecurityException {
            if (!"RSA".equals(kty) || kid == null || kid.isBlank() || (alg != null && !"RS256".equals(alg))
                    || (use != null && !"sig".equals(use)) || (operations != null && !List.of("verify").equals(operations))) {
                throw new IllegalArgumentException();
            }
            var modulus = unsignedInteger(n);
            if (modulus.bitLength() != 3072) throw new IllegalArgumentException();
            var spec = new RSAPublicKeySpec(modulus, unsignedInteger(e));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        }

        private static BigInteger unsignedInteger(String encoded) {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            if (bytes.length == 0 || bytes[0] == 0
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(encoded)) {
                throw new IllegalArgumentException();
            }
            return new BigInteger(1, bytes);
        }
    }

    private JwtKeyConfiguration() {}

    static UniSpaceJwtService load(JwtProperties properties, Clock clock) {
        try {
            var keys = properties.keys();
            if (properties.issuer() == null || properties.issuer().isBlank() || keys == null
                    || keys.kid() == null || keys.kid().isBlank() || keys.signing() == null || keys.verification() == null) {
                throw new IllegalArgumentException();
            }
            var signingKey = readPrivateKey(keys.signing());
            var trusted = new HashMap<String, RSAPublicKey>();
            var document = JWKS.readValue(Files.readString(keys.verification()), VerificationKeys.class);
            for (var entry : document.keys()) {
                if (trusted.putIfAbsent(entry.kid(), entry.verificationKey()) != null) throw new IllegalArgumentException();
            }
            var active = trusted.get(keys.kid());
            if (active == null || !signingKey.getModulus().equals(active.getModulus())
                    || !signingKey.getPublicExponent().equals(active.getPublicExponent())) throw new IllegalArgumentException();
            checkPair(signingKey, active);
            return new UniSpaceJwtService(properties.issuer(), keys.kid(), signingKey, Map.copyOf(trusted), clock);
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            // Parser exceptions can contain key material. Never propagate the input or parser message.
            throw new IllegalStateException("Invalid UniSpace JWT issuer or signing and verification key configuration");
        }
    }

    private static RSAPrivateCrtKey readPrivateKey(Path path) throws IOException, GeneralSecurityException {
        var pem = PRIVATE_PEM.matcher(Files.readString(path).strip());
        if (!pem.matches()) throw new IllegalArgumentException();
        var spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem.group(1).replaceAll("\\s", "")));
        if (KeyFactory.getInstance("RSA").generatePrivate(spec) instanceof RSAPrivateCrtKey key) return key;
        throw new IllegalArgumentException();
    }

    private static void checkPair(RSAPrivateCrtKey privateKey, RSAPublicKey publicKey) throws GeneralSecurityException {
        byte[] sample = "UniSpace signing key check".getBytes(StandardCharsets.US_ASCII);
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(sample);
        byte[] signature = signer.sign();
        signer.initVerify(publicKey);
        signer.update(sample);
        if (!signer.verify(signature)) throw new IllegalArgumentException();
    }
}
