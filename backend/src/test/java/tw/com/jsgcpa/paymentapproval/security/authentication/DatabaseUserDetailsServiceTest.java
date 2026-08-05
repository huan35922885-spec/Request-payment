package tw.com.jsgcpa.paymentapproval.security.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.security.entity.AppUserCredential;
import tw.com.jsgcpa.paymentapproval.security.entity.AppUserRole;
import tw.com.jsgcpa.paymentapproval.security.enums.SecurityRole;
import tw.com.jsgcpa.paymentapproval.security.repository.AppUserCredentialRepository;
import tw.com.jsgcpa.paymentapproval.security.repository.AppUserRoleRepository;

@ExtendWith(MockitoExtension.class)
class DatabaseUserDetailsServiceTest {

    @Mock
    private AppUserCredentialRepository credentialRepository;

    @Mock
    private AppUserRoleRepository roleRepository;

    @Mock
    private AppUserCredential credential;

    @Mock
    private AppUser user;

    @Mock
    private AppUserRole cashierRole;

    @Mock
    private AppUserRole paymentOperatorRole;

    @Mock
    private AppUserRole masterDataAdminRole;

    private DatabaseUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new DatabaseUserDetailsService(
                credentialRepository,
                roleRepository
        );
    }

    private void stubCredentialUser() {
        when(credential.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(42L);
        when(user.getUsername()).thenReturn("operator");
        when(user.getDisplayName()).thenReturn("Payment Operator");
        when(credential.getPasswordHash()).thenReturn("{bcrypt}encoded-password");
    }

    @Test
    void mapsUserWithoutRoles() {
        when(user.getActive()).thenReturn(true);
        stubRoles();

        AuthenticatedUserPrincipal principal = loadPrincipal();

        assertEquals(42L, principal.getUserId());
        assertEquals("operator", principal.getUsername());
        assertEquals("Payment Operator", principal.getDisplayName());
        assertEquals("{bcrypt}encoded-password", principal.getPassword());
        assertTrue(principal.isEnabled());
        assertTrue(principal.getAuthorities().isEmpty());
    }

    @Test
    void mapsCashierAuthorityWithoutRolePrefix() {
        when(user.getActive()).thenReturn(true);
        when(cashierRole.getRoleCode()).thenReturn(SecurityRole.CASHIER);
        stubRoles(cashierRole);

        AuthenticatedUserPrincipal principal = loadPrincipal();

        assertEquals(List.of("CASHIER"), authorityNames(principal));
    }

    @Test
    void mapsPaymentOperatorAuthorityWithoutRolePrefix() {
        when(user.getActive()).thenReturn(true);
        when(paymentOperatorRole.getRoleCode())
                .thenReturn(SecurityRole.PAYMENT_OPERATOR);
        stubRoles(paymentOperatorRole);

        AuthenticatedUserPrincipal principal = loadPrincipal();

        assertEquals(List.of("PAYMENT_OPERATOR"), authorityNames(principal));
    }

    @Test
    void mapsMasterDataAdminAuthorityWithoutRolePrefixOrOtherAuthorities() {
        when(user.getActive()).thenReturn(true);
        when(masterDataAdminRole.getRoleCode())
                .thenReturn(SecurityRole.MASTER_DATA_ADMIN);
        stubRoles(masterDataAdminRole);

        AuthenticatedUserPrincipal principal = loadPrincipal();

        assertEquals(
                List.of("MASTER_DATA_ADMIN"),
                authorityNames(principal)
        );
        assertFalse(authorityNames(principal).contains("ROLE_MASTER_DATA_ADMIN"));
        assertFalse(authorityNames(principal).contains("CASHIER"));
        assertFalse(authorityNames(principal).contains("PAYMENT_OPERATOR"));
    }

    @Test
    void mapsBothAuthoritiesInRepositoryOrder() {
        when(user.getActive()).thenReturn(true);
        when(cashierRole.getRoleCode()).thenReturn(SecurityRole.CASHIER);
        when(paymentOperatorRole.getRoleCode())
                .thenReturn(SecurityRole.PAYMENT_OPERATOR);
        stubRoles(cashierRole, paymentOperatorRole);

        AuthenticatedUserPrincipal principal = loadPrincipal();

        assertEquals(
                List.of("CASHIER", "PAYMENT_OPERATOR"),
                authorityNames(principal)
        );
    }

    @Test
    void throwsUsernameNotFoundWhenCredentialDoesNotExist() {
        when(credentialRepository.findByUser_Username("missing"))
                .thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername("missing")
        );
    }

    @Test
    void mapsActiveUserToEnabledTrue() {
        when(user.getActive()).thenReturn(true);
        stubRoles();

        assertTrue(loadPrincipal().isEnabled());
    }

    @Test
    void mapsInactiveUserToEnabledFalse() {
        when(user.getActive()).thenReturn(false);
        stubRoles();

        assertFalse(loadPrincipal().isEnabled());
    }

    @Test
    void mapsPasswordHashWithoutChangingIt() {
        when(user.getActive()).thenReturn(true);
        stubRoles();

        assertEquals(
                "{bcrypt}encoded-password",
                loadPrincipal().getPassword()
        );
    }

    @Test
    void mapsUserIdFromAppUser() {
        when(user.getActive()).thenReturn(true);
        stubRoles();

        assertEquals(42L, loadPrincipal().getUserId());
        verify(roleRepository).findByUser_IdOrderByIdAsc(42L);
    }

    @Test
    void mapsDisplayNameWithoutKeepingEntityInPrincipal() {
        when(user.getActive()).thenReturn(true);
        stubRoles();

        AuthenticatedUserPrincipal principal = loadPrincipal();

        assertEquals("Payment Operator", principal.getDisplayName());
        assertFalse(principal.getClass().getDeclaredFields()[0].getType()
                .equals(AppUser.class));
        assertSame(String.class, principal.getUsername().getClass());
    }

    private void stubRoles(AppUserRole... roles) {
        stubCredentialUser();
        when(credentialRepository.findByUser_Username("operator"))
                .thenReturn(Optional.of(credential));
        when(roleRepository.findByUser_IdOrderByIdAsc(42L))
                .thenReturn(List.of(roles));
    }

    private AuthenticatedUserPrincipal loadPrincipal() {
        return assertInstanceOf(
                AuthenticatedUserPrincipal.class,
                service.loadUserByUsername("operator")
        );
    }

    private List<String> authorityNames(UserDetails principal) {
        return principal.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
