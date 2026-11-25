package com.unispace.infrastructure.security;

import com.unispace.identity.application.AuthenticationStateService;
import com.unispace.identity.application.AuthenticationStateService.AccessSession;
import com.unispace.identity.application.AuthenticationStateService.RegistrationFlow;
import com.unispace.identity.application.AuthenticationStateService.Role;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminAuthorizationTests {
    private final AuthenticationStateService state = mock(AuthenticationStateService.class);
    private final AdminAuthorization admins = new AdminAuthorization(state);
    private final AccessSession session = new AccessSession(BigInteger.ONE, BigInteger.TWO);
    private final RegistrationFlow flow = new RegistrationFlow(BigInteger.ONE, BigInteger.TWO, new byte[32]);

    @Test
    void httpRuleRequiresAnAccessPrincipalAndTheAdminAuthority() {
        var rule = AdminAuthorization.httpAccess();
        var access = new SimpleGrantedAuthority("ACCESS");
        var admin = new SimpleGrantedAuthority("ROLE_ADMIN");
        assertThat(rule.authorize(() -> UsernamePasswordAuthenticationToken.authenticated(
                session, null, List.of(access, admin)), null).isGranted()).isTrue();
        assertThat(rule.authorize(() -> UsernamePasswordAuthenticationToken.authenticated(
                session, null, List.of(access)), null).isGranted()).isFalse();
        assertThat(rule.authorize(() -> UsernamePasswordAuthenticationToken.authenticated(
                flow, null, List.of(access, admin)), null).isGranted()).isFalse();
        assertThat(rule.authorize(() -> UsernamePasswordAuthenticationToken.unauthenticated(
                session, null), null).isGranted()).isFalse();
    }

    @Test
    void mutationRejectsFlowAndMissingPrincipalsBeforeReadingTheDatabase() {
        assertThatThrownBy(() -> admins.requireForMutation(flow)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admins.requireForMutation(null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(state);
    }

    @Test
    void mutationUsesTheLockedRoleAndDeniesMissingOrDisabledBindings() {
        when(state.lockAccessRole(session.userId(), session.sessionId()))
                .thenReturn(Optional.of(Role.ADMIN), Optional.of(Role.MANAGEMENT), Optional.empty())
                .thenThrow(new AuthenticationStateService.DisabledAccountException());
        assertThat(admins.requireForMutation(session)).isEqualTo(session.userId());
        for (int rejected = 0; rejected < 3; rejected++) {
            assertThatThrownBy(() -> admins.requireForMutation(session)).isInstanceOf(AccessDeniedException.class);
        }
    }
}
