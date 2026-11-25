package com.unispace.identity.application;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.text.ParseException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

final class DeviceCryptography {
    record Key(String json, byte[] thumbprint, ECPublicKey publicKey) {}

    private DeviceCryptography() {}

    static Key submittedKey(Map<String, Object> jwk) {
        if (!jwk.keySet().equals(Set.of("kty", "crv", "x", "y"))
                || !"EC".equals(jwk.get("kty")) || !"P-256".equals(jwk.get("crv"))
                || !(jwk.get("x") instanceof String x) || !(jwk.get("y") instanceof String y)
                || x.length() != 43 || y.length() != 43 || decode(x).length != 32 || decode(y).length != 32) {
            throw new IllegalArgumentException("Invalid P-256 public JWK");
        }
        try {
            return publicKey(ECKey.parse(jwk));
        } catch (ParseException | JOSEException e) {
            throw new IllegalArgumentException("Invalid P-256 public JWK");
        }
    }

    static Key storedKey(String json) {
        if (json == null) throw new IllegalArgumentException("Missing device key");
        try {
            return publicKey(ECKey.parse(json));
        } catch (ParseException | JOSEException e) {
            throw new IllegalArgumentException("Invalid stored device key");
        }
    }

    private static Key publicKey(ECKey key) throws JOSEException {
        if (key.isPrivate() || !Curve.P_256.equals(key.getCurve())
                || (key.getKeyUse() != null && !KeyUse.SIGNATURE.equals(key.getKeyUse()))
                || (key.getKeyOperations() != null && !Set.of(KeyOperation.VERIFY).equals(key.getKeyOperations()))
                || (key.getAlgorithm() != null && !JWSAlgorithm.ES256.equals(key.getAlgorithm()))) {
            throw new IllegalArgumentException("Invalid device verification key");
        }
        return new Key(key.toJSONString(), key.computeThumbprint().decode(), key.toECPublicKey());
    }

    static byte[] payload(boolean enrollment, BigInteger user, BigInteger flow, BigInteger proof,
                          BigInteger target, byte[] thumbprint, byte[] nonce) {
        return String.join("\n", "UniSpace/device-proof/v1", enrollment ? "ENROLL" : "SIGN_IN",
                user.toString(), flow.toString(), proof.toString(), target.toString(),
                encode(thumbprint), encode(nonce)).getBytes(StandardCharsets.US_ASCII);
    }

    static boolean verify(Key key, byte[] payload, String encodedSignature) {
        if (encodedSignature.length() > 96) return false;
        try {
            byte[] der = decode(encodedSignature);
            if (der.length < 8 || der.length > 72) return false;
            var verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key.publicKey());
            verifier.update(payload);
            // The JDK verifier validates the DER sequence and ECDSA scalar ranges.
            return verifier.verify(der);
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            return false;
        }
    }

    static String handle(byte[] thumbprint) {
        return "d_" + encode(thumbprint);
    }

    static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] decode(String text) {
        byte[] value = Base64.getUrlDecoder().decode(text);
        if (!encode(value).equals(text)) throw new IllegalArgumentException("Noncanonical base64url");
        return value;
    }
}
