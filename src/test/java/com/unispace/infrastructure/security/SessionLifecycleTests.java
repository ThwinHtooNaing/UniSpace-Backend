package com.unispace.infrastructure.security;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.unispace.identity.persistence.SessionLifecycleRepository;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfSystemProperty(named = "unispace.mysql.tests", matches = "true")
class SessionLifecycleTests {
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String SIGN_OUT = "/api/v1/auth/sign-out";
    private static final JsonMapper JSON = new JsonMapper();
    private static final AtomicLong IDS = new AtomicLong();
    private static JwtProperties signing;
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @TempDir static Path directory;

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired OpaqueRefreshTokens refreshTokens;
    @MockitoSpyBean UniSpaceJwtService jwt;
    @MockitoSpyBean SessionLifecycleRepository store;
    MockMvc http;
    BigInteger user;

    record Fixture(BigInteger device, BigInteger session, String refresh) {}

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) throws Exception {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.flyway.url", MYSQL::getJdbcUrl);
        properties.add("spring.flyway.user", MYSQL::getUsername);
        properties.add("spring.flyway.password", MYSQL::getPassword);
        var key = JwtKeyConfigurationTests.key("sessions", 3072);
        signing = JwtKeyConfigurationTests.writeKeys(directory, key, key.toPublicJWK());
        properties.add("unispace.jwt.issuer", signing::issuer);
        properties.add("unispace.jwt.keys.kid", signing.keys()::kid);
        properties.add("unispace.jwt.keys.signing", signing.keys()::signing);
        properties.add("unispace.jwt.keys.verification", signing.keys()::verification);
        var secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        properties.add("unispace.auth.refresh.hmac.key", () -> Base64.getEncoder().encodeToString(secret));
    }

    @BeforeEach
    void setup() {
        http = MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        user = id();
        db.update("""
                INSERT INTO users (id,role,status,email_verified_at,created_at,updated_at)
                VALUES (?,'STUDENT','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, user);
    }

    @Test
    void rotatesWithinSameFamilyAndPersistsOnlyKeyedDigests() throws Exception {
        var fixture = session(null);
        var response = json(refresh(fixture.refresh()).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.expiresInSeconds").value(1200))
                .andExpect(jsonPath("$.status").value("AUTHENTICATED")));
        String successor = field(response,"refreshToken");
        assertThat(successor).isNotEqualTo(fixture.refresh());
        var access = jwt.verify(field(response,"accessToken"));
        assertThat(access.bindingId()).isEqualTo(fixture.session());
        assertThat(access.userId()).isEqualTo(user);
        assertThat(db.queryForObject("SELECT refresh_token_hash FROM auth_sessions WHERE id=?",String.class,fixture.session()))
                .isEqualTo(hex(successor)).isNotEqualTo(successor);
        assertThat(db.queryForObject("SELECT HEX(token_digest) FROM consumed_refresh_tokens WHERE session_id=?",
                String.class,fixture.session())).isEqualToIgnoringCase(hex(fixture.refresh()));
        assertThat(db.queryForObject("""
                SELECT TIMESTAMPDIFF(SECOND,last_refreshed_at,expires_at) FROM auth_sessions WHERE id=?
                """,Long.class,fixture.session())).isEqualTo(30L*24*60*60);
        assertThat(field(response,"deviceId")).startsWith("d_");
        assertThat(Instant.parse(field(response,"refreshExpiresAt"))).isAfter(Instant.now().plusSeconds(29L*86400));
    }

    @Test
    void immediateReplayRevokesSuccessorAndKeepsOtherDeviceAndTrustIntact() throws Exception {
        var first = session(null);
        var other = session(null);
        var response = json(refresh(first.refresh()).andExpect(status().isOk()));
        refresh(first.refresh()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        refresh(first.refresh()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        refresh(field(response,"refreshToken")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        assertThat(revoked(first.session(),"auth_sessions")).isTrue();
        assertThat(revoked(first.device(),"trusted_devices")).isFalse();
        assertThat(revoked(other.session(),"auth_sessions")).isFalse();
        refresh(other.refresh()).andExpect(status().isOk());
    }

    @Test
    void concurrentRequestsHaveAtMostOneSuccessAndRevokeItsSuccessor() throws Exception {
        var fixture = session(null);
        var barrier = new CountDownLatch(2);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Callable<org.springframework.test.web.servlet.MvcResult> call = () -> {
                barrier.countDown();
                if (!barrier.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent request did not start");
                return refresh(fixture.refresh()).andReturn();
            };
            var a = workers.submit(call);
            var b = workers.submit(call);
            var results = List.of(a.get(15, TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            assertThat(results.stream().map(r -> r.getResponse().getStatus())).containsExactlyInAnyOrder(200,401);
            var denied = results.stream().filter(r -> r.getResponse().getStatus()==401).findFirst().orElseThrow();
            assertThat(JSON.readTree(denied.getResponse().getContentAsByteArray()).get("code").asString())
                    .isEqualTo("REFRESH_TOKEN_REUSED");
            var success = results.stream().filter(r -> r.getResponse().getStatus()==200).findFirst().orElseThrow();
            refresh(field(JSON.readTree(success.getResponse().getContentAsByteArray()),"refreshToken"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        }
    }

    @Test
    void failsClosedForInvalidExpiredDisabledAndRevokedCredentials() throws Exception {
        var fixture = session(null);
        refresh("invalid").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
        refresh(refreshTokens.generate()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
        db.update("UPDATE auth_sessions SET expires_at=UTC_TIMESTAMP(6) WHERE id=?",fixture.session());
        refresh(fixture.refresh()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        assertThat(revoked(fixture.device(),"trusted_devices")).isFalse();
        db.update("UPDATE auth_sessions SET expires_at=UTC_TIMESTAMP(6)+INTERVAL 1 DAY WHERE id=?",fixture.session());
        db.update("UPDATE users SET status='DISABLED' WHERE id=?",user);
        refresh(fixture.refresh()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        db.update("UPDATE users SET status='ACTIVE' WHERE id=?",user);
        db.update("UPDATE trusted_devices SET revoked_at=UTC_TIMESTAMP(6) WHERE id=?",fixture.device());
        refresh(fixture.refresh()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
        assertThat(history(fixture.session())).isZero();
    }

    @Test
    void bearerSignOutRevokesTheSelectedSessionAndInvalidatesSameDeviceCredentials() throws Exception {
        var first = session(null);
        var sibling = session(first.device());
        var other = session(null);
        http.perform(post(SIGN_OUT).header("Authorization", "Bearer "+jwt.issueAccess(user,first.session())))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(revoked(first.session(),"auth_sessions")).isTrue();
        assertThat(revoked(first.device(),"trusted_devices")).isTrue();
        refresh(first.refresh()).andExpect(status().isUnauthorized());
        refresh(sibling.refresh()).andExpect(status().isUnauthorized());
        refresh(other.refresh()).andExpect(status().isOk());
    }

    @Test
    void expiredBearerMustBeOmittedForRefreshFallback() throws Exception {
        var f = session(null);
        var expired = JwtKeyConfiguration.load(signing,
                Clock.fixed(Instant.now().minusSeconds(1800),ZoneOffset.UTC)).issueAccess(user,f.session());
        http.perform(post(SIGN_OUT).header("Authorization","Bearer "+expired))
                .andExpect(status().isUnauthorized());
        http.perform(post(SIGN_OUT).header("Authorization","Bearer "+expired)
                .contentType("application/json").content(body(f.refresh()))).andExpect(status().isUnauthorized());
        assertThat(revoked(f.device(),"trusted_devices")).isFalse();
        fallback(f.refresh()).andExpect(status().isNoContent());
        assertThat(revoked(f.session(),"auth_sessions")).isTrue();
        assertThat(revoked(f.device(),"trusted_devices")).isTrue();
        fallback(f.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshReplayDuringFallbackRevokesOnlyTheFamilyAndCannotConfirmSignOut() throws Exception {
        var f = session(null);
        refresh(f.refresh()).andExpect(status().isOk());
        fallback(f.refresh()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        assertThat(revoked(f.session(),"auth_sessions")).isTrue();
        assertThat(revoked(f.device(),"trusted_devices")).isFalse();
    }

    @Test
    void rejectsMixedCredentialsAndRestrictedFlowsWithoutStateChanges() throws Exception {
        var f = session(null);
        var other = session(null);
        http.perform(post(SIGN_OUT).header("Authorization","Bearer "+jwt.issueAccess(user,f.session()))
                .contentType("application/json").content(body(other.refresh()))).andExpect(status().isBadRequest());
        assertThat(revoked(f.session(),"auth_sessions")).isFalse();
        assertThat(revoked(other.session(),"auth_sessions")).isFalse();

        String flow = flow();
        for (String path : List.of(REFRESH,SIGN_OUT)) {
            http.perform(post(path).header("Authorization","Bearer "+flow)
                    .contentType("application/json").content(body(f.refresh())))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        assertThat(history(f.session())).isZero();
        assertThat(revoked(f.device(),"trusted_devices")).isFalse();
    }

    @Test
    void rollsBackHistoryAndRotationIfFreshJwtIssuanceFails() throws Exception {
        var f = session(null);
        doThrow(new IllegalStateException("internal signing failure")).when(jwt).issueAccess(eq(user),eq(f.session()));
        refresh(f.refresh()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_STATE_UNAVAILABLE"));
        assertThat(history(f.session())).isZero();
        assertThat(db.queryForObject("SELECT refresh_token_hash FROM auth_sessions WHERE id=?",String.class,f.session()))
                .isEqualTo(hex(f.refresh()));
        reset(jwt);
        refresh(f.refresh()).andExpect(status().isOk());
    }

    @Test
    void rollsBackSignOutIfDeviceRevocationCannotBeConfirmed() throws Exception {
        var f = session(null);
        var ref = new SessionLifecycleRepository.Reference(user, f.session());
        doThrow(new IllegalStateException("fixture device write failure")).when(store).revokeDevice(ref, f.device());
        fallback(f.refresh()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_STATE_UNAVAILABLE"));
        assertThat(revoked(f.session(),"auth_sessions")).isFalse();
        assertThat(revoked(f.device(),"trusted_devices")).isFalse();
        reset(store);
        fallback(f.refresh()).andExpect(status().isNoContent());
    }

    @Test
    void recognizesReplayBeforeAccountTrustAndExpiryChecks() throws Exception {
        var f = session(null);
        refresh(f.refresh()).andExpect(status().isOk());
        db.update("UPDATE auth_sessions SET expires_at=UTC_TIMESTAMP(6) WHERE id=?",f.session());
        db.update("UPDATE users SET status='DISABLED' WHERE id=?",user);
        refresh(f.refresh()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        assertThat(revoked(f.session(),"auth_sessions")).isTrue();
        assertThat(revoked(f.device(),"trusted_devices")).isFalse();
    }

    private Fixture session(BigInteger existingDevice) throws Exception {
        BigInteger device = existingDevice;
        if (device == null) {
            device = id();
            var key = new ECKeyGenerator(Curve.P_256).generate().toPublicJWK();
            db.update("""
                    INSERT INTO trusted_devices (id,user_id,device_key_hash,public_key_jwk,verified_at)
                    VALUES (?,?,?,?,UTC_TIMESTAMP(6))
                    """,device,user,HexFormat.of().formatHex(key.computeThumbprint().decode()),key.toJSONString());
        }
        String refresh = refreshTokens.generate();
        var session = id();
        db.update("""
                INSERT INTO auth_sessions (id,user_id,device_id,refresh_token_hash,created_at,expires_at)
                VALUES (?,?,?,?,UTC_TIMESTAMP(6)-INTERVAL 1 DAY,UTC_TIMESTAMP(6)+INTERVAL 2 DAY)
                """,session,user,device,hex(refresh));
        return new Fixture(device,session,refresh);
    }

    private String flow() {
        var id = id();
        db.update("""
                INSERT INTO provider_auth_challenges (id,challenge_id_digest,provider,purpose,nonce_digest,created_at,expires_at,consumed_at)
                VALUES (?,UNHEX(SHA2(?,256)),'GOOGLE','SIGN_IN',UNHEX(SHA2(?,256)),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 5 MINUTE,UTC_TIMESTAMP(6))
                """,id,"proof-"+id,"nonce-"+id);
        var pending = jwt.prepareFlow();
        db.update("""
                INSERT INTO auth_flows (id,user_id,source_challenge_id,flow_jti_digest,created_at,expires_at)
                VALUES (?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 15 MINUTE)
                """,id,user,id,pending.jtiDigest());
        return pending.sign().apply(user,id);
    }

    private static BigInteger id() { return BigInteger.valueOf(IDS.incrementAndGet()); }
    private String hex(String refresh) { return HexFormat.of().formatHex(refreshTokens.digest(refresh)); }
    private Integer history(BigInteger session) {
        return db.queryForObject("SELECT COUNT(*) FROM consumed_refresh_tokens WHERE session_id=?",Integer.class,session);
    }
    private boolean revoked(BigInteger id, String table) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT revoked_at IS NOT NULL FROM "+table+" WHERE id=?",Boolean.class,id));
    }
    private ResultActions refresh(String token) throws Exception {
        return http.perform(post(REFRESH).contentType("application/json").content(body(token)));
    }
    private ResultActions fallback(String token) throws Exception {
        return http.perform(post(SIGN_OUT).contentType("application/json").content(body(token)));
    }
    private static String body(String token) { return JSON.writeValueAsString(Map.of("refreshToken",token)); }
    private static String field(JsonNode node, String field) { return node.get(field).asString(); }
    private static JsonNode json(ResultActions response) throws Exception {
        return JSON.readTree(response.andReturn().getResponse().getContentAsByteArray());
    }
}
