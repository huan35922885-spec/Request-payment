package tw.com.jsgcpa.paymentapproval.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;

class MasterDataAdminSessionTest {

    @Test
    void preservesMasterDataAdminAuthorityWhenSessionIsReloaded() {
        HttpSessionSecurityContextRepository repository =
                new HttpSessionSecurityContextRepository();
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        saveRequest.setSession(session);

        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                7L,
                "e2e.master-data-admin",
                "{bcrypt}hash",
                "E2E Master Data Admin",
                true,
                List.of(new SimpleGrantedAuthority("MASTER_DATA_ADMIN"))
        );
        Authentication authentication = UsernamePasswordAuthenticationToken
                .authenticated(
                        principal,
                        principal.getPassword(),
                        principal.getAuthorities()
                );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        repository.saveContext(context, saveRequest, saveResponse);

        MockHttpServletRequest reloadRequest = new MockHttpServletRequest();
        reloadRequest.setSession(session);
        SecurityContext reloaded = repository
                .loadDeferredContext(reloadRequest)
                .get();

        assertEquals(
                List.of("MASTER_DATA_ADMIN"),
                reloaded.getAuthentication().getAuthorities()
                        .stream()
                        .map(authority -> authority.getAuthority())
                        .toList()
        );
    }
}
