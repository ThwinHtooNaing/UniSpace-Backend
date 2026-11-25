package com.unispace.identity.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.unispace.identity.application.AuthenticationStateService;
import com.unispace.identity.application.AuthenticationStateService.RegistrationFlow;
import com.unispace.identity.application.OnboardingService;
import com.unispace.identity.application.OnboardingService.Failure;
import com.unispace.identity.application.OnboardingService.Rejected;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1/me")
public class OnboardingController {
    private static final int MAX_REQUEST_BYTES = 4096;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final OnboardingService onboarding;

    public OnboardingController(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProfileResponse(String firstName, String lastName, String studentId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProgressResponse(String accountStatus, String nextAction, ProfileResponse profile, String universityEmailMasked) {
        static ProgressResponse from(OnboardingService.Progress progress) {
            var p = progress.profile();
            return new ProgressResponse(progress.accountStatus(), progress.nextAction(),
                    new ProfileResponse(p.firstName(), p.lastName(), p.studentId()), progress.universityEmailMasked());
        }
    }

    @GetMapping("/onboarding")
    ResponseEntity<ProgressResponse> progress(@AuthenticationPrincipal RegistrationFlow flow) {
        return noStore(HttpStatus.OK).body(ProgressResponse.from(onboarding.progress(requireFlow(flow))));
    }

    @PutMapping(path = "/onboarding/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ProgressResponse> profile(@AuthenticationPrincipal RegistrationFlow flow, HttpServletRequest request) {
        var body = read(request, Set.of("firstName", "lastName", "studentId"));
        var progress = onboarding.saveProfile(requireFlow(flow), body.get("firstName").asString(),
                body.get("lastName").asString(), body.get("studentId").asString());
        return noStore(HttpStatus.OK).body(ProgressResponse.from(progress));
    }

    @PostMapping(path = "/university-email/challenges", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<OnboardingService.IssuedCode> issue(@AuthenticationPrincipal RegistrationFlow flow, HttpServletRequest request) {
        var body = read(request, Set.of("email"));
        return noStore(HttpStatus.CREATED).body(onboarding.issue(requireFlow(flow), body.get("email").asString()));
    }

    @PostMapping(path = "/university-email/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<OnboardingService.VerifiedEmail> verify(@AuthenticationPrincipal RegistrationFlow flow,
                                                          HttpServletRequest request) {
        var body = read(request, Set.of("challengeId", "code"));
        return noStore(HttpStatus.OK).body(onboarding.verify(requireFlow(flow), body.get("challengeId").asString(),
                body.get("code").asString()));
    }

    private static RegistrationFlow requireFlow(RegistrationFlow flow) {
        if (flow == null) throw new Rejected(Failure.ACCESS_DENIED);
        return flow;
    }

    private static JsonNode read(HttpServletRequest request, Set<String> fields) {
        try {
            byte[] bytes = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
            if (bytes.length > MAX_REQUEST_BYTES) throw new Rejected(Failure.VALIDATION_FAILED);
            var node = JSON.readTree(bytes);
            if (node == null || !node.isObject() || !fields.equals(node.propertyNames())) {
                throw new Rejected(Failure.VALIDATION_FAILED);
            }
            for (String field : fields) {
                if (!node.get(field).isString()) throw new Rejected(Failure.VALIDATION_FAILED);
            }
            return node;
        } catch (IOException | JacksonException invalid) {
            throw new Rejected(Failure.VALIDATION_FAILED);
        }
    }

    @ExceptionHandler(Rejected.class)
    ResponseEntity<Map<String, Object>> rejected(Rejected rejected) {
        var status = switch (rejected.failure()) {
            case AUTH_FLOW_EXPIRED -> HttpStatus.UNAUTHORIZED;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case STUDENT_ID_CONFLICT, EMAIL_CONFLICT -> HttpStatus.CONFLICT;
            case OTP_RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case EMAIL_SERVICE_UNAVAILABLE, AUTH_STATE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return problem(status, rejected.failure().name());
    }

    @ExceptionHandler(AuthenticationStateService.DisabledAccountException.class)
    ResponseEntity<Map<String, Object>> disabled() {
        return problem(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class, IllegalStateException.class})
    ResponseEntity<Map<String, Object>> unavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, Failure.AUTH_STATE_UNAVAILABLE.name());
    }

    private static ResponseEntity.BodyBuilder noStore(HttpStatus status) {
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String code) {
        var response = noStore(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (status == HttpStatus.UNAUTHORIZED) response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return response.body(Map.of("type", "about:blank", "title", status.getReasonPhrase(), "status", status.value(),
                "code", code, "traceId", UUID.randomUUID().toString()));
    }
}
