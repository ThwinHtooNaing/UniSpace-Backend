package com.unispace.identity.application;

import java.math.BigInteger;

public interface AccessTokenIssuer {
    String issueAccess(BigInteger userId, BigInteger sessionId);
}
