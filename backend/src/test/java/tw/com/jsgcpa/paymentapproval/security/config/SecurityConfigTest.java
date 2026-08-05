package tw.com.jsgcpa.paymentapproval.security.config;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void passwordEncoderUsesDelegatingPasswordEncoder() {
        String encoded = passwordEncoder.encode("test-password");

        assertTrue(encoded.startsWith("{"));
        assertNotEquals("test-password", encoded);
        assertTrue(passwordEncoder.matches("test-password", encoded));
    }

    @Test
    void masterDataGetRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/master/companies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/master/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/master/expense-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/master/expense-types/1/prices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void expenseTypeAdminGetRequiresMasterDataAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/master/expense-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/admin/master/expense-types")
                        .with(user("applicant")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/admin/master/expense-types")
                        .with(user("cashier")
                                .authorities(new SimpleGrantedAuthority("CASHIER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/admin/master/expense-types")
                        .with(user("payment-operator")
                                .authorities(new SimpleGrantedAuthority("PAYMENT_OPERATOR"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/admin/master/expense-types")
                        .with(user("master-admin")
                                .authorities(new SimpleGrantedAuthority("MASTER_DATA_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void expenseTypeAdminWriteRequiresMasterDataAdminAndCsrf() throws Exception {
        String body = "{\"code\":\"E2E_TEST\",\"name\":\"Test\",\"calculationType\":\"MANUAL\"}";

        mockMvc.perform(post("/api/admin/master/expense-types")
                        .with(user("master-admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        mockMvc.perform(post("/api/admin/master/expense-types")
                        .with(user("applicant")
                                .authorities(new SimpleGrantedAuthority("APPLICANT")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/admin/master/expense-types")
                        .with(user("master-admin")
                                .authorities(new SimpleGrantedAuthority("MASTER_DATA_ADMIN")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void authenticatedUserCanReadMasterDataWithoutRole() throws Exception {
        mockMvc.perform(get("/api/master/companies").with(user("applicant")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/master/customers").with(user("applicant")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/master/expense-types").with(user("applicant")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/master/expense-types/1/prices")
                        .with(user("applicant")))
                .andExpect(status().isOk());
    }

    @Test
    void detailGetRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/payment-requests/999999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void listGetRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/payment-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void managerPendingListRequiresAuthenticationButNoManagerAuthority() throws Exception {
        mockMvc.perform(get("/api/payment-requests")
                        .param("scope", "MANAGER_PENDING"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void exposesCsrfTokenForFrontend() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"));
    }

    @Test
    void rejectsUnsafeRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
    }

    @Test
    void invalidPostReachesValidationInsteadOfSecurity403() throws Exception {
                mockMvc.perform(post("/api/payment-requests/drafts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void submitPostRequiresAuthenticationWithCsrf() throws Exception {
        mockMvc.perform(post("/api/payment-requests/1/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void managerApprovePostRequiresAuthenticationWithCsrf() throws Exception {
        mockMvc.perform(post("/api/payment-requests/1/manager-approve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void managerRejectPostRequiresAuthenticationWithCsrf() throws Exception {
        mockMvc.perform(post("/api/payment-requests/1/manager-reject")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void cashierApprovePostRequiresAuthenticationWithCsrf() throws Exception {
        mockMvc.perform(post("/api/payment-requests/1/cashier-approve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void cashierRejectPostRequiresAuthenticationWithCsrf() throws Exception {
        mockMvc.perform(post("/api/payment-requests/1/cashier-reject")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void recordPaymentPostRequiresPaymentOperatorWithCsrf() throws Exception {
        mockMvc.perform(post("/api/payment-requests/1/record-payment")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"paidAt\":\"2026-07-31T13:30:00+08:00\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
