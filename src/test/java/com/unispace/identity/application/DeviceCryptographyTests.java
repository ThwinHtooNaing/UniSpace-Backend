package com.unispace.identity.application;

import java.math.BigInteger;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceCryptographyTests {
    @Test
    void verifiesIndependentP256VectorAndSeparatesOperations() {
        var key = DeviceCryptography.submittedKey(Map.of("kty","EC","crv","P-256",
                "x","VzffRIs5SGLrb8NWyOJk1BFuMixMa3bRz7br8TDfqi0",
                "y","mFOahLuEpyL0aPeps_Ix7zB-wtCyUjbeX8xThtCRZB8"));
        assertThat(DeviceCryptography.encode(key.thumbprint())).isEqualTo("U3irnePz9a_NaEBDGfou3_WAT24KKeWMHSbc2SWaRAY");
        var nonce = HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        var user = BigInteger.valueOf(11);
        var flow = BigInteger.valueOf(22);
        var proof = BigInteger.valueOf(33);
        var target = BigInteger.valueOf(44);
        var payload = DeviceCryptography.payload(true, user, flow, proof, target, key.thumbprint(), nonce);
        assertThat(DeviceCryptography.encode(payload)).isEqualTo(
                "VW5pU3BhY2UvZGV2aWNlLXByb29mL3YxCkVOUk9MTAoxMQoyMgozMwo0NApVM2lybmVQejlhX05hRUJER2ZvdTNfV0FUMjRLS2VXTUhTYmMyU1dhUkFZCkFBRUNBd1FGQmdjSUNRb0xEQTBPRHhBUkVoTVVGUllYR0JrYUd4d2RIaDg");
        String signature = "MEQCIDeM53PK3wHLoTBwYB3537CQdBX1ilR70njyy-wFxKPiAiBg-wkAfmzmPzV7EOd4yLwd3BG6SLBvT3f-RjmUnZ4ECA";
        assertThat(DeviceCryptography.verify(key, payload, signature)).isTrue();
        assertThat(DeviceCryptography.verify(key, payload, signature + "=")).isFalse();
        assertThat(DeviceCryptography.verify(key, DeviceCryptography.payload(false, user, flow, proof, target,
                key.thumbprint(), nonce), signature)).isFalse();
    }

    @Test
    void refusesPrivateMaterialWrongCurveAndOffCurvePoints() {
        String zero = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        assertThatThrownBy(() -> DeviceCryptography.submittedKey(Map.of("kty","EC","crv","P-256","x",zero,"y",zero)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeviceCryptography.submittedKey(Map.of("kty","EC","crv","P-384","x",zero,"y",zero)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeviceCryptography.submittedKey(Map.of("kty","EC","crv","P-256","x",zero,"y",zero,"d",zero)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
