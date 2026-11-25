package com.unispace.infrastructure.security;

import com.unispace.identity.application.AuthenticationStateService;
import com.unispace.identity.application.AuthenticationStateService.AccessSession;
import com.unispace.identity.application.AuthenticationStateService.DisabledAccountException;
import com.unispace.identity.application.AuthenticationStateService.Role;
import java.math.BigInteger;
import java.security.Principal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthorization {
    private static final SimpleGrantedAuthority ACCESS = new SimpleGrantedAuthority("ACCESS");
    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");

    private final AuthenticationStateService state;

    public AdminAuthorization(AuthenticationStateService state) {
        this.state = state;
    }

    /** Use on an explicitly registered operation. The bearer filter derives authorities from current state. */
    public static AuthorizationManager<RequestAuthorizationContext> httpAccess() {
        return (authentication, context) -> {
            var current = authentication.get();
            return new AuthorizationDecision(current != null && current.isAuthenticated()
                    && current.getPrincipal() instanceof AccessSession
                    && current.getAuthorities().contains(ACCESS)
                    && current.getAuthorities().contains(ADMIN));
        };
    }

    /** Pass the authenticated principal before changing state, inside the business transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigInteger requireForMutation(Principal principal) {
        if (!(principal instanceof AccessSession session)) throw denied();
        try {
            if (state.lockAccessRole(session.userId(), session.sessionId()).orElse(null) != Role.ADMIN) {
                throw denied();
            }
        } catch (DisabledAccountException disabled) {
            throw denied();
        }
        return session.userId();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Current Admin access is required");
    }
}
