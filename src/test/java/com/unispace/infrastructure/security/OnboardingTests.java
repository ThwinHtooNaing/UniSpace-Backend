package com.unispace.infrastructure.security;

import com.unispace.identity.application.RegistrationEmailSender;
import com.unispace.identity.persistence.OnboardingRepository;
import java.math.BigInteger;
import java.nio.file.Path;
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
@Import(OnboardingTests.MailFixture.class)
class OnboardingTests {
    private static final String ROOT = "/api/v1/me";
    private static final JsonMapper JSON = new JsonMapper();
    private static final AtomicLong IDS = new AtomicLong(1000);
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @TempDir static Path keys;
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired UniSpaceJwtService tokens;
    @Autowired Mailbox mailbox;
    @MockitoSpyBean OnboardingRepository store;
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
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailFixture {
        @Bean Mailbox mailbox() { return new Mailbox(); }
    }

    static class Mailbox implements RegistrationEmailSender {
        final Map<String, String> delivered = new ConcurrentHashMap<>();
        volatile boolean fail;

        @Override
        public void send(String destination, String code) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (fail) throw new DeliveryFailed();
            delivered.put(destination, code);
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
    void completesOnboardingWithoutCreatingAFullSession() throws Exception {
        request(get(ROOT + "/onboarding"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("PROFILE_REQUIRED"));
        saveProfile().andExpect(status().isOk()).andExpect(jsonPath("$.nextAction").value("EMAIL_REQUIRED"));
        String challenge = issue();
        request(get(ROOT + "/onboarding"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("EMAIL_VERIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.universityEmailMasked").value("***@university.example"));
        verify(challenge, mailbox.delivered.get(email)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.nextAction").value("DEVICE_KEY_REGISTRATION_REQUIRED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist()).andExpect(jsonPath("$.refreshToken").doesNotExist());
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM device_enrollments WHERE auth_flow_id=? AND authorization_verified_at IS NOT NULL
                    AND public_key_jwk IS NULL AND completed_at IS NULL
                """, Integer.class, tokens.verify(token).bindingId())).isOne();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=?", Integer.class, user)).isZero();
        verify(challenge, mailbox.delivered.get(email)).andExpect(status().isForbidden());
        request(get(ROOT + "/linked-providers"), token).andExpect(status().isForbidden());
    }

    @Test
    void checksAuthorizationPolicyAndProfileFields() throws Exception {
        http.perform(get(ROOT + "/onboarding")).andExpect(status().isUnauthorized());
        request(get(ROOT + "/onboarding"), tokens.issueAccess(user, user)).andExpect(status().isUnauthorized());
        challenge(email).andExpect(status().isForbidden());
        form(put(ROOT + "/onboarding/profile"), Map.of("firstName","Ada","lastName","Lovelace","studentId","bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STUDENT_ID_INVALID"));
        form(put(ROOT + "/onboarding/profile"), Map.of("firstName","Ada","lastName","Lovelace","studentId","ST"+user,"role","ADMIN"))
                .andExpect(status().isBadRequest());
        db.update("DELETE FROM institutional_policies");
        saveProfile().andExpect(status().isServiceUnavailable());
        assertThat(readStatus()).isEqualTo("PENDING_PROFILE");
    }

    @Test
    void rejectsDuplicateStudentIdAndDisallowedDomains() throws Exception {
        db.update("UPDATE users SET student_id_normalized=? WHERE id=?", "ST"+user, newUser());
        saveProfile().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDENT_ID_CONFLICT"));
        assertThat(readStatus()).isEqualTo("PENDING_PROFILE");
        db.update("UPDATE users SET student_id_normalized=NULL WHERE student_id_normalized=?", "ST"+user);
        saveProfile().andExpect(status().isOk());
        challenge("person@university.example.attacker.example").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_DOMAIN_NOT_ALLOWED"));
    }

    @Test
    void enforcesCooldownReplacementAndFlowBindingAcrossInterruptedSessions() throws Exception {
        saveProfile();
        String first = issue();
        String originalFlow = token;
        token = newFlow(user);
        request(get(ROOT + "/onboarding"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.studentId").value("ST"+user));
        verify(first, mailbox.delivered.get(email)).andExpect(status().isBadRequest());
        challenge(email).andExpect(status().isTooManyRequests());
        db.update("UPDATE email_challenges SET resend_available_at=UTC_TIMESTAMP(6) WHERE id=?", new BigInteger(first));
        String second = issue();
        assertThat(second).isNotEqualTo(first);
        String newFlow = token;
        token = originalFlow;
        verify(first, mailbox.delivered.get(email)).andExpect(status().isBadRequest());
        token = newFlow;
        verify(second, mailbox.delivered.get(email)).andExpect(status().isOk());
    }

    @Test
    void expiresAndInvalidatesAfterFiveIncorrectAttempts() throws Exception {
        saveProfile();
        String first = issue();
        db.update("UPDATE email_challenges SET expires_at=UTC_TIMESTAMP(6),resend_available_at=UTC_TIMESTAMP(6) WHERE id=?",
                new BigInteger(first));
        verify(first, mailbox.delivered.get(email)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_EXPIRED"));
        String fresh = issue();
        for (int attempt=1; attempt<=5; attempt++) {
            verify(fresh, "invalid-code").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(attempt == 5 ? "OTP_ATTEMPTS_EXCEEDED" : "OTP_INVALID"));
        }
        verify(fresh, mailbox.delivered.get(email)).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT attempt_count FROM email_challenges WHERE id=?", Integer.class,
                new BigInteger(fresh))).isEqualTo(5);
        assertThat(readStatus()).isEqualTo("PENDING_EMAIL_VERIFICATION");
    }

    @Test
    void failedDeliveryReleasesCooldownAndLeavesChallengeUnusable() throws Exception {
        saveProfile();
        mailbox.fail = true;
        challenge(email).andExpect(status().isServiceUnavailable());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM email_challenges WHERE user_id=? AND invalidated_at IS NULL",
                Integer.class,user)).isZero();
        mailbox.fail = false;
        issue();
    }

    @Test
    void bindsTheDigestToTheDestinationAndRejectsEmailCollisions() throws Exception {
        saveProfile();
        String challenge = issue();
        String code = mailbox.delivered.get(email);
        String alternate = "alt@university.example";
        var id = new BigInteger(challenge);
        db.update("UPDATE email_challenges SET destination_email=? WHERE id=?", alternate, id);
        verify(challenge, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_INVALID"));
        db.update("UPDATE email_challenges SET destination_email=? WHERE id=?", email, id);
        db.update("UPDATE users SET university_email=?,email_normalized=? WHERE id=?", email, email, newUser());
        verify(challenge, code).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_CONFLICT"));
        assertThat(readStatus()).isEqualTo("PENDING_EMAIL_VERIFICATION");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM email_challenges WHERE id=? AND consumed_at IS NULL",
                Integer.class,id)).isOne();
    }

    @Test
    void rollsBackActivationAndConsumptionWhenEnrollmentWriteFails() throws Exception {
        saveProfile();
        String challenge = issue();
        doThrow(new DataAccessResourceFailureException("internal fixture")).when(store).establishEnrollment(any());
        verify(challenge, mailbox.delivered.get(email)).andExpect(status().isServiceUnavailable());
        assertThat(readStatus()).isEqualTo("PENDING_EMAIL_VERIFICATION");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM email_challenges WHERE id=? AND consumed_at IS NULL", Integer.class,
                new BigInteger(challenge))).isOne();
        reset(store);
        verify(challenge, mailbox.delivered.get(email)).andExpect(status().isOk());
    }

    @Test
    void concurrentVerificationCanSucceedOnlyOnce() throws Exception {
        saveProfile();
        String challenge = issue();
        String code = mailbox.delivered.get(email);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> verify(challenge, code).andReturn().getResponse().getStatus());
            var second = pool.submit(() -> verify(challenge, code).andReturn().getResponse().getStatus());
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 403);
        }
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

    private String readStatus() { return db.queryForObject("SELECT status FROM users WHERE id=?", String.class,user); }
    private ResultActions saveProfile() throws Exception {
        return form(put(ROOT + "/onboarding/profile"), Map.of("firstName","Ada","lastName","Lovelace","studentId","ST"+user));
    }
    private ResultActions challenge(String address) throws Exception {
        return form(post(ROOT + "/university-email/challenges"), Map.of("email",address));
    }
    private ResultActions verify(String id, String code) throws Exception {
        return form(post(ROOT + "/university-email/verify"), Map.of("challengeId",id,"code",code));
    }
    private String issue() throws Exception {
        JsonNode json = JSON.readTree(challenge(email).andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresInSeconds").value(600)).andExpect(jsonPath("$.resendAfterSeconds").value(60))
                .andExpect(jsonPath("$.code").doesNotExist()).andReturn().getResponse().getContentAsByteArray());
        return json.get("challengeId").asString();
    }
    private ResultActions form(MockHttpServletRequestBuilder request, Map<String,String> body) throws Exception {
        return request(request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)),token);
    }
    private ResultActions request(MockHttpServletRequestBuilder request, String credential) throws Exception {
        return http.perform(request.header("Authorization","Bearer "+credential));
    }
}
