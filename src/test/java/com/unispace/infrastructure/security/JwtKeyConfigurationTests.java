package com.unispace.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.*;

class JwtKeyConfigurationTests {
    static RSAKey first;
    static RSAKey second;
    @TempDir Path dir;

    @BeforeAll static void generate() throws Exception {
        first = key("first", 3072);
        second = key("second", 3072);
    }

    static RSAKey key(String kid, int bits) throws Exception {
        return new RSAKeyGenerator(bits).keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).generate();
    }

    static JwtProperties writeKeys(Path dir, RSAKey signer, RSAKey... publicKeys) throws Exception {
        var priv = dir.resolve("private.pem");
        var pub = dir.resolve("verification.json");
        Files.writeString(priv, "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(signer.toPrivateKey().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        Files.writeString(pub, new JWKSet(List.of(publicKeys)).toString(false));
        return new JwtProperties("urn:test:issuer", new JwtProperties.Keys(signer.getKeyID(), priv, pub));
    }

    @Test void rotatesWithOverlapAndWithdrawsOldKey() throws Exception {
        var clock = Clock.systemUTC();
        var old = JwtKeyConfiguration.load(writeKeys(dir, first, first.toPublicJWK()), clock);
        var token = old.issueAccess(BigInteger.ONE, BigInteger.TWO);
        var overlap = JwtKeyConfiguration.load(writeKeys(dir, second, first.toPublicJWK(), second.toPublicJWK()), clock);
        assertThat(overlap.verify(token).userId()).isEqualTo(BigInteger.ONE);
        assertThat(overlap.verify(overlap.issueAccess(BigInteger.ONE, BigInteger.TWO)).bindingId()).isEqualTo(BigInteger.TWO);
        var retired = JwtKeyConfiguration.load(writeKeys(dir, second, second.toPublicJWK()), clock);
        assertThatThrownBy(() -> retired.verify(token)).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test void refusesMissingMaterialPrivateVerificationKeysAndMismatches() throws Exception {
        var p = writeKeys(dir, first, first);
        assertThatThrownBy(() -> JwtKeyConfiguration.load(p, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        writeKeys(dir, first, new RSAKey.Builder(second.toPublicJWK()).keyID("first").build());
        assertThatThrownBy(() -> JwtKeyConfiguration.load(p, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        writeKeys(dir, first, first.toPublicJWK(), first.toPublicJWK());
        assertThatThrownBy(() -> JwtKeyConfiguration.load(p, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        writeKeys(dir, first, second.toPublicJWK());
        assertThatThrownBy(() -> JwtKeyConfiguration.load(p, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        Files.delete(p.keys().signing());
        assertThatThrownBy(() -> JwtKeyConfiguration.load(p, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsUndersizedKeysAndInvalidPem() throws Exception {
        var weak = key("weak", 2048);
        var p = writeKeys(dir, weak, weak.toPublicJWK());
        assertThatThrownBy(() -> JwtKeyConfiguration.load(p, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        var invalid = writeKeys(dir, first, first.toPublicJWK());
        Files.writeString(invalid.keys().signing(), "invalid");
        assertThatThrownBy(() -> JwtKeyConfiguration.load(invalid, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }
}
