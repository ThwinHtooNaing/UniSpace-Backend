package com.unispace.infrastructure.security;

import com.unispace.identity.application.AuthenticationStateService;
import com.unispace.identity.application.GoogleIdentityVerifier;
import java.math.BigInteger;
import java.security.Principal;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfiguration {
    @FunctionalInterface
    interface OperationRules {
        void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rules);
    }

    record CurrentIdentity(UniSpaceJwtService.Kind kind, BigInteger userId, BigInteger bindingId) implements Principal {
        @Override public String getName() { return userId.toString(); }
    }

    @Bean
    UniSpaceJwtService jwtService(JwtProperties properties) {
        return JwtKeyConfiguration.load(properties, Clock.systemUTC());
    }

    @Bean
    AuthenticationManager authenticationManager(UniSpaceJwtService tokens, AuthenticationStateService state) {
        return authentication -> {
            if (!(authentication instanceof BearerTokenAuthenticationToken bearer)) throw invalidBinding();
            var verified = tokens.verify(bearer.getToken());
            List<SimpleGrantedAuthority> authorities;
            try {
                if (verified.kind() == UniSpaceJwtService.Kind.ACCESS) {
                    var role = state.accessRole(verified.userId(), verified.bindingId())
                            .orElseThrow(SecurityConfiguration::invalidBinding);
                    authorities = List.of(new SimpleGrantedAuthority("ACCESS"), new SimpleGrantedAuthority("ROLE_" + role));
                } else {
                    var step = state.flowStep(verified.userId(), verified.bindingId(), UniSpaceJwtService.digest(verified.jti()))
                            .orElseThrow(SecurityConfiguration::invalidBinding);
                    authorities = List.of(new SimpleGrantedAuthority("FLOW_" + step));
                }
            } catch (AuthenticationStateService.DisabledAccountException disabled) {
                throw new OAuth2AuthenticationException(new OAuth2Error(SecurityProblemHandler.ACCOUNT_DISABLED));
            } catch (DataAccessException unavailable) {
                throw new OAuth2AuthenticationException(new OAuth2Error(SecurityProblemHandler.STATE_UNAVAILABLE));
            }
            Principal principal = verified.kind() == UniSpaceJwtService.Kind.ACCESS
                    ? new AuthenticationStateService.AccessSession(verified.userId(), verified.bindingId())
                    : new CurrentIdentity(verified.kind(), verified.userId(), verified.bindingId());
            return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        };
    }

    private static InvalidBearerTokenException invalidBinding() {
        return new InvalidBearerTokenException("Invalid bearer credential");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager manager, ObjectMapper mapper,
                                            ObjectProvider<OperationRules> operations,
                                            ObjectProvider<GoogleIdentityVerifier> google) throws Exception {
        var problems = new SecurityProblemHandler(mapper);
        var bearer = new BearerTokenAuthenticationConverter();
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable) // This API accepts explicit bearer credentials only.
                .oauth2ResourceServer(oauth -> oauth.authenticationManagerResolver(request -> manager)
                        .authenticationConverter(request -> {
                            var headers = request.getHeaders(HttpHeaders.AUTHORIZATION).asIterator();
                            if (headers.hasNext()) {
                                headers.next();
                                if (headers.hasNext()) throw invalidBinding();
                            }
                            return bearer.convert(request);
                        })
                        .authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .authorizeHttpRequests(rules -> {
                    rules.requestMatchers("/actuator", "/actuator/**").denyAll();
                    if (google.getIfAvailable() != null) {
                        rules.requestMatchers(HttpMethod.POST, "/api/v1/auth/challenges",
                                "/api/v1/auth/provider-exchange").permitAll();
                    }
                    operations.orderedStream().forEach(operation -> operation.configure(rules));
                    rules.anyRequest().denyAll();
                });
        return http.build();
    }
}
