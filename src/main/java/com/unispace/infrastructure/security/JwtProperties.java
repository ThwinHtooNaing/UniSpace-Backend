package com.unispace.infrastructure.security;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("unispace.jwt")
public record JwtProperties(String issuer, Keys keys) {
    public record Keys(String kid, Path signing, Path verification) {}
}
