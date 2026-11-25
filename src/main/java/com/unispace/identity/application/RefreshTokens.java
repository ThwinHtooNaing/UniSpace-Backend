package com.unispace.identity.application;

public interface RefreshTokens {
    String generate();

    byte[] digest(String token);
}
