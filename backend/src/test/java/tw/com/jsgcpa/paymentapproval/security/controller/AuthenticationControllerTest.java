package tw.com.jsgcpa.paymentapproval.security.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tw.com.jsgcpa.paymentapproval.common.exception.GlobalExceptionHandler;
import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;
import tw.com.jsgcpa.paymentapproval.security.dto.response.AuthenticatedUserResponse;
import tw.com.jsgcpa.paymentapproval.security.exception.AuthenticationBusinessException;
import tw.com.jsgcpa.paymentapproval.security.service.AuthenticationService;

@WebMvcTest(AuthenticationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthenticationControllerTest {

    private static final String SYNTHETIC_TEST_PASSWORD =
            "synthetic-test-password-not-for-real-accounts";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private SecurityContextRepository securityContextRepository;

    @MockitoBean
    private SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @MockitoBean
    private tw.com.jsgcpa.paymentapproval.security.authentication.DatabaseUserDetailsService databaseUserDetailsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logsInAndSavesSessionSecurityContext() throws Exception {
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        AuthenticatedUserResponse response = response();
        when(authenticationService.authenticate("e2e.cashier", SYNTHETIC_TEST_PASSWORD))
                .thenReturn(authentication);
        when(authenticationService.toResponse(authentication))
                .thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"e2e.cashier\",\"password\":\""
                                + SYNTHETIC_TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(6))
                .andExpect(jsonPath("$.username").value("e2e.cashier"))
                .andExpect(jsonPath("$.displayName").value("E2E 測試出納"))
                .andExpect(jsonPath("$.roles[0]").value("CASHIER"))
                .andExpect(jsonPath("$.roles[1]").value("PAYMENT_OPERATOR"));

        verify(sessionAuthenticationStrategy).onAuthentication(
                same(authentication),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
        org.mockito.ArgumentCaptor<SecurityContext> contextCaptor =
                org.mockito.ArgumentCaptor.forClass(SecurityContext.class);
        verify(securityContextRepository).saveContext(
                contextCaptor.capture(),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
        assertEquals(
                authentication,
                contextCaptor.getValue().getAuthentication()
        );
    }

    @Test
    void logsInAndSavesMasterDataAdminRoleWithoutOtherAuthorities()
            throws Exception {
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        AuthenticatedUserResponse response = masterDataAdminResponse();
        when(authenticationService.authenticate(
                "e2e.master-data-admin",
                SYNTHETIC_TEST_PASSWORD
        )).thenReturn(authentication);
        when(authenticationService.toResponse(authentication))
                .thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"e2e.master-data-admin\",\"password\":\""
                                + SYNTHETIC_TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("MASTER_DATA_ADMIN"))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasSize(1)));

        org.mockito.ArgumentCaptor<SecurityContext> contextCaptor =
                org.mockito.ArgumentCaptor.forClass(SecurityContext.class);
        verify(securityContextRepository).saveContext(
                contextCaptor.capture(),
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
        assertEquals(
                authentication,
                contextCaptor.getValue().getAuthentication()
        );
    }

    @Test
    void validatesLoginBody() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(authenticationService, never()).authenticate(any(), any());
    }

    @Test
    void mapsInvalidCredentialsTo401Json() throws Exception {
        when(authenticationService.authenticate("unknown", "wrong"))
                .thenThrow(new AuthenticationBusinessException(
                        "INVALID_CREDENTIALS",
                        "帳號或密碼錯誤"
                ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"unknown\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("帳號或密碼錯誤"));
    }

    @Test
    void getsCurrentAuthenticatedUser() throws Exception {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                6L,
                "e2e.cashier",
                "{bcrypt}hash",
                "E2E 測試出納",
                true,
                List.of(
                        new SimpleGrantedAuthority("CASHIER"),
                        new SimpleGrantedAuthority("PAYMENT_OPERATOR")
                )
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        when(authenticationService.toResponse(authentication)).thenReturn(response());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("e2e.cashier"))
                .andExpect(jsonPath("$.roles[0]").value("CASHIER"));
    }

    @Test
    void getsCurrentAuthenticatedUserWithMasterDataAdminRole() throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        when(authenticationService.toResponse(authentication))
                .thenReturn(masterDataAdminResponse());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("MASTER_DATA_ADMIN"))
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void logsOutWithNoContent() throws Exception {
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
    }

    private AuthenticatedUserResponse response() {
        return new AuthenticatedUserResponse(
                6L,
                "e2e.cashier",
                "E2E 測試出納",
                List.of(
                        tw.com.jsgcpa.paymentapproval.security.enums.SecurityRole.CASHIER,
                        tw.com.jsgcpa.paymentapproval.security.enums.SecurityRole.PAYMENT_OPERATOR
                )
        );
    }

    private AuthenticatedUserResponse masterDataAdminResponse() {
        return new AuthenticatedUserResponse(
                7L,
                "e2e.master-data-admin",
                "E2E Master Data Admin",
                List.of(
                        tw.com.jsgcpa.paymentapproval.security.enums.SecurityRole.MASTER_DATA_ADMIN
                )
        );
    }
}
