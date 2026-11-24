package com.unispace.infrastructure.security;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.unispace.identity.application.AuthenticationStateService;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfSystemProperty(named = "unispace.mysql.tests", matches = "true")
@Import(SecurityConfigurationTests.Probes.class)
class SecurityConfigurationTests {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @TempDir static Path keyDir;
    static final AtomicLong IDS = new AtomicLong();
    static ECKey deviceKey;
    @Autowired WebApplicationContext context;
    @Autowired UniSpaceJwtService tokens;
    @Autowired JdbcTemplate db;
    @MockitoSpyBean AuthenticationStateService state;
    MockMvc http;
    long id;
    BigInteger user;
    UniSpaceJwtService.FlowTokenId tokenId;

    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) throws Exception {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.flyway.url", MYSQL::getJdbcUrl);
        properties.add("spring.flyway.user", MYSQL::getUsername);
        properties.add("spring.flyway.password", MYSQL::getPassword);
        var key = JwtKeyConfigurationTests.key("http-test", 3072);
        var p = JwtKeyConfigurationTests.writeKeys(keyDir, key, key.toPublicJWK());
        properties.add("unispace.jwt.issuer", p::issuer);
        properties.add("unispace.jwt.keys.kid", p.keys()::kid);
        properties.add("unispace.jwt.keys.signing", p.keys()::signing);
        properties.add("unispace.jwt.keys.verification", p.keys()::verification);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Probes {
        @Bean SecurityConfiguration.OperationRules probeRules() {
            return rules -> rules.requestMatchers(HttpMethod.GET, "/probe/member").hasAuthority("ACCESS")
                    .requestMatchers(HttpMethod.GET, "/probe/admin").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/probe/profile").hasAuthority("FLOW_PROFILE_REQUIRED")
                    .requestMatchers(HttpMethod.GET, "/probe/email").hasAuthority("FLOW_EMAIL_REQUIRED")
                    .requestMatchers(HttpMethod.GET, "/probe/code").hasAuthority("FLOW_EMAIL_VERIFICATION_REQUIRED")
                    .requestMatchers(HttpMethod.GET, "/probe/start-device").hasAuthority("FLOW_BEGIN_DEVICE_VERIFICATION");
        }
        @Bean ProbeController probeController() { return new ProbeController(); }
    }

    @org.springframework.boot.test.context.TestComponent
    @RestController
    static class ProbeController {
        @GetMapping({"/probe/member", "/probe/admin", "/probe/profile", "/probe/email", "/probe/code", "/probe/start-device"})
        String ok() { return "ok"; }
    }

    @BeforeAll static void generateDeviceKey() throws Exception {
        deviceKey = new ECKeyGenerator(Curve.P_256).generate().toPublicJWK();
    }

    @BeforeEach void setup() {
        http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        id = IDS.incrementAndGet();
        user = BigInteger.valueOf(id);
        db.update("""
                INSERT INTO users (id, role, status, email_verified_at, created_at, updated_at)
                VALUES (?, 'STUDENT', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, id);
    }

    String access() throws Exception {
        db.update("""
                INSERT INTO trusted_devices (id, user_id, device_key_hash, public_key_jwk, verified_at)
                VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6))
                """, id, id, HexFormat.of().formatHex(deviceKey.computeThumbprint().decode()), deviceKey.toJSONString());
        db.update("""
                INSERT INTO auth_sessions (id, user_id, device_id, refresh_token_hash, created_at, expires_at)
                VALUES (?, ?, ?, SHA2(?,256), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) + INTERVAL 1 DAY)
                """, id, id, id, "fixture-" + id);
        return tokens.issueAccess(user, user);
    }

    String flow() {
        tokenId = tokens.newFlowTokenId();
        db.update("""
                INSERT INTO provider_auth_challenges
                (id, provider, purpose, challenge_id_digest, nonce_digest, created_at, expires_at, consumed_at)
                VALUES (?, 'GOOGLE', 'SIGN_IN', UNHEX(SHA2(?,256)), UNHEX(SHA2(?,256)), UTC_TIMESTAMP(6),
                        UTC_TIMESTAMP(6) + INTERVAL 5 MINUTE, UTC_TIMESTAMP(6))
                """, id, "challenge-" + id, "nonce-" + id);
        db.update("""
                INSERT INTO auth_flows (id, user_id, source_challenge_id, flow_jti_digest, created_at, expires_at)
                VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) + INTERVAL 15 MINUTE)
                """, id, id, id, tokenId.digest());
        return tokens.issueFlow(user, user, tokenId);
    }

    ResultActions call(String path, String token) throws Exception {
        return http.perform(get(path).header("Authorization", "Bearer " + token));
    }

    @Test void deniesAnonymousInvalidAndUnimplementedOperations() throws Exception {
        http.perform(get("/probe/member")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        call("/probe/member", "invalid").andExpect(status().isUnauthorized());
        String token = access();
        call("/actuator/health", token).andExpect(status().isForbidden());
        call("/api/v1/me/linked-providers", token).andExpect(status().isForbidden());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/challenges"))
                .andExpect(status().isUnauthorized());
    }

    @Test void rechecksCurrentRoleOwnershipAndSessionOnEveryRequest() throws Exception {
        String token = access();
        call("/probe/member", token).andExpect(status().isOk());
        call("/probe/profile", token).andExpect(status().isForbidden());
        db.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        call("/probe/admin", token).andExpect(status().isOk());
        db.update("UPDATE users SET role='STUDENT' WHERE id=?", id);
        call("/probe/admin", token).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403)).andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        call("/probe/member", tokens.issueAccess(user.add(BigInteger.TEN), user)).andExpect(status().isUnauthorized());
        db.update("UPDATE auth_sessions SET expires_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        call("/probe/member", token).andExpect(status().isUnauthorized());
        db.update("UPDATE auth_sessions SET expires_at=UTC_TIMESTAMP(6)+INTERVAL 1 DAY, revoked_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        call("/probe/member", token).andExpect(status().isUnauthorized());
    }

    @Test void acceptsOnlyAnExplicitSingleBearerAndCreatesNoSession() throws Exception {
        String token = access();
        var response = call("/probe/member", token).andExpect(status().isOk()).andReturn();
        org.assertj.core.api.Assertions.assertThat(response.getRequest().getSession(false)).isNull();
        http.perform(get("/probe/member").param("access_token", token)).andExpect(status().isUnauthorized());
        http.perform(get("/probe/member").header("Authorization", "Bearer " + token, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test void rechecksDeviceAndAccountAndRejectsLegacyTrust() throws Exception {
        String token = access();
        call("/probe/member", token).andExpect(status().isOk());
        db.update("UPDATE users SET status='DISABLED' WHERE id=?", id);
        call("/probe/member", token).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        db.update("UPDATE users SET status='ACTIVE' WHERE id=?", id);
        db.update("UPDATE trusted_devices SET revoked_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        call("/probe/member", token).andExpect(status().isUnauthorized());
        db.update("UPDATE trusted_devices SET revoked_at=NULL, public_key_jwk=NULL WHERE id=?", id);
        call("/probe/member", token).andExpect(status().isUnauthorized());
        db.update("UPDATE auth_sessions SET device_id=NULL WHERE id=?", id);
        call("/probe/member", token).andExpect(status().isUnauthorized());
    }

    @Test void restrictsFlowToItsCurrentOnboardingStep() throws Exception {
        db.update("UPDATE users SET status='PENDING_PROFILE', email_verified_at=NULL WHERE id=?", id);
        String token = flow();
        call("/probe/profile", token).andExpect(status().isOk());
        call("/probe/member", token).andExpect(status().isForbidden());
        call("/probe/email", token).andExpect(status().isForbidden());
        db.update("UPDATE users SET status='PENDING_EMAIL_VERIFICATION' WHERE id=?", id);
        call("/probe/profile", token).andExpect(status().isForbidden());
        call("/probe/email", token).andExpect(status().isOk());
        db.update("""
                INSERT INTO email_challenges(user_id,auth_flow_id,purpose,destination_email,code_digest,
                    created_at,expires_at,resend_available_at)
                VALUES (?,?,'REGISTRATION','student@example.edu',UNHEX(SHA2('fixture',256)),
                    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)+INTERVAL 10 MINUTE, UTC_TIMESTAMP(6)+INTERVAL 1 MINUTE)
                """, id, id);
        call("/probe/code", token).andExpect(status().isOk());
        db.update("UPDATE email_challenges SET invalidated_at=UTC_TIMESTAMP(6) WHERE auth_flow_id=?", id);
        call("/probe/code", token).andExpect(status().isForbidden());
        db.update("UPDATE auth_flows SET completed_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        call("/probe/email", token).andExpect(status().isUnauthorized());
    }

    @Test void enforcesFlowBindingAndDatabaseExpiryWithoutGrace() throws Exception {
        db.update("UPDATE users SET status='PENDING_PROFILE' WHERE id=?", id);
        String token = flow();
        call("/probe/profile", token).andExpect(status().isOk());
        call("/probe/profile", tokens.issueFlow(user, user, tokens.newFlowTokenId())).andExpect(status().isUnauthorized());
        db.update("UPDATE auth_flows SET invalidated_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        call("/probe/profile", token).andExpect(status().isUnauthorized());
        db.update("UPDATE auth_flows SET invalidated_at=NULL, created_at=UTC_TIMESTAMP(6)-INTERVAL 5 SECOND, expires_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        call("/probe/profile", token).andExpect(status().isUnauthorized());
    }

    @Test void activeFlowCanOnlyBeginVerificationForItsOwnAccountAndFlow() throws Exception {
        String token = flow();
        call("/probe/start-device", token).andExpect(status().isOk());
        call("/probe/member", token).andExpect(status().isForbidden());
        call("/probe/profile", token).andExpect(status().isForbidden());
        call("/probe/start-device", tokens.issueFlow(user.add(BigInteger.TEN), user, tokenId))
                .andExpect(status().isUnauthorized());
        call("/probe/start-device", tokens.issueFlow(user, user.add(BigInteger.TEN), tokenId))
                .andExpect(status().isUnauthorized());
        db.update("UPDATE users SET status='DISABLED' WHERE id=?", id);
        call("/probe/start-device", token).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test void storeFailureIsSafeAndNeverAuthorizes() throws Exception {
        String token = access();
        doThrow(new DataAccessResourceFailureException("sensitive internal failure")).when(state).accessRole(user, user);
        call("/probe/member", token).andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(503)).andExpect(jsonPath("$.code").value("AUTH_STATE_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive"))));
    }
}
