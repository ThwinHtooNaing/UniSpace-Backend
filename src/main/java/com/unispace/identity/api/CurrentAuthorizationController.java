package com.unispace.identity.api;

import com.unispace.identity.application.AuthenticationStateService.AccessSession;
import com.unispace.identity.application.AuthenticationStateService.Role;
import java.util.Arrays;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentAuthorizationController {
    record CurrentAuthorization(String userId, Role role) {}

    @GetMapping("/api/v1/me/authorization")
    ResponseEntity<CurrentAuthorization> current(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AccessSession session)) {
            throw new AccessDeniedException("An access session is required");
        }
        // The bearer filter built these authorities from the database for this request.
        Role role = Arrays.stream(Role.values())
                .filter(candidate -> authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_" + candidate)))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("A current account role is required"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CurrentAuthorization(session.userId().toString(), role));
    }
}
