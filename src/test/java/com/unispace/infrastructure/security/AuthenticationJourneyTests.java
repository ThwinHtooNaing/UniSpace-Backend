package com.unispace.infrastructure.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.unispace.identity.application.GoogleIdentityVerifier;
import com.unispace.identity.application.RegistrationEmailSender;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "unispace.auth.google.audience=fixture.apps.googleusercontent.com"})
@ActiveProfiles("test")
@Testcontainers
@EnabledIfSystemProperty(named = "unispace.mysql.tests", matches = "true")
@Import({AuthenticationJourneyTests.Fixtures.class, SecurityConfigurationTests.Probes.class})
class AuthenticationJourneyTests {
    private static final String AUTH = "/api/v1/auth/";
    private static final String ME = "/api/v1/me/";
    private static final String DEVICE = AUTH + "device-challenges";
    private static final JsonMapper JSON = new JsonMapper();
    private static final AtomicLong IDS = new AtomicLong(10000);
    private static final AtomicLong jwtOffset = new AtomicLong();
    @TempDir static Path keys;
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired JdbcTemplate db;
    @Autowired Environment env;
    @Autowired Mailbox mail;
    @Autowired RSAKey fixtureSigner;
    private final HttpClient client = HttpClient.newHttpClient();
    private String subject;
    private String address;
    private String student;

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean RSAKey fixtureSigner() throws Exception { return JwtKeyConfigurationTests.key("fixture-google", 2048); }

        @Bean @Primary GoogleIdentityVerifier fixtureGoogle(RSAKey signer) throws Exception {
            return new GoogleIdTokenVerifier(GoogleIdTokenVerifier.configure(
                    NimbusJwtDecoder.withPublicKey(signer.toRSAPublicKey()).build()),
                    "fixture.apps.googleusercontent.com", Set.of(), Clock.systemUTC());
        }

        @Bean Mailbox journeyMailbox() { return new Mailbox(); }

        @Bean @Primary UniSpaceJwtService journeyTokens(JwtProperties properties) {
            return JwtKeyConfiguration.load(properties, new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return Clock.offset(Clock.system(zone), Duration.ofSeconds(jwtOffset.get())); }
                @Override public Instant instant() { return Instant.now().plusSeconds(jwtOffset.get()); }
            });
        }
    }

    static class Mailbox implements RegistrationEmailSender {
        private final Map<String,String> latest = new ConcurrentHashMap<>();
        @Override public void send(String email, String code) { latest.put(email, code); }

        String receive(String email) {
            return Objects.requireNonNull(latest.remove(email), "Expected a verification message");
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        var key = JwtKeyConfigurationTests.key("journey-signing",3072);
        var jwt = JwtKeyConfigurationTests.writeKeys(keys,key,key.toPublicJWK());
        registry.add("unispace.jwt.issuer", jwt::issuer);
        registry.add("unispace.jwt.keys.kid", jwt.keys()::kid);
        registry.add("unispace.jwt.keys.signing", jwt.keys()::signing);
        registry.add("unispace.jwt.keys.verification", jwt.keys()::verification);
        for (String property : List.of("unispace.auth.otp.hmac.key","unispace.auth.refresh.hmac.key")) {
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            String value = Base64.getEncoder().encodeToString(secret);
            registry.add(property, () -> value);
        }
    }

    @BeforeEach
    void prepare() {
        jwtOffset.set(0);
        subject = UUID.randomUUID().toString();
        student = "ST" + IDS.incrementAndGet();
        address = subject + "@school.example";
        // Only bootstrap institutional policy. All journey users, credentials and trust are created over HTTP.
        if (db.queryForObject("SELECT COUNT(*) FROM institutional_policies",Integer.class) == 0) {
            db.update("""
                    INSERT INTO users(id,role,status,created_at,updated_at)
                    VALUES (1,'ADMIN','PENDING_PROFILE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                    """);
            db.update("""
                    INSERT INTO institutional_policies(student_id_pattern,allowed_email_domains,revision,updated_by_user_id,updated_at)
                    VALUES ('ST[0-9]+',JSON_ARRAY('school.example'),1,1,UTC_TIMESTAMP(6))
                    """);
        }
    }

    @Test
    void trustedReturnExpiryRefreshSignOutAndReauthentication() throws Exception {
        ECKey firstKey = key();
        var initial = register(firstKey);
        var flow = signIn("DEVICE_VERIFICATION_REQUIRED");
        var proof = ok("POST", DEVICE, flow, Map.of("deviceId", field(initial,"deviceId")),201);
        var returning = prove(flow, proof, firstKey);
        assertThat(field(returning,"deviceId")).isEqualTo(field(initial,"deviceId"));
        protectedAccess(returning,200);
        jwtOffset.set(1231);
        protectedAccess(returning,401);
        var rotated = refresh(field(returning,"refreshToken"),200);
        protectedAccess(rotated,200);
        ok("POST",AUTH+"sign-out",field(rotated,"accessToken"),null,204);
        protectedAccess(rotated,401);
        protectedAccess(initial,401);
        String reauth = signIn("DEVICE_VERIFICATION_REQUIRED");
        var newSession = enrollWithOtp(reauth,Map.of("deviceId",field(rotated,"deviceId")),key());
        protectedAccess(newSession,200);
        db.update("UPDATE users SET status='DISABLED' WHERE email_normalized=?",address);
        protectedAccess(newSession,403);
        assertThat(field(refresh(field(newSession,"refreshToken"),403),"code")).isEqualTo("ACCOUNT_DISABLED");
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 6})
    void oldestSpentDigestRevokesTheLatestGenerationOnly(int rotations) throws Exception {
        var family = register(key());
        var sibling = enrollWithOtp(signIn("DEVICE_VERIFICATION_REQUIRED"),Map.of(),key());
        String oldest = field(family,"refreshToken");
        String latest = oldest;
        for (int i=0; i<rotations; i++) {
            family = refresh(latest,200);
            latest = field(family,"refreshToken");
        }
        String tip = latest;
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM consumed_refresh_tokens h JOIN auth_sessions s ON s.id=h.session_id
                JOIN users u ON u.id=s.user_id WHERE u.email_normalized=?
                """,Integer.class,address)).isEqualTo(rotations);
        if (rotations == 2) {
            assertThat(field(refresh(oldest,401),"code")).isEqualTo("REFRESH_TOKEN_REUSED");
        } else {
            var start = new CyclicBarrier(2);
            try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                var replay = workers.submit(() -> {
                    start.await(10,TimeUnit.SECONDS);
                    return refresh(oldest,401);
                });
                var rotate = workers.submit(() -> {
                    start.await(10,TimeUnit.SECONDS);
                    return send("POST",AUTH+"refresh",null,Map.of("refreshToken",tip));
                });
                assertThat(field(replay.get(20,TimeUnit.SECONDS),"code")).isEqualTo("REFRESH_TOKEN_REUSED");
                var competing = rotate.get(20,TimeUnit.SECONDS);
                assertThat(competing.statusCode()).isIn(200,401);
                if (competing.statusCode()==200) {
                    var successor = JSON.readTree(competing.body());
                    assertThat(field(refresh(field(successor,"refreshToken"),401),"code")).isEqualTo("SESSION_REVOKED");
                    protectedAccess(successor,401);
                } else {
                    assertThat(field(JSON.readTree(competing.body()),"code")).isEqualTo("SESSION_REVOKED");
                }
            }
        }
        var tipError = field(refresh(tip,401),"code");
        // A concurrently rotated tip is itself a spent credential.
        assertThat(tipError).isIn("SESSION_REVOKED","REFRESH_TOKEN_REUSED");
        if (rotations==2) assertThat(tipError).isEqualTo("SESSION_REVOKED");
        protectedAccess(family,401);
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM auth_sessions s JOIN users u ON u.id=s.user_id
                WHERE u.email_normalized=? AND s.revoked_at IS NOT NULL
                """,Integer.class,address)).isOne();
        protectedAccess(sibling,200);
        protectedAccess(refresh(field(sibling,"refreshToken"),200),200);
    }

    @Test
    void proofRemainsBoundToItsOriginalUserAndFlowAcrossHttp() throws Exception {
        var key = key();
        var original = register(key);
        String owner = signIn("DEVICE_VERIFICATION_REQUIRED");
        var proof = ok("POST",DEVICE,owner,Map.of("deviceId", field(original,"deviceId")),201);
        String otherFlow = signIn("DEVICE_VERIFICATION_REQUIRED");
        subject = UUID.randomUUID().toString();
        address = subject + "@school.example";
        student = "ST" + IDS.incrementAndGet();
        var other = register(key());
        var foreignFlow = signIn("DEVICE_VERIFICATION_REQUIRED");
        String path = DEVICE+"/"+field(proof,"challengeId")+"/verify-proof";
        var signed = signature(proof,key);
        for (String flow : List.of(otherFlow,foreignFlow)) {
            assertThat(field(ok("POST",path,flow,Map.of("signature",signed),400),"code"))
                    .isEqualTo("DEVICE_CHALLENGE_INVALID_OR_EXPIRED");
        }
        var authorized = ok("POST",path,owner,Map.of("signature",signed),200);
        protectedAccess(authorized,200);
        ok("POST",path,owner,Map.of("signature",signed),401);
        ok("GET",ME+"linked-providers",field(other,"accessToken"),null,403);
        ok("POST",ME+"linked-providers",field(other,"accessToken"),Map.of(),403);
    }

    private JsonNode register(ECKey key) throws Exception {
        String flow = signIn("ONBOARDING_REQUIRED");
        ok("GET","/probe/member",flow,null,403);
        var initial = ok("GET",ME+"onboarding",flow,null,200);
        assertThat(field(initial,"nextAction")).isEqualTo("PROFILE_REQUIRED");
        ok("PUT",ME+"onboarding/profile",flow,Map.of("firstName","Ada","lastName","Example","studentId",student),200);
        var otp = ok("POST",ME+"university-email/challenges",flow,Map.of("email",address),201);
        String code = mail.receive(address);
        assertThat(otp.has("code")).isFalse();
        var active = ok("POST",ME+"university-email/verify",flow,Map.of("challengeId",field(otp,"challengeId"),"code",code),200);
        assertThat(field(active,"accountStatus")).isEqualTo("ACTIVE");
        assertThat(active.has("accessToken")).isFalse();
        var enrollment = ok("POST",DEVICE,flow,Map.of(),201);
        assertThat(field(enrollment,"nextAction")).isEqualTo("DEVICE_KEY_REGISTRATION_REQUIRED");
        assertThat(mail.latest.containsKey(address)).isFalse();
        return enroll(flow,enrollment,key);
    }

    private String signIn(String expected) throws Exception {
        var c = ok("POST",AUTH+"challenges",null,Map.of("provider","GOOGLE"),201);
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer("https://accounts.google.com")
                .audience("fixture.apps.googleusercontent.com").subject(subject).claim("nonce",field(c,"nonce"))
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).build();
        var google = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(fixtureSigner.getKeyID()).build(),claims);
        google.sign(new RSASSASigner(fixtureSigner));
        var exchange = ok("POST",AUTH+"provider-exchange",null,Map.of("provider","GOOGLE",
                "challengeId",field(c,"challengeId"),"idToken",google.serialize()),200);
        assertThat(field(exchange,"status")).isEqualTo(expected);
        assertThat(exchange.has("accessToken") || exchange.has("refreshToken")).isFalse();
        return field(exchange,"flowToken");
    }

    private JsonNode enrollWithOtp(String flow, Map<String,String> handle, ECKey key) throws Exception {
        var challenge = ok("POST",DEVICE,flow,handle,201);
        assertThat(field(challenge,"nextAction")).isEqualTo("DEVICE_OTP_REQUIRED");
        String path = DEVICE+"/"+field(challenge,"challengeId");
        ok("POST",path+"/verify-otp",flow,Map.of("code",mail.receive(address)),200);
        return enroll(flow,challenge,key);
    }

    private JsonNode enroll(String flow, JsonNode enrollment, ECKey key) throws Exception {
        var proof = ok("POST",DEVICE+"/"+field(enrollment,"challengeId")+"/register-key",flow,
                Map.of("publicKeyJwk",key.toPublicJWK().toJSONObject()),200);
        var session = prove(flow,proof,key);
        protectedAccess(session,200);
        return session;
    }

    private JsonNode prove(String flow, JsonNode proof, ECKey key) throws Exception {
        return ok("POST",DEVICE+"/"+field(proof,"challengeId")+"/verify-proof",flow,
                Map.of("signature",signature(proof,key)),200);
    }

    private static String signature(JsonNode proof, ECKey key) throws Exception {
        var signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(key.toECPrivateKey());
        signature.update(Base64.getUrlDecoder().decode(field(proof,"payloadToSign")));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private static ECKey key() throws Exception { return new ECKeyGenerator(Curve.P_256).generate(); }

    private JsonNode refresh(String credential,int status) throws Exception {
        return ok("POST",AUTH+"refresh",null,Map.of("refreshToken",credential),status);
    }

    private void protectedAccess(JsonNode session,int status) throws Exception {
        ok("GET","/probe/member",field(session,"accessToken"),null,status);
    }

    private static String field(JsonNode json,String name) { return json.required(name).asString(); }

    private JsonNode ok(String method,String path,String bearer,Object body,int expected) throws Exception {
        var res = send(method,path,bearer,body);
        assertThat(res.statusCode()).as("%s %s",method,path).isEqualTo(expected);
        if (expected >= 400) {
            assertThat(res.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        }
        if (res.body().length == 0 || path.equals("/probe/member") && expected==200) return null;
        return JSON.readTree(res.body());
    }

    private HttpResponse<byte[]> send(String method,String path,String bearer,Object body) throws Exception {
        var uri = URI.create("http://127.0.0.1:"+env.getRequiredProperty("local.server.port")+path);
        var req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15));
        if (bearer!=null) req.header("Authorization","Bearer "+bearer);
        var payload = HttpRequest.BodyPublishers.noBody();
        if (body!=null) {
            req.header("Content-Type","application/json");
            payload = HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body));
        }
        return client.send(req.method(method,payload).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
}
