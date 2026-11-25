package com.unispace.infrastructure.security;

import com.unispace.identity.application.RegistrationEmailSender;
import com.unispace.identity.persistence.DeviceAuthenticationRepository;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.Signature;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfSystemProperty(named = "unispace.mysql.tests", matches = "true")
@Import(DeviceAuthenticationTests.MailFixture.class)
class DeviceAuthenticationTests {
    private static final String ROOT = "/api/v1/auth/device-challenges";
    private static final JsonMapper JSON = new JsonMapper();
    private static final AtomicLong IDS = new AtomicLong(1000);
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @TempDir static Path keys;
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired UniSpaceJwtService tokens;
    @Autowired Mailbox mailbox;
    @MockitoSpyBean DeviceAuthenticationRepository store;
    MockMvc http;
    BigInteger user;
    String token;
    String email;

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry properties) throws Exception {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.flyway.url", MYSQL::getJdbcUrl);
        properties.add("spring.flyway.user", MYSQL::getUsername);
        properties.add("spring.flyway.password", MYSQL::getPassword);
        var kp = JwtKeyConfigurationTests.key("onboarding", 3072);
        var p = JwtKeyConfigurationTests.writeKeys(keys, kp, kp.toPublicJWK());
        properties.add("unispace.jwt.issuer", p::issuer);
        properties.add("unispace.jwt.keys.kid", p.keys()::kid);
        properties.add("unispace.jwt.keys.signing", p.keys()::signing);
        properties.add("unispace.jwt.keys.verification", p.keys()::verification);
        var secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        properties.add("unispace.auth.otp.hmac.key", () -> Base64.getEncoder().encodeToString(secret));
        var refresh = new byte[32];
        new SecureRandom().nextBytes(refresh);
        properties.add("unispace.auth.refresh.hmac.key", () -> Base64.getEncoder().encodeToString(refresh));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailFixture {
        @Bean Mailbox mailbox() { return new Mailbox(); }
    }

    static class Mailbox implements RegistrationEmailSender {
        final Map<String, String> delivered = new ConcurrentHashMap<>();
        final AtomicLong sent = new AtomicLong();
        volatile boolean fail;

        @Override
        public void send(String destination, String code) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (fail) throw new DeliveryFailed();
            delivered.put(destination, code);
            sent.incrementAndGet();
        }
    }

    @BeforeEach
    void prepare() {
        http = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mailbox.fail = false;
        user = newUser();
        email = "s" + user + "@university.example";
        token = newFlow(user);
        db.update("DELETE FROM institutional_policies");
        db.update("""
                INSERT INTO institutional_policies (student_id_pattern,allowed_email_domains,revision,updated_by_user_id,updated_at)
                VALUES (?,JSON_ARRAY('university.example'),1,?,UTC_TIMESTAMP(6))
                """, "ST[0-9]{4,}", user);
    }

    @Test
    void firstRegistrationUsesItsExistingOtpAndCreatesTrustOnlyAfterProof() throws Exception {
        postJson("/api/v1/me/onboarding/profile", Map.of("firstName","Ada","lastName","Lovelace","studentId","ST"+user),true)
                .andExpect(status().isOk());
        var otp = asJson(postJson("/api/v1/me/university-email/challenges", Map.of("email",email),false)
                .andExpect(status().isCreated()));
        postJson("/api/v1/me/university-email/verify",
                Map.of("challengeId", value(otp,"challengeId"), "code", mailbox.delivered.get(email)),false)
                .andExpect(status().isOk());
        var key = key();
        long sent = mailbox.sent.get();
        var enrollment = asJson(start(Map.of()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nextAction").value("DEVICE_KEY_REGISTRATION_REQUIRED")));
        assertThat(mailbox.sent.get()).isEqualTo(sent);
        var proof = register(enrollment,key);
        assertThat(count("trusted_devices")).isZero();
        assertThat(count("auth_sessions")).isZero();
        var session = complete(proof,key);
        assertThat(tokens.verify(value(session,"accessToken")).kind()).isEqualTo(UniSpaceJwtService.Kind.ACCESS);
        assertThat(Base64.getUrlDecoder().decode(value(session,"refreshToken"))).hasSize(32);
        assertThat(db.queryForObject("SELECT refresh_token_hash FROM auth_sessions WHERE user_id=?", String.class,user))
                .matches("[0-9a-f]{64}").isNotEqualTo(value(session,"refreshToken"));
        assertThat(count("trusted_devices")).isOne();
        prove(proof,key).andExpect(status().isUnauthorized());
    }

    @Test
    void requiresOtpForNewDevicesAndKeepsResendBoundToTheExistingEnrollment() throws Exception {
        active();
        var first = asJson(start(Map.of()).andExpect(status().isCreated()).andExpect(jsonPath("$.nextAction").value("DEVICE_OTP_REQUIRED")));
        String path = ROOT + "/" + value(first,"challengeId");
        postJson(path+"/register-key",Map.of("publicKeyJwk",key().toPublicJWK().toJSONObject()),false)
                .andExpect(status().isForbidden());
        start(Map.of()).andExpect(status().isTooManyRequests());
        String oldCode = mailbox.delivered.get(email);
        db.update("""
                UPDATE email_challenges SET resend_available_at=UTC_TIMESTAMP(6) WHERE user_id=? AND purpose='NEW_DEVICE'
                """, user);
        var replacement = asJson(start(Map.of()).andExpect(status().isCreated()));
        assertThat(value(replacement,"challengeId")).isEqualTo(value(first,"challengeId"));
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM email_challenges WHERE user_id=? AND purpose='NEW_DEVICE' AND invalidated_at IS NOT NULL
                """,Integer.class,user)).isOne();
        String newCode = mailbox.delivered.get(email);
        assertThat(newCode).isNotEqualTo(oldCode);
        postJson(path+"/verify-otp",Map.of("code",oldCode),false).andExpect(status().isBadRequest());
        postJson(path+"/verify-otp",Map.of("code",newCode),false).andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("DEVICE_KEY_REGISTRATION_REQUIRED"));
        postJson(path+"/verify-otp",Map.of("code",newCode),false).andExpect(status().isBadRequest());
        ECKey key = key();
        complete(register(replacement,key),key);
    }

    @Test
    void expiresDeviceOtpAndPersistsTheFifthIncorrectAttempt() throws Exception {
        active();
        var enrollment = asJson(start(Map.of()).andExpect(status().isCreated()));
        String otp = ROOT+"/"+value(enrollment,"challengeId")+"/verify-otp";
        db.update("""
                UPDATE email_challenges SET expires_at=UTC_TIMESTAMP(6),resend_available_at=UTC_TIMESTAMP(6)
                WHERE user_id=? AND purpose='NEW_DEVICE'
                """,user);
        postJson(otp,Map.of("code",mailbox.delivered.get(email)),false).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
        start(Map.of()).andExpect(status().isCreated());
        for (int attempt = 1; attempt <= 5; attempt++) {
            postJson(otp,Map.of("code","incorrect"),false).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(attempt == 5 ? "OTP_ATTEMPTS_EXCEEDED" : "OTP_INVALID"));
        }
        postJson(otp,Map.of("code",mailbox.delivered.get(email)),false).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM email_challenges WHERE user_id=? AND purpose='NEW_DEVICE'
                    AND attempt_count=5 AND invalidated_at IS NOT NULL
                """,Integer.class,user)).isOne();
    }

    @Test
    void returningDeviceWorksAfterSessionExpiryAndLostOrRevokedDeviceRequiresOtp() throws Exception {
        ECKey key = key();
        var session = complete(register(eligible(),key),key);
        db.update("UPDATE auth_sessions SET expires_at=UTC_TIMESTAMP(6) WHERE user_id=?",user);
        long sent = mailbox.sent.get();
        token = newFlow(user);
        var proof = asJson(start(Map.of("deviceId", value(session,"deviceId"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nextAction").value("DEVICE_PROOF_REQUIRED")));
        complete(proof,key);
        assertThat(count("trusted_devices")).isOne();
        assertThat(mailbox.sent.get()).isEqualTo(sent);

        token = newFlow(user);
        var stale = asJson(start(Map.of("deviceId",value(session,"deviceId"))).andExpect(status().isCreated()));
        db.update("UPDATE trusted_devices SET revoked_at=UTC_TIMESTAMP(6) WHERE user_id=?",user);
        prove(stale,key).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DEVICE_TRUST_REVOKED"));
        start(Map.of("deviceId",value(session,"deviceId"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nextAction").value("DEVICE_OTP_REQUIRED"));
        db.update("UPDATE email_challenges SET resend_available_at=UTC_TIMESTAMP(6) WHERE user_id=?", user);
        token = newFlow(user);
        start(Map.of()).andExpect(status().isCreated()).andExpect(jsonPath("$.nextAction").value("DEVICE_OTP_REQUIRED"));
    }

    @Test
    void rejectsWrongProofsCrossFlowAndCrossAccountAndDoesNotTrustLegacyRows() throws Exception {
        ECKey key = key();
        var proof = register(eligible(),key);
        String owner = token;
        prove(proof,key()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DEVICE_SIGNATURE_INVALID"));
        token = newFlow(user);
        prove(proof,key).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DEVICE_CHALLENGE_INVALID_OR_EXPIRED"));
        var other = newUser();
        db.update("UPDATE users SET status='ACTIVE', university_email=?, email_verified_at=UTC_TIMESTAMP(6) WHERE id=?",email,other);
        token = newFlow(other);
        prove(proof,key).andExpect(status().isBadRequest());
        token = owner;
        assertThat(count("trusted_devices")).isZero();
        db.update("UPDATE device_signature_challenges SET created_at=UTC_TIMESTAMP(6)-INTERVAL 2 MINUTE,expires_at=UTC_TIMESTAMP(6) WHERE user_id=?",user);
        prove(proof,key).andExpect(status().isBadRequest());
        db.update("INSERT INTO trusted_devices(user_id,device_key_hash,verified_at) VALUES(?,REPEAT('00',32),UTC_TIMESTAMP(6))",user);
        start(Map.of("deviceId","d_" + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32])))
                // The independently verified registration enrollment still takes precedence.
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nextAction").value("DEVICE_KEY_REGISTRATION_REQUIRED"));
        token = newFlow(user);
        start(Map.of("deviceId","d_" + Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32])))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nextAction").value("DEVICE_OTP_REQUIRED"));
    }

    @Test
    void rollsBackProofTrustAndFlowWhenSessionWriteFails() throws Exception {
        ECKey key = key();
        var proof = register(eligible(),key);
        doThrow(new DataAccessResourceFailureException("internal fixture")).when(store)
                .createSession(any(),any(),any(),any(),any());
        prove(proof,key).andExpect(status().isServiceUnavailable());
        assertThat(count("trusted_devices")).isZero();
        assertThat(count("auth_sessions")).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM device_signature_challenges WHERE user_id=? AND consumed_at IS NULL",
                Integer.class,user)).isOne();
        reset(store);
        complete(proof,key);
    }

    @Test
    void concurrentProofCreatesOnlyOneSession() throws Exception {
        ECKey key = key();
        var proof = register(eligible(),key);
        String signature = sign(proof,key);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> submitProof(proof,signature).andReturn().getResponse().getStatus());
            var second = pool.submit(() -> submitProof(proof,signature).andReturn().getResponse().getStatus());
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 401);
        }
        assertThat(count("trusted_devices")).isOne();
        assertThat(count("auth_sessions")).isOne();
    }

    @Test
    void refusesUnauthorizedFlowsAndUnapprovedKeyMetadata() throws Exception {
        http.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        start(Map.of()).andExpect(status().isForbidden());
        var enrollment = eligible();
        var parts = new java.util.HashMap<>(key().toPublicJWK().toJSONObject());
        parts.put("use", "enc");
        postJson(ROOT+"/"+value(enrollment,"challengeId")+"/register-key",Map.of("publicKeyJwk",parts),false)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DEVICE_KEY_INVALID"));
        var first = key();
        register(enrollment,first);
        postJson(ROOT+"/"+value(enrollment,"challengeId")+"/register-key",
                Map.of("publicKeyJwk",key().toPublicJWK().toJSONObject()),false).andExpect(status().isConflict());
        assertThat(count("trusted_devices")).isZero();
    }

    private void active() {
        db.update("UPDATE users SET status='ACTIVE', university_email=?, email_verified_at=UTC_TIMESTAMP(6) WHERE id=?",email,user);
    }

    private JsonNode eligible() throws Exception {
        active();
        db.update("""
                INSERT INTO device_enrollments(auth_flow_id,authorization_verified_at,created_at,expires_at)
                SELECT id,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),expires_at FROM auth_flows WHERE id=?
                """,tokens.verify(token).bindingId());
        return asJson(start(Map.of()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nextAction").value("DEVICE_KEY_REGISTRATION_REQUIRED")));
    }

    private static ECKey key() throws Exception {
        return new ECKeyGenerator(Curve.P_256).generate();
    }

    private int count(String table) {
        return db.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id=?",Integer.class,user);
    }

    private ResultActions start(Map<String,Object> body) throws Exception { return postJson(ROOT,body,false); }

    private JsonNode register(JsonNode enrollment, ECKey key) throws Exception {
        return asJson(postJson(ROOT+"/"+value(enrollment,"challengeId")+"/register-key",
                Map.of("publicKeyJwk",key.toPublicJWK().toJSONObject()),false).andExpect(status().isOk()));
    }

    private JsonNode complete(JsonNode proof, ECKey key) throws Exception {
        return asJson(prove(proof,key).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(header().string("Cache-Control","no-store")));
    }

    private ResultActions prove(JsonNode proof, ECKey key) throws Exception { return submitProof(proof,sign(proof,key)); }

    private ResultActions submitProof(JsonNode proof, String signature) throws Exception {
        return postJson(ROOT+"/"+value(proof,"challengeId")+"/verify-proof",Map.of("signature",signature),false);
    }

    private static String sign(JsonNode proof, ECKey key) throws Exception {
        var signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(key.toECPrivateKey());
        signer.update(Base64.getUrlDecoder().decode(value(proof,"payloadToSign")));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    private ResultActions postJson(String route, Map<String,Object> body, boolean put) throws Exception {
        var request = put ? put(route) : post(route);
        return http.perform(request.header("Authorization","Bearer "+token)
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
    }

    private static String value(JsonNode node, String field) { return node.get(field).asString(); }
    private static JsonNode asJson(ResultActions response) throws Exception {
        return JSON.readTree(response.andReturn().getResponse().getContentAsByteArray());
    }

    private BigInteger newUser() {
        long id = IDS.incrementAndGet();
        db.update("""
                INSERT INTO users (id,role,status,created_at,updated_at)
                VALUES (?,'STUDENT','PENDING_PROFILE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """,id);
        return BigInteger.valueOf(id);
    }

    private String newFlow(BigInteger uid) {
        long id = IDS.incrementAndGet();
        db.update("""
                INSERT INTO provider_auth_challenges(id,challenge_id_digest,provider,purpose,nonce_digest,created_at,expires_at,consumed_at)
                VALUES (?,UNHEX(SHA2(?,256)),'GOOGLE','SIGN_IN',UNHEX(SHA2(?,256)),UTC_TIMESTAMP(6),
                    UTC_TIMESTAMP(6)+INTERVAL 5 MINUTE,UTC_TIMESTAMP(6))
                """,id,"challenge"+id,"nonce"+id);
        var reservation = tokens.prepareFlow();
        db.update("""
                INSERT INTO auth_flows(id,user_id,source_challenge_id,flow_jti_digest,created_at,expires_at)
                VALUES (?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)+INTERVAL 15 MINUTE)
                """,id,uid,id,reservation.jtiDigest());
        return reservation.sign().apply(uid,BigInteger.valueOf(id));
    }

}
