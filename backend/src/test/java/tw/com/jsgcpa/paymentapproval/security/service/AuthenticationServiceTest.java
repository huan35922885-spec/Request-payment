package tw.com.jsgcpa.paymentapproval.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;
import tw.com.jsgcpa.paymentapproval.security.dto.response.AuthenticatedUserResponse;
import tw.com.jsgcpa.paymentapproval.security.entity.AppUserCredential;
import tw.com.jsgcpa.paymentapproval.security.enums.SecurityRole;
import tw.com.jsgcpa.paymentapproval.security.exception.AuthenticationBusinessException;
import tw.com.jsgcpa.paymentapproval.security.repository.AppUserCredentialRepository;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");
    private static final Instant LOGIN_INSTANT =
            Instant.parse("2026-08-03T01:00:00Z");
    private static final OffsetDateTime LOGIN_AT =
            OffsetDateTime.ofInstant(LOGIN_INSTANT, BUSINESS_ZONE);

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AppUserCredentialRepository credentialRepository;

    @Mock
    private AppUserCredential credential;

    @Mock
    private Authentication authentication;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(
                authenticationManager,
                credentialRepository,
                Clock.fixed(LOGIN_INSTANT, BUSINESS_ZONE)
        );
    }

    @Test
    void authenticatesWithUsernamePasswordTokenAndUpdatesLastLoginAt() {
        AuthenticatedUserPrincipal principal = principal(
                List.of(new SimpleGrantedAuthority("CASHIER"))
        );
        stubSuccessfulAuthentication(principal);

        Authentication result = service.authenticate("cashier", "secret");

        assertSame(authentication, result);
        ArgumentCaptor<Authentication> tokenCaptor =
                ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(tokenCaptor.capture());
        Authentication token = tokenCaptor.getValue();
        assertEquals("cashier", token.getName());
        assertEquals("secret", token.getCredentials());
        assertEquals(false, token.isAuthenticated());
        verify(credential).setLastLoginAt(LOGIN_AT);
    }

    @Test
    void mapsCashierRole() {
        AuthenticatedUserPrincipal principal = principal(List.of(
                new SimpleGrantedAuthority("CASHIER")
        ));

        AuthenticatedUserResponse response = service.toResponse(principal);

        assertEquals(42L, response.userId());
        assertEquals("cashier", response.username());
        assertEquals("E2E Cashier", response.displayName());
        assertEquals(
                List.of(SecurityRole.CASHIER),
                response.roles()
        );
    }

    @Test
    void mapsMasterDataAdminRoleWithoutAddingOtherRoles() {
        AuthenticatedUserPrincipal principal = principal(List.of(
                new SimpleGrantedAuthority("MASTER_DATA_ADMIN")
        ));

        AuthenticatedUserResponse response = service.toResponse(principal);

        assertEquals(
                List.of(SecurityRole.MASTER_DATA_ADMIN),
                response.roles()
        );
        assertEquals(
                "MASTER_DATA_ADMIN",
                SecurityRole.MASTER_DATA_ADMIN.name()
        );
        assertEquals(4, response.getClass().getRecordComponents().length);
    }

    @Test
    void mapsNoRoleUserToEmptyRoles() {
        AuthenticatedUserResponse response = service.toResponse(principal(List.of()));

        assertEquals(List.of(), response.roles());
    }

    @Test
    void mapsPrincipalFieldsWithoutReturningPassword() {
        AuthenticatedUserResponse response = service.toResponse(
                principal(List.of())
        );

        assertEquals(42L, response.userId());
        assertEquals("cashier", response.username());
        assertEquals("E2E Cashier", response.displayName());
        assertEquals(4, response.getClass().getRecordComponents().length);
    }

    @Test
    void mapsAuthenticationPrincipalToResponse() {
        AuthenticatedUserPrincipal principal = principal(List.of());
        when(authentication.getPrincipal()).thenReturn(principal);

        AuthenticatedUserResponse response = service.toResponse(authentication);

        assertEquals("cashier", response.username());
        assertEquals(List.of(), response.roles());
    }

    @Test
    void mapsBadCredentialsToGenericInvalidCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("details"));

        AuthenticationBusinessException exception = assertThrows(
                AuthenticationBusinessException.class,
                () -> service.authenticate("cashier", "wrong")
        );

        assertEquals("INVALID_CREDENTIALS", exception.getCode());
        verify(credentialRepository, never()).findById(any());
        verify(credential, never()).setLastLoginAt(any());
    }

    @Test
    void mapsDisabledUserToSameGenericInvalidCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("disabled"));

        AuthenticationBusinessException exception = assertThrows(
                AuthenticationBusinessException.class,
                () -> service.authenticate("cashier", "secret")
        );

        assertEquals("INVALID_CREDENTIALS", exception.getCode());
        verify(credentialRepository, never()).findById(any());
    }

    @Test
    void failedAuthenticationDoesNotUpdateLastLoginAt() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("wrong"));

        assertThrows(
                AuthenticationBusinessException.class,
                () -> service.authenticate("cashier", "wrong")
        );

        verify(credential, never()).setLastLoginAt(any());
    }

    @Test
    void missingCredentialAfterAuthenticationIsConsistencyError() {
        AuthenticatedUserPrincipal principal = principal(List.of());
        when(authenticationManager.authenticate(any()))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(credentialRepository.findById(42L)).thenReturn(Optional.empty());

        AuthenticationBusinessException exception = assertThrows(
                AuthenticationBusinessException.class,
                () -> service.authenticate("cashier", "secret")
        );

        assertEquals("AUTHENTICATION_CONSISTENCY_ERROR", exception.getCode());
    }

    @Test
    void invalidAuthenticationPrincipalIsConsistencyError() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(new Object());

        AuthenticationBusinessException exception = assertThrows(
                AuthenticationBusinessException.class,
                () -> service.authenticate("cashier", "secret")
        );

        assertEquals("AUTHENTICATION_CONSISTENCY_ERROR", exception.getCode());
        verify(credentialRepository, never()).findById(any());
    }

    private void stubSuccessfulAuthentication(
            AuthenticatedUserPrincipal principal
    ) {
        when(authenticationManager.authenticate(any()))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(credentialRepository.findById(42L))
                .thenReturn(Optional.of(credential));
    }

    private AuthenticatedUserPrincipal principal(
            List<SimpleGrantedAuthority> authorities
    ) {
        return new AuthenticatedUserPrincipal(
                42L,
                "cashier",
                "{bcrypt}hash",
                "E2E Cashier",
                true,
                authorities
        );
    }
}
