package com.unispace.administration.api;

import com.unispace.administration.application.UserDirectory;
import com.unispace.administration.application.UserDirectory.Page;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUsersController {
    private static final Set<String> LIST_PARAMETERS = Set.of("cursor", "limit");
    private static final Pattern ID = Pattern.compile("[1-9][0-9]{0,19}");
    private static final Pattern LIMIT = Pattern.compile("[1-9][0-9]?|100");
    private static final int DEFAULT_LIMIT = 25;

    private final UserDirectory directory;

    public AdminUsersController(UserDirectory directory) {
        this.directory = directory;
    }

    @GetMapping
    ResponseEntity<Page> list(@RequestParam MultiValueMap<String, String> parameters) {
        if (!LIST_PARAMETERS.containsAll(parameters.keySet())
                || parameters.values().stream().anyMatch(values -> values.size() != 1)) {
            throw new InvalidRequest();
        }
        String cursor = parameters.getFirst("cursor");
        String size = parameters.getFirst("limit");
        BigInteger afterId = cursor == null ? BigInteger.ZERO : parseId(cursor);
        if (size != null && !LIMIT.matcher(size).matches()) throw new InvalidRequest();
        int limit = size == null ? DEFAULT_LIMIT : Integer.parseInt(size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(directory.list(afterId, limit));
    }

    @GetMapping("/{userId}")
    ResponseEntity<?> detail(@PathVariable("userId") String userId) {
        var entry = directory.find(parseId(userId));
        if (entry.isEmpty()) return problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entry.get());
    }

    private static BigInteger parseId(String value) {
        if (!ID.matcher(value).matches()) throw new InvalidRequest();
        var id = new BigInteger(value);
        if (id.bitLength() > Long.SIZE) throw new InvalidRequest();
        return id;
    }

    static final class InvalidRequest extends RuntimeException {}

    @ExceptionHandler(InvalidRequest.class)
    ResponseEntity<Map<String, Object>> invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, Object>> unavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "DIRECTORY_UNAVAILABLE");
    }

    private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Map.of("type", "about:blank", "title", status.getReasonPhrase(), "status", status.value(),
                        "code", code, "traceId", UUID.randomUUID().toString()));
    }
}
