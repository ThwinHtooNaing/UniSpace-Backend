package com.unispace.infrastructure.security;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.unispace.identity.application.GoogleIdentityVerifier;
import com.unispace.identity.persistence.GoogleAuthenticationRepository;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "unispace.auth.google.audience=fixture.apps.googleusercontent.com")
@ActiveProfiles("test")
@Testcontainers
@EnabledIfSystemProperty(named = "unispace.mysql.tests", matches = "true")
class GoogleAuthenticationTests {
    private static final String CHALLENGES = "/api/v1/auth/challenges";
    private static final String EXCHANGE = "/api/v1/auth/provider-exchange";
    private static final JsonMapper JSON = new JsonMapper();
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @TempDir static Path keyDir;

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired UniSpaceJwtService tokens;
    @MockitoBean GoogleIdentityVerifier verifier;
    @MockitoSpyBean GoogleAuthenticationRepository store;
    MockMvc http;
    String subject;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry properties) throws Exception {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.flyway.url", MYSQL::getJdbcUrl);
        properties.add("spring.flyway.user", MYSQL::getUsername);
        properties.add("spring.flyway.password", MYSQL::getPassword);
        var key = JwtKeyConfigurationTests.key("google-http", 3072);
        var jwt = JwtKeyConfigurationTests.writeKeys(keyDir, key, key.toPublicJWK());
        properties.add("unispace.jwt.issuer", jwt::issuer);
        properties.add("unispace.jwt.keys.kid", jwt.keys()::kid);
        properties.add("unispace.jwt.keys.signing", jwt.keys()::signing);
        properties.add("unispace.jwt.keys.verification", jwt.keys()::verification);
    }

    @BeforeEach
    void setup() {
        http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        subject = UUID.randomUUID().toString();
    }

    @Test
    void provisionsAndBindsOnlyARestrictedFlow() throws Exception {
        JsonNode challenge = challenge();
        for (String field : new String[] {"challengeId", "nonce"}) {
            assertThat(Base64.getUrlDecoder().decode(challenge.get(field).asString())).hasSize(32);
        }
        var response = json(exchange(challenge, "fixture").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("ONBOARDING_REQUIRED"))
                .andExpect(jsonPath("$.accountStatus").value("PENDING_PROFILE"))
                .andExpect(jsonPath("$.nextAction").value("PROFILE_REQUIRED"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist()));
        var jwt = tokens.verify(response.get("flowToken").asString());
        assertThat(jwt.kind()).isEqualTo(UniSpaceJwtService.Kind.FLOW);
        assertThat(db.queryForMap("""
                SELECT u.role, u.first_name, u.university_email, u.email_verified_at, f.flow_jti_digest,
                       p.challenge_id_digest, p.nonce_digest, p.consumed_at,
                       TIMESTAMPDIFF(SECOND,p.created_at,p.expires_at) AS challenge_seconds,
                       TIMESTAMPDIFF(SECOND,f.created_at,f.expires_at) AS flow_seconds
                FROM auth_flows f JOIN users u ON u.id = f.user_id
                JOIN provider_auth_challenges p ON p.id=f.source_challenge_id
                JOIN provider_identities i ON i.user_id = u.id WHERE i.provider_subject=?
                """, subject)).containsEntry("role", "STUDENT").containsEntry("first_name", "Ada")
                .containsEntry("university_email", null).containsEntry("email_verified_at", null)
                .containsEntry("flow_jti_digest", UniSpaceJwtService.digest(jwt.jti()))
                .containsEntry("challenge_id_digest", UniSpaceJwtService.digest(challenge.get("challengeId").asString()))
                .containsEntry("nonce_digest", UniSpaceJwtService.digest(challenge.get("nonce").asString()))
                .containsEntry("challenge_seconds", 300L).containsEntry("flow_seconds", 900L);
        http.perform(get("/api/v1/me/linked-providers").header("Authorization", "Bearer " + response.get("flowToken").asString()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=?",
                Integer.class, jwt.userId())).isZero();
        exchange(challenge, "fixture").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_CHALLENGE_INVALID_OR_EXPIRED"));
    }

    @Test
    void keepsExistingIdentityAndRequiresDeviceVerificationAfterActivation() throws Exception {
        var first = tokens.verify(json(exchange(challenge(), "first").andExpect(status().isOk())).get("flowToken").asString());
        db.update("UPDATE users SET first_name='Edited', status='PENDING_EMAIL_VERIFICATION' WHERE id=?", first.userId());
        var next = json(exchange(challenge(), "next").andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("EMAIL_REQUIRED")));
        assertThat(tokens.verify(next.get("flowToken").asString()).userId()).isEqualTo(first.userId());
        assertThat(db.queryForObject("SELECT first_name FROM users WHERE id=?", String.class, first.userId())).isEqualTo("Edited");
        db.update("UPDATE users SET status='ACTIVE', email_verified_at=UTC_TIMESTAMP(6) WHERE id=?", first.userId());
        exchange(challenge(), "active").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEVICE_VERIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.nextAction").value("DEVICE_PROOF_REQUIRED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        db.update("UPDATE users SET status='DISABLED' WHERE id=?", first.userId());
        exchange(challenge(), "disabled").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void neverAcceptsEmailOrUserIdFromTheRequestAndDoesNotMergeAnEmailCollision() throws Exception {
        String email = subject + "@example.edu";
        db.update("""
                INSERT INTO users (role,status,university_email,email_normalized,created_at,updated_at)
                VALUES ('STUDENT','PENDING_EMAIL_VERIFICATION',?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, email, email);
        BigInteger prior = db.queryForObject("SELECT id FROM users WHERE email_normalized=?", BigInteger.class, email);
        var challenge = challenge();
        http.perform(post(EXCHANGE).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("provider", "GOOGLE", "challengeId", challenge.get("challengeId").asString(),
                        "idToken", "fixture", "email", email, "userId", prior.toString()))))
                .andExpect(status().isBadRequest());
        var response = json(exchange(challenge, "fixture").andExpect(status().isOk()));
        assertThat(tokens.verify(response.get("flowToken").asString()).userId()).isNotEqualTo(prior);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM users WHERE email_normalized=?", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void rejectsBadAssertionsAndNonceAndExpiredChallenges() throws Exception {
        var challenge = challenge();
        when(verifier.verify("invalid")).thenThrow(new GoogleIdentityVerifier.InvalidProof());
        request(challenge, "invalid").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_PROVIDER_INVALID")).andExpect(jsonPath("$.traceId").isNotEmpty());
        when(verifier.verify("unavailable")).thenThrow(new GoogleIdentityVerifier.VerificationUnavailable());
        request(challenge, "unavailable").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_PROVIDER_UNAVAILABLE"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        when(verifier.verify("wrong")).thenReturn(proof("wrong"));
        request(challenge, "wrong").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_NONCE_MISMATCH"));
        String nonce = challenge.get("nonce").asString();
        when(verifier.verify("predates-challenge")).thenReturn(new GoogleIdentityVerifier.Proof(subject, nonce,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(300), null, null));
        request(challenge, "predates-challenge").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_PROVIDER_INVALID"));
        when(verifier.verify("expires-before-consumption")).thenReturn(new GoogleIdentityVerifier.Proof(subject, nonce,
                Instant.now().minusSeconds(1), Instant.now(), null, null));
        request(challenge, "expires-before-consumption").andExpect(status().isUnauthorized());
        db.update("""
                UPDATE provider_auth_challenges SET created_at=UTC_TIMESTAMP(6)-INTERVAL 5 MINUTE,
                    expires_at=UTC_TIMESTAMP(6) WHERE challenge_id_digest=?
                """, UniSpaceJwtService.digest(challenge.get("challengeId").asString()));
        exchange(challenge, "expired").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_CHALLENGE_INVALID_OR_EXPIRED"));
        assertThat(identities()).isZero();
    }

    @Test
    void rollsBackConsumptionAndProvisioningOnFlowFailure() throws Exception {
        var challenge = challenge();
        doThrow(new DataAccessResourceFailureException("internal fixture")).when(store).insertFlow(any(),any(),any());
        exchange(challenge, "first").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_PROVIDER_UNAVAILABLE"));
        assertThat(identities()).isZero();
        assertThat(db.queryForObject("SELECT consumed_at FROM provider_auth_challenges WHERE challenge_id_digest=?",
                java.sql.Timestamp.class, UniSpaceJwtService.digest(challenge.get("challengeId").asString()))).isNull();
        reset(store);
        exchange(challenge, "second").andExpect(status().isOk());
        assertThat(identities()).isOne();
    }

    @Test
    void serializesTwoExchangesOfTheSameChallenge() throws Exception {
        JsonNode challenge = challenge();
        var statuses = concurrent(challenge, challenge);
        assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        assertThat(identities()).isOne();
    }

    @Test
    void reconcilesTwoIndependentFirstSignInsForTheSameGoogleSubject() throws Exception {
        var lookupBarrier = new CyclicBarrier(2);
        var lookups = new AtomicInteger();
        doAnswer(invocation -> {
            var account = invocation.callRealMethod();
            if (lookups.incrementAndGet() <= 2) lookupBarrier.await(10, TimeUnit.SECONDS);
            return account;
        }).when(store).findUserId(subject);
        String existingUsers = "SELECT COUNT(*) FROM users";
        int before = db.queryForObject(existingUsers, Integer.class);
        assertThat(concurrent(challenge(), challenge())).containsExactly(200, 200);
        assertThat(identities()).isOne();
        assertThat(lookups.get()).isGreaterThanOrEqualTo(3);
        assertThat(db.queryForObject(existingUsers, Integer.class)).isEqualTo(before + 1);
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM auth_flows f JOIN provider_identities p ON p.user_id=f.user_id
                WHERE p.provider_subject=?
                """, Integer.class, subject)).isEqualTo(2);
    }

    @Test
    void leavesAppleAndMalformedChallengeRequestsClosed() throws Exception {
        http.perform(post(CHALLENGES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"APPLE\"}")).andExpect(status().isServiceUnavailable());
        http.perform(post(CHALLENGES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"APPLE\",\"provider\":\"GOOGLE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        http.perform(post(CHALLENGES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"GOOGLE\",\"purpose\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bindsLinkChallengesToAnActiveSessionAndExcludesThemFromSignIn() throws Exception {
        String link = "{\"provider\":\"GOOGLE\",\"purpose\":\"LINK_PROVIDER\"}";
        http.perform(post(CHALLENGES).contentType(MediaType.APPLICATION_JSON).content(link)).andExpect(status().isUnauthorized());
        var flow = json(exchange(challenge(), "fixture").andExpect(status().isOk())).get("flowToken").asString();
        http.perform(post(CHALLENGES).header("Authorization", "Bearer " + flow)
                .contentType(MediaType.APPLICATION_JSON).content(link)).andExpect(status().isForbidden());
        var uid = tokens.verify(flow).userId();
        db.update("UPDATE users SET status='ACTIVE', email_verified_at=UTC_TIMESTAMP(6) WHERE id=?", uid);
        var device = new ECKeyGenerator(Curve.P_256).generate().toPublicJWK();
        db.update("INSERT INTO trusted_devices(id,user_id,device_key_hash,public_key_jwk,verified_at) VALUES (?,?,?,?, UTC_TIMESTAMP(6))",
                uid, uid, HexFormat.of().formatHex(device.computeThumbprint().decode()), device.toJSONString());
        db.update("""
                INSERT INTO auth_sessions(id,user_id,device_id,refresh_token_hash,created_at,expires_at)
                VALUES (?,?,?,SHA2(?,256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 1 DAY)
                """, uid, uid, uid, subject);
        String access = tokens.issueAccess(uid, uid);
        var issued = json(http.perform(post(CHALLENGES).header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content(link)).andExpect(status().isCreated()));
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM provider_auth_challenges
                WHERE purpose='LINK_PROVIDER' AND bound_user_id=? AND bound_session_id=? AND challenge_id_digest=?
                """, Integer.class, uid, uid, UniSpaceJwtService.digest(issued.get("challengeId").asString()))).isOne();
        exchange(issued, "wrong-purpose").andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/me/linked-providers").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
    }

    private List<Integer> concurrent(JsonNode first, JsonNode second) throws Exception {
        var barrier = new CyclicBarrier(2);
        when(verifier.verify(any())).thenAnswer(invocation -> {
            barrier.await(10, TimeUnit.SECONDS);
            return proof((String) invocation.getArgument(0));
        });
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = workers.submit(() -> request(first, first.get("nonce").asString()).andReturn().getResponse().getStatus());
            var b = workers.submit(() -> request(second, second.get("nonce").asString()).andReturn().getResponse().getStatus());
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }

    private Integer identities() {
        return db.queryForObject("SELECT COUNT(*) FROM provider_identities WHERE provider_subject=?", Integer.class, subject);
    }

    private GoogleIdentityVerifier.Proof proof(String nonce) {
        return new GoogleIdentityVerifier.Proof(subject, nonce, Instant.now(), Instant.now().plusSeconds(300), "Ada", "Lovelace");
    }

    private JsonNode challenge() throws Exception {
        return json(http.perform(post(CHALLENGES).contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"GOOGLE\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store")));
    }

    private ResultActions exchange(JsonNode challenge, String fixture) throws Exception {
        when(verifier.verify(fixture)).thenReturn(proof(challenge.get("nonce").asString()));
        return request(challenge, fixture);
    }

    private ResultActions request(JsonNode challenge, String fixture) throws Exception {
        return http.perform(post(EXCHANGE).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(
                Map.of("provider", "GOOGLE", "challengeId", challenge.get("challengeId").asString(), "idToken", fixture))));
    }

    private static JsonNode json(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }
}
