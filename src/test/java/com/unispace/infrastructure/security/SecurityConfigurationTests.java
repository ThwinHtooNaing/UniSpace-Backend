package com.unispace.infrastructure.security;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.unispace.administration.application.UserDirectory;
import com.unispace.identity.application.AuthenticationStateService;
import com.unispace.identity.application.AuthenticationStateService.AccessSession;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfSystemProperty(named = "unispace.mysql.tests", matches = "true")
@Import(SecurityConfigurationTests.Probes.class)
class SecurityConfigurationTests {
    private static final String AUTHORIZATION = "/api/v1/me/authorization";
    private static final String ADMIN_USERS = "/api/v1/admin/users";
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @TempDir static Path keyDir;
    static final AtomicLong IDS = new AtomicLong();
    static ECKey deviceKey;
    @Autowired WebApplicationContext context;
    @Autowired UniSpaceJwtService tokens;
    @Autowired JdbcTemplate db;
    @Autowired AdminAuthorization admins;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean AuthenticationStateService state;
    @MockitoSpyBean UserDirectory directory;
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
                    .requestMatchers(HttpMethod.GET, "/probe/admin").access(AdminAuthorization.httpAccess())
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
        call("/probe/admin", token).andExpect(status().isForbidden());
        call("/probe/profile", token).andExpect(status().isForbidden());
        db.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        call("/probe/admin", token).andExpect(status().isOk());
        call("/api/v1/admin/role-requests", token).andExpect(status().isForbidden());
        call("/probe/admin", flow()).andExpect(status().isForbidden());
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

    @Test void currentAuthorizationReflectsDatabaseRoleOnTheSameAccessToken() throws Exception {
        String token = access();
        call(AUTHORIZATION, token).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("""
                        {"userId":"%s","role":"STUDENT"}
                        """.formatted(user), JsonCompareMode.STRICT));
        db.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        call(AUTHORIZATION, token).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        call(ADMIN_USERS, token).andExpect(status().isOk());
        db.update("UPDATE users SET role='MANAGEMENT' WHERE id=?", id);
        call(AUTHORIZATION, token).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MANAGEMENT"));
        call(ADMIN_USERS, token).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test void readOnlyRoutesRejectMissingInvalidAndUnusableAccessSessions() throws Exception {
        String token = access();
        db.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        for (String path : new String[]{AUTHORIZATION, ADMIN_USERS, ADMIN_USERS + "/" + id}) {
            http.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            call(path, "invalid").andExpect(status().isUnauthorized());
        }
        db.update("UPDATE users SET status='DISABLED' WHERE id=?", id);
        call(AUTHORIZATION, token).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        call(ADMIN_USERS, token).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        db.update("UPDATE users SET status='PENDING_EMAIL_VERIFICATION', email_verified_at=NULL WHERE id=?", id);
        call(AUTHORIZATION, token).andExpect(status().isUnauthorized());
        call(ADMIN_USERS, token).andExpect(status().isUnauthorized());
        db.update("UPDATE users SET status='ACTIVE', email_verified_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        db.update("UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        call(AUTHORIZATION, token).andExpect(status().isUnauthorized());
        call(ADMIN_USERS + "/" + id, token).andExpect(status().isUnauthorized());
    }

    @Test void onlyCurrentAdminsCanReadDirectoryAndOnlyAccessTokensCanUseEitherApi() throws Exception {
        String token = access();
        String target = ADMIN_USERS + "/99999";
        for (String role : new String[]{"STUDENT", "SUB_MANAGEMENT", "MANAGEMENT"}) {
            db.update("UPDATE users SET role=? WHERE id=?", role, id);
            call(ADMIN_USERS + "?limit=0", token).andExpect(status().isForbidden());
            call(target, token).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        db.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        String restricted = flow();
        for (String path : new String[]{AUTHORIZATION, ADMIN_USERS, target}) {
            call(path, restricted).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        call(target, token).andExpect(status().isNotFound());
        http.perform(put(ADMIN_USERS + "/" + id + "/role").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test void directoryPagesByUnsignedIdAndSkipsProvisionalAccounts() throws Exception {
        String admin = access();
        db.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        db.update("""
                INSERT INTO users (id,first_name,last_name,role,status,email_verified_at,created_at,updated_at)
                VALUES (9223372036854775808,'Same','Name','STUDENT','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                       (9223372036854775809,'Same','Name','STUDENT','PENDING_EMAIL_VERIFICATION',NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                       (9223372036854775810,'Same','Name','MANAGEMENT','DISABLED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                       (9223372036854775811,'Same','Name','STUDENT','DISABLED',NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                       (9223372036854775812,'Same','Name','STUDENT','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        call(ADMIN_USERS + "?limit=2&cursor=9223372036854775807", admin).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("""
                        {"items":[
                          {"userId":"9223372036854775808","firstName":"Same","lastName":"Name","role":"STUDENT","accountStatus":"ACTIVE"},
                          {"userId":"9223372036854775810","firstName":"Same","lastName":"Name","role":"MANAGEMENT","accountStatus":"DISABLED"}
                        ],"nextCursor":"9223372036854775810"}
                        """, JsonCompareMode.STRICT));
        call(ADMIN_USERS + "?limit=2&cursor=9223372036854775810", admin).andExpect(status().isOk())
                .andExpect(content().json("""
                        {"items":[{"userId":"9223372036854775812","firstName":"Same","lastName":"Name","role":"STUDENT","accountStatus":"ACTIVE"}],
                         "nextCursor":null}
                        """, JsonCompareMode.STRICT));
        call(ADMIN_USERS + "/9223372036854775809", admin).andExpect(status().isNotFound());
        call(ADMIN_USERS + "/9223372036854775811", admin).andExpect(status().isNotFound());
        call(ADMIN_USERS + "/9223372036854775810", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("DISABLED"))
                .andExpect(jsonPath("$.channel").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test void directoryDetailExposesOnlyApprovedFieldsAndCurrentMembership() throws Exception {
        String admin = access();
        db.update("""
                UPDATE users SET role='ADMIN',first_name='Admin',last_name='Reader',student_id='Fixture-42',
                                 university_email='fixture@example.edu'
                WHERE id=?
                """, id);
        db.update("""
                INSERT INTO provider_identities (user_id,provider,provider_subject,linked_at)
                VALUES (?,'GOOGLE','private-fixture-subject',UTC_TIMESTAMP(6))
                """, id);
        db.update("""
                INSERT INTO channels (id,name,name_normalized,creator_user_id,version,created_at,updated_at)
                VALUES (500,'Fixture Channel','fixture channel',?,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, id);
        db.update("INSERT INTO channel_memberships (user_id,channel_id,joined_at) VALUES (?,500,UTC_TIMESTAMP(6))", id);
        call(ADMIN_USERS + "/" + id, admin).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("""
                        {"userId":"%s","firstName":"Admin","lastName":"Reader","role":"ADMIN","accountStatus":"ACTIVE",
                         "studentId":"Fixture-42","universityEmail":"fixture@example.edu",
                         "channel":{"channelId":"500","name":"Fixture Channel"}}
                        """.formatted(id), JsonCompareMode.STRICT));
    }

    @Test void directoryReturnsBoundedValidationAndMissingResourceProblems() throws Exception {
        String admin = access();
        db.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        for (String suffix : new String[]{"?limit=0", "?limit=101", "?limit=1&limit=2", "?cursor=18446744073709551616",
                "?search=private", "?sort=firstName", "/01", "/18446744073709551616"}) {
            call(ADMIN_USERS + suffix, admin).andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.traceId").isNotEmpty());
        }
        call(ADMIN_USERS + "/18446744073709551615", admin).andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        call(ADMIN_USERS + "?cursor=18446744073709551615&limit=1", admin).andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[],\"nextCursor\":null}", JsonCompareMode.STRICT));
    }

    @Test void readOnlyRoutesReturnSanitizedProblemsWhenDatabaseStateIsUnavailable() throws Exception {
        String admin = access();
        db.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        var failure = new DataAccessResourceFailureException("private database fixture");
        doThrow(failure).when(directory).list(BigInteger.ZERO, 25);
        call(ADMIN_USERS, admin).andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("DIRECTORY_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
        doThrow(failure).when(state).accessRole(user, user);
        call(AUTHORIZATION, admin).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_STATE_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))));
    }

    @Test void privilegedWriteRequiresATransactionAndRejectsARevokedSession() throws Exception {
        access();
        db.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        var session = new AccessSession(user, user);
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> admins.requireForMutation(session))
                .isInstanceOf(IllegalTransactionStateException.class);
        BigInteger actor = transaction.execute(status -> admins.requireForMutation(session));
        assertThat(actor).isEqualTo(user);
        db.update("UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP(6) WHERE id=?", id);
        assertThatThrownBy(() -> transaction.execute(status -> admins.requireForMutation(session)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void adminRoleIsLockedUntilThePrivilegedTransactionEnds() throws Exception {
        access();
        db.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        var session = new AccessSession(user, user);
        var transaction = new TransactionTemplate(transactions);
        var updating = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var update = transaction.execute(status -> {
                admins.requireForMutation(session);
                var pending = executor.submit(() -> {
                    updating.countDown();
                    return db.update("UPDATE users SET role='STUDENT' WHERE id=?", id);
                });
                try {
                    assertThat(updating.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> pending.get(200, TimeUnit.MILLISECONDS))
                            .isInstanceOf(TimeoutException.class);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                return pending;
            });
            assertThat(update.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThatThrownBy(() -> transaction.execute(status -> admins.requireForMutation(session)))
                .isInstanceOf(AccessDeniedException.class);
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

    @Test void futureEmailVerificationIsNotCurrentForEitherCredential() throws Exception {
        access();
        flow();
        db.update("UPDATE users SET email_verified_at=UTC_TIMESTAMP(6)+INTERVAL 1 DAY WHERE id=?", id);
        assertAll(
                () -> assertThat(state.accessRole(user, user)).isEmpty(),
                () -> assertThat(state.flowStep(user, user, tokenId.digest())).isEmpty());
    }

    @Test void trustedDeviceRequiresASignatureVerificationKey() throws Exception {
        access();
        db.update("""
                UPDATE trusted_devices SET public_key_jwk=JSON_SET(public_key_jwk, '$.use', 'sig',
                    '$.key_ops', JSON_ARRAY('verify'), '$.alg', 'ES256') WHERE id=?
                """, id);
        assertThat(state.accessRole(user, user)).contains(AuthenticationStateService.Role.STUDENT);
        db.update("UPDATE trusted_devices SET public_key_jwk=JSON_SET(public_key_jwk, '$.alg', 'ES384') WHERE id=?", id);
        assertThat(state.accessRole(user, user)).isEmpty();
        db.update("UPDATE trusted_devices SET public_key_jwk=JSON_SET(public_key_jwk, '$.key_ops', JSON_ARRAY('sign'), '$.alg', 'ES256') WHERE id=?", id);
        assertThat(state.accessRole(user, user)).isEmpty();
        db.update("UPDATE trusted_devices SET public_key_jwk=? WHERE id=?", deviceKey.toJSONString(), id);
        db.update("UPDATE trusted_devices SET public_key_jwk=JSON_SET(public_key_jwk, '$.use', 'enc') WHERE id=?", id);
        assertThat(state.accessRole(user, user)).isEmpty();
    }

    @Test void sourceChallengeMustHaveBeenConsumedDuringItsLifetime() {
        flow();
        db.update("""
                UPDATE provider_auth_challenges SET created_at=UTC_TIMESTAMP(6)-INTERVAL 8 MINUTE,
                    expires_at=UTC_TIMESTAMP(6)-INTERVAL 3 MINUTE, consumed_at=UTC_TIMESTAMP(6)-INTERVAL 4 MINUTE
                WHERE id=?
                """, id);
        // A valid exchange remains usable after its provider challenge has expired.
        assertThat(state.flowStep(user, user, tokenId.digest())).isPresent();
        db.update("UPDATE provider_auth_challenges SET consumed_at=expires_at WHERE id=?", id);
        assertThat(state.flowStep(user, user, tokenId.digest())).isEmpty();
        db.update("UPDATE provider_auth_challenges SET expires_at=UTC_TIMESTAMP(6), consumed_at=created_at+INTERVAL 5 MINUTE WHERE id=?", id);
        assertThat(state.flowStep(user, user, tokenId.digest())).isEmpty();
        db.update("UPDATE provider_auth_challenges SET consumed_at=created_at-INTERVAL 1 SECOND WHERE id=?", id);
        assertThat(state.flowStep(user, user, tokenId.digest())).isEmpty();
    }
}
