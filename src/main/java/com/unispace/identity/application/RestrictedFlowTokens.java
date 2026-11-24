package com.unispace.identity.application;

import java.math.BigInteger;
import java.util.function.BiFunction;

public interface RestrictedFlowTokens {
    record Pending(byte[] jtiDigest, BiFunction<BigInteger, BigInteger, String> sign) {}

    Pending prepareFlow();
}
