package com.unispace.infrastructure.security;

import com.unispace.identity.application.GoogleIdentityVerifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.text.ParseException;
import com.nimbusds.jose.JWSObject;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

final class GoogleIdTokenVerifier implements GoogleIdentityVerifier {
    private static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private final JwtDecoder decoder;
    private final String audience;
    private final Set<String> presenters;
    private final Clock clock;

    GoogleIdTokenVerifier(JwtDecoder decoder, String audience, Set<String> presenters, Clock clock) {
        this.decoder = decoder;
        this.audience = audience;
        this.presenters = presenters;
        this.clock = clock;
    }

    static NimbusJwtDecoder configure(NimbusJwtDecoder decoder) {
        // Google claim policy is enforced below with its own clock and without expiration grace.
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        return decoder;
    }

    @Override
    public Proof verify(String idToken) {
        try {
            var jwt = decoder.decode(idToken);
            // The decoder normalizes registered claims. Check the subject's original JSON type
            // on the exact payload whose signature it just authenticated.
            var signedClaims = JWSObject.parse(jwt.getTokenValue()).getPayload().toJSONObject();
            var claims = jwt.getClaims();
            var headers = jwt.getHeaders();
            var now = clock.instant();
            Instant issued = jwt.getIssuedAt();
            Instant expires = jwt.getExpiresAt();
            Instant notBefore = jwt.getNotBefore();
            Object presenter = claims.get("azp");
            Object nonce = claims.get("nonce");
            if (!"RS256".equals(headers.get("alg")) || !(headers.get("kid") instanceof String kid) || kid.isBlank()
                    || !ISSUERS.contains(String.valueOf(claims.get("iss")))
                    || !List.of(audience).equals(jwt.getAudience())
                    || (claims.containsKey("azp") && (!(presenter instanceof String id)
                            || (!audience.equals(id) && !presenters.contains(id))))
                    || issued == null || expires == null || !expires.isAfter(now) || !expires.isAfter(issued)
                    || issued.isAfter(now.plusSeconds(30))
                    || (notBefore != null && (notBefore.isAfter(now.plusSeconds(30)) || !notBefore.isBefore(expires)))
                    || signedClaims == null || !(signedClaims.get("sub") instanceof String subject)
                    || !subject.matches("[\\x21-\\x7e]{1,255}") || !subject.equals(jwt.getSubject())
                    || !(nonce instanceof String value) || value.isBlank() || value.length() > 128) {
                throw new InvalidProof();
            }
            return new Proof(subject, value, issued, expires, name(claims.get("given_name")), name(claims.get("family_name")));
        } catch (BadJwtException | IllegalArgumentException | ParseException e) {
            throw new InvalidProof();
        } catch (JwtException e) {
            throw new VerificationUnavailable();
        }
    }

    private static String name(Object claim) {
        if (!(claim instanceof String text)) return null;
        String name = text.strip();
        return name.isEmpty() || name.codePointCount(0, name.length()) > 100
                || name.codePoints().anyMatch(Character::isISOControl) ? null : name;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "unispace.auth.google", name = "audience")
    static class ConfigurationForGoogle {
        @Bean
        @SuppressWarnings("removal") // Spring Security 7.2's JWKS builder expects RestOperations.
        GoogleIdentityVerifier googleIdentityVerifier(Environment environment) {
            String audience = environment.getRequiredProperty("unispace.auth.google.audience").strip();
            String allowed = environment.getProperty("unispace.auth.google.presenters", "");
            var presenters = Arrays.stream(allowed.split(",")).map(String::strip)
                    .filter(id -> !id.isEmpty()).collect(Collectors.toUnmodifiableSet());
            if (!validClientId(audience) || presenters.stream().anyMatch(id -> !validClientId(id))) {
                throw new IllegalStateException("Invalid Google OAuth client ID configuration");
            }
            var requests = new SimpleClientHttpRequestFactory();
            requests.setConnectTimeout(Duration.ofSeconds(3));
            requests.setReadTimeout(Duration.ofSeconds(3));
            var decoder = configure(NimbusJwtDecoder.withJwkSetUri(JWKS).jwsAlgorithm(SignatureAlgorithm.RS256)
                    .restOperations(new RestTemplate(requests)).build());
            return new GoogleIdTokenVerifier(decoder, audience, presenters, Clock.systemUTC());
        }

        private static boolean validClientId(String id) {
            return id.matches("[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com");
        }
    }
}
