package tw.com.jsgcpa.paymentapproval.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalAction;
import tw.com.jsgcpa.paymentapproval.common.exception.GlobalExceptionHandler;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CreatePaymentDraftItemRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CreatePaymentDraftRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CashierReviewPaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.RecordPaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.CashierReviewPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.CreatePaymentDraftResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestPageResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.SubmitPaymentDraftResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.ManagerReviewPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentRequestListScope;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;
import tw.com.jsgcpa.paymentapproval.payment.service.CashierReviewPaymentService;
import tw.com.jsgcpa.paymentapproval.payment.service.CreatePaymentDraftService;
import tw.com.jsgcpa.paymentapproval.payment.service.GetPaymentRequestDetailService;
import tw.com.jsgcpa.paymentapproval.payment.service.ListPaymentRequestsService;
import tw.com.jsgcpa.paymentapproval.payment.service.ManagerReviewPaymentService;
import tw.com.jsgcpa.paymentapproval.payment.service.RecordPaymentService;
import tw.com.jsgcpa.paymentapproval.payment.service.SubmitPaymentDraftService;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.PaymentRequestListQuery;
import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;
import tw.com.jsgcpa.paymentapproval.security.authentication.DatabaseUserDetailsService;
import tw.com.jsgcpa.paymentapproval.security.handler.RestAccessDeniedHandler;
import tw.com.jsgcpa.paymentapproval.security.handler.RestAuthenticationEntryPoint;

@WebMvcTest(PaymentRequestController.class)
@Import({
        GlobalExceptionHandler.class,
        PaymentRequestAuthenticationTest.SecurityTestConfiguration.class
})
class PaymentRequestAuthenticationTest {

    @TestConfiguration
    @EnableWebSecurity
    static class SecurityTestConfiguration {

        @Bean
        SecurityFilterChain securityFilterChain(
                HttpSecurity http,
                ObjectMapper objectMapper
        ) throws Exception {
            AuthenticationEntryPoint entryPoint =
                    new RestAuthenticationEntryPoint(objectMapper);
            AccessDeniedHandler deniedHandler =
                    new RestAccessDeniedHandler(objectMapper);
            CsrfTokenRepository csrfTokenRepository =
                    new HttpSessionCsrfTokenRepository();
            ((HttpSessionCsrfTokenRepository) csrfTokenRepository)
                    .setHeaderName("X-CSRF-TOKEN");
            ((HttpSessionCsrfTokenRepository) csrfTokenRepository)
                    .setParameterName("_csrf");

            http
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/api/payment-requests/drafts")
                            .authenticated()
                            .requestMatchers(
                                    org.springframework.http.HttpMethod.POST,
                                    "/api/payment-requests/*/submit"
                            )
                            .authenticated()
                            .requestMatchers(
                                    org.springframework.http.HttpMethod.POST,
                                    "/api/payment-requests/*/manager-approve",
                                    "/api/payment-requests/*/manager-reject"
                            )
                            .authenticated()
                            .requestMatchers(
                                    org.springframework.http.HttpMethod.POST,
                                    "/api/payment-requests/*/cashier-approve",
                                    "/api/payment-requests/*/cashier-reject"
                            )
                            .hasAuthority("CASHIER")
                            .requestMatchers(
                                    org.springframework.http.HttpMethod.POST,
                                    "/api/payment-requests/*/record-payment"
                            )
                            .hasAuthority("CASHIER")
                            .requestMatchers(
                                    org.springframework.http.HttpMethod.GET,
                                    "/api/payment-requests"
                            )
                            .authenticated()
                            .requestMatchers(
                                    org.springframework.http.HttpMethod.GET,
                                    "/api/payment-requests/*"
                            )
                            .authenticated()
                            .anyRequest().permitAll()
                    )
                    .exceptionHandling(exceptionHandling -> exceptionHandling
                            .authenticationEntryPoint(entryPoint)
                            .accessDeniedHandler(deniedHandler)
                    )
                    .formLogin(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .logout(AbstractHttpConfigurer::disable)
                    .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository));

            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatePaymentDraftService createPaymentDraftService;

    @MockitoBean
    private SubmitPaymentDraftService submitPaymentDraftService;

    @MockitoBean
    private ManagerReviewPaymentService managerReviewPaymentService;

    @MockitoBean
    private CashierReviewPaymentService cashierReviewPaymentService;

    @MockitoBean
    private RecordPaymentService recordPaymentService;

    @MockitoBean
    private GetPaymentRequestDetailService getPaymentRequestDetailService;

    @MockitoBean
    private ListPaymentRequestsService listPaymentRequestsService;

    @MockitoBean
    private DatabaseUserDetailsService databaseUserDetailsService;

    @Test
    void unauthenticatedDetailReturns401WithoutCallingService() throws Exception {
        mockMvc.perform(get("/api/payment-requests/5"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(getPaymentRequestDetailService, never()).getDetail(
                any(Long.class), any(Long.class), any(Boolean.class), any(Boolean.class));
    }

    @Test
    void authenticatedDetailPassesPrincipalAndAuthorityFlags() throws Exception {
        when(getPaymentRequestDetailService.getDetail(5L, 1L, true, false))
                .thenReturn(null);

        mockMvc.perform(get("/api/payment-requests/5")
                        .with(user(principalWithAuthorities(1L, "CASHIER"))))
                .andExpect(status().isOk());

        verify(getPaymentRequestDetailService)
                .getDetail(5L, 1L, true, false);
    }

    @Test
    void unauthenticatedListReturns401WithoutCsrf() throws Exception {
        mockMvc.perform(get("/api/payment-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(listPaymentRequestsService, never()).list(
                any(PaymentRequestListQuery.class),
                any(PaymentRequestListScope.class),
                any(Long.class)
        );
        verify(listPaymentRequestsService, never()).list(
                any(Integer.class), any(Integer.class), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void authenticatedListUsesPrincipalWithoutCsrf() throws Exception {
        when(listPaymentRequestsService.list(
                any(PaymentRequestListQuery.class),
                any(PaymentRequestListScope.class),
                any(Long.class)
        )).thenReturn(new PaymentRequestPageResponse(
                List.of(), 0, 20, 0, 0, true, true
        ));

        mockMvc.perform(get("/api/payment-requests")
                        .param("scope", "MY_REQUESTS")
                        .with(user(principal(1L))))
                .andExpect(status().isOk());

        verify(listPaymentRequestsService).list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.MY_REQUESTS),
                eq(1L),
                eq(false),
                eq(false)
        );
    }

    @Test
    void authenticatedListWithoutScopeReturns400() throws Exception {
        mockMvc.perform(get("/api/payment-requests")
                        .with(user(principal(1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_LIST_SCOPE_REQUIRED"));

        verify(listPaymentRequestsService, never()).list(
                any(PaymentRequestListQuery.class),
                any(PaymentRequestListScope.class),
                any(Long.class),
                any(Boolean.class),
                any(Boolean.class)
        );
    }

    @Test
    void cashierCanQueryPaymentPendingWithoutCsrf() throws Exception {
        when(listPaymentRequestsService.list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.PAYMENT_PENDING),
                eq(6L),
                eq(true),
                eq(false)
        )).thenReturn(new PaymentRequestPageResponse(
                List.of(), 0, 20, 0, 0, true, true
        ));

        mockMvc.perform(get("/api/payment-requests")
                        .param("scope", "PAYMENT_PENDING")
                        .with(user(principalWithAuthorities(6L, "CASHIER"))))
                .andExpect(status().isOk());

        verify(listPaymentRequestsService).list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.PAYMENT_PENDING),
                eq(6L),
                eq(true),
                eq(false)
        );
    }

    @Test
    void authenticatedNonCashierCannotQueryPaymentPending() throws Exception {
        when(listPaymentRequestsService.list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.PAYMENT_PENDING),
                eq(1L),
                eq(false),
                eq(false)
        )).thenThrow(new tw.com.jsgcpa.paymentapproval.payment.exception
                .PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN",
                        "目前登入者沒有付款待辦查看權限"
                ));

        mockMvc.perform(get("/api/payment-requests")
                        .param("scope", "PAYMENT_PENDING")
                        .with(user(principal(1L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN"));
    }

    @Test
    void unauthenticatedManagerPendingListReturns401WithoutCsrf() throws Exception {
        mockMvc.perform(get("/api/payment-requests")
                        .param("scope", "MANAGER_PENDING"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(listPaymentRequestsService, never()).list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.MANAGER_PENDING),
                any(Long.class),
                anyBoolean(),
                anyBoolean()
        );
    }

    @Test
    void authenticatedNonManagerCanQueryManagerPendingWithoutAuthorityOrCsrf()
            throws Exception {
        when(listPaymentRequestsService.list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.MANAGER_PENDING),
                eq(1L),
                eq(false),
                eq(false)
        )).thenReturn(new PaymentRequestPageResponse(
                List.of(), 0, 20, 0, 0, true, true
        ));

        mockMvc.perform(get("/api/payment-requests")
                        .param("scope", "MANAGER_PENDING")
                        .with(user(principal(1L))))
                .andExpect(status().isOk());

        verify(listPaymentRequestsService).list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.MANAGER_PENDING),
                eq(1L),
                eq(false),
                eq(false)
        );
    }

    @Test
    void cashierCanQueryCashierPendingWithoutCsrf() throws Exception {
        when(listPaymentRequestsService.list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.CASHIER_PENDING),
                eq(6L),
                eq(true),
                eq(false)
        )).thenReturn(new PaymentRequestPageResponse(
                List.of(), 0, 20, 0, 0, true, true
        ));

        mockMvc.perform(get("/api/payment-requests")
                        .param("scope", "CASHIER_PENDING")
                        .with(user(cashierPrincipal(6L))))
                .andExpect(status().isOk());

        verify(listPaymentRequestsService).list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.CASHIER_PENDING),
                eq(6L),
                eq(true),
                eq(false)
        );
    }

    @Test
    void authenticatedUserWithoutCashierCannotQueryCashierPending() throws Exception {
        when(listPaymentRequestsService.list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.CASHIER_PENDING),
                eq(1L),
                eq(false),
                eq(false)
        )).thenThrow(new tw.com.jsgcpa.paymentapproval.payment.exception
                .PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN",
                        "目前登入者沒有出納待辦查看權限"
                ));

        mockMvc.perform(get("/api/payment-requests")
                        .param("scope", "CASHIER_PENDING")
                        .with(user(principal(1L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN"));

        verify(listPaymentRequestsService).list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.CASHIER_PENDING),
                eq(1L),
                eq(false),
                eq(false)
        );
    }

    @Test
    void applicantOnlyUserCannotQueryCashierPending() throws Exception {
        when(listPaymentRequestsService.list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.CASHIER_PENDING),
                eq(6L),
                eq(false),
                eq(false)
        )).thenThrow(new tw.com.jsgcpa.paymentapproval.payment.exception
                .PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN",
                        "目前登入者沒有出納待辦查看權限"
                ));

        mockMvc.perform(get("/api/payment-requests")
                        .param("scope", "CASHIER_PENDING")
                        .with(user(principal(6L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN"));

        verify(listPaymentRequestsService).list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.CASHIER_PENDING),
                eq(6L),
                eq(false),
                eq(false)
        );
    }

    @Test
    void unauthenticatedCashierPendingListReturns401() throws Exception {
        mockMvc.perform(get("/api/payment-requests")
                        .param("scope", "CASHIER_PENDING"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(listPaymentRequestsService, never()).list(
                any(PaymentRequestListQuery.class),
                eq(PaymentRequestListScope.CASHIER_PENDING),
                any(Long.class),
                any(Boolean.class)
        );
    }

    @Test
    void authenticatedCreateUsesPrincipalUserId() throws Exception {
        CreatePaymentDraftRequest expectedRequest = validRequest();
        when(createPaymentDraftService.createDraft(1L, expectedRequest))
                .thenReturn(successResponse());

        mockMvc.perform(post("/api/payment-requests/drafts")
                        .with(user(principal(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isCreated());

        verify(createPaymentDraftService).createDraft(1L, expectedRequest);
    }

    @Test
    void unauthenticatedCreateWithCsrfReturns401() throws Exception {
        mockMvc.perform(post("/api/payment-requests/drafts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(createPaymentDraftService, never())
                .createDraft(any(Long.class), any(CreatePaymentDraftRequest.class));
    }

    @Test
    void authenticatedCreateWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(post("/api/payment-requests/drafts")
                        .with(user(principal(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        verify(createPaymentDraftService, never())
                .createDraft(any(Long.class), any(CreatePaymentDraftRequest.class));
    }

    @Test
    void authenticatedCreateWithCsrfStillValidatesRequestBody() throws Exception {
        mockMvc.perform(post("/api/payment-requests/drafts")
                        .with(user(principal(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(createPaymentDraftService, never())
                .createDraft(any(Long.class), any(CreatePaymentDraftRequest.class));
    }

    @Test
    void applicantSpoofCannotChangePrincipalApplicant() throws Exception {
        when(createPaymentDraftService.createDraft(eq(1L), any(CreatePaymentDraftRequest.class)))
                .thenReturn(successResponse());

        MvcResult result = mockMvc.perform(post("/api/payment-requests/drafts")
                        .with(user(principal(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicantId": 999999,
                                  "companyId": 2,
                                  "customerId": 3,
                                  "requestCategory": "EXPENSE",
                                  "reason": "test reason",
                                  "items": [{"expenseTypeId": 10, "manualAmount": 100.00}]
                                }
                                """))
                .andReturn();

        int status = result.getResponse().getStatus();
        if (status == 201) {
            verify(createPaymentDraftService)
                    .createDraft(eq(1L), any(CreatePaymentDraftRequest.class));
        } else {
            org.junit.jupiter.api.Assertions.assertEquals(400, status);
        }
        verify(createPaymentDraftService, never())
                .createDraft(eq(999999L), any(CreatePaymentDraftRequest.class));
    }

    @Test
    void authenticatedSubmitUsesPrincipalUserId() throws Exception {
        when(submitPaymentDraftService.submit(5L, 1L, 0L))
                .thenReturn(submitResponse());

        mockMvc.perform(post("/api/payment-requests/5/submit")
                        .with(user(principal(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus")
                        .value("PENDING_MANAGER"));

        verify(submitPaymentDraftService).submit(5L, 1L, 0L);
    }

    @Test
    void unauthenticatedSubmitWithCsrfReturns401() throws Exception {
        mockMvc.perform(post("/api/payment-requests/5/submit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(submitPaymentDraftService, never())
                .submit(any(Long.class), any(Long.class), any(Long.class));
    }

    @Test
    void authenticatedSubmitWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(post("/api/payment-requests/5/submit")
                        .with(user(principal(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        verify(submitPaymentDraftService, never())
                .submit(any(Long.class), any(Long.class), any(Long.class));
    }

    @Test
    void submitOwnershipErrorReturnsDomainForbidden() throws Exception {
        when(submitPaymentDraftService.submit(5L, 1L, 0L))
                .thenThrow(new tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_SUBMIT_FORBIDDEN",
                        "只有原申請人可以送出此請款草稿"
                ));

        mockMvc.perform(post("/api/payment-requests/5/submit")
                        .with(user(principal(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_SUBMIT_FORBIDDEN"));
    }

    @Test
    void authenticatedManagerApproveUsesPrincipalUserId() throws Exception {
        when(managerReviewPaymentService.approve(5L, 1L, 0L, "ok"))
                .thenReturn(managerResponse(ApprovalAction.MANAGER_APPROVE));

        mockMvc.perform(post("/api/payment-requests/5/manager-approve")
                        .with(user(principal(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"comment\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("MANAGER_APPROVE"));

        verify(managerReviewPaymentService).approve(5L, 1L, 0L, "ok");
    }

    @Test
    void authenticatedManagerRejectUsesPrincipalUserId() throws Exception {
        when(managerReviewPaymentService.reject(5L, 1L, 0L, "no"))
                .thenReturn(managerResponse(ApprovalAction.MANAGER_REJECT));

        mockMvc.perform(post("/api/payment-requests/5/manager-reject")
                        .with(user(principal(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"comment\":\"no\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("MANAGER_REJECT"));

        verify(managerReviewPaymentService).reject(5L, 1L, 0L, "no");
    }

    @Test
    void unauthenticatedManagerApproveWithCsrfReturns401() throws Exception {
        mockMvc.perform(post("/api/payment-requests/5/manager-approve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(managerReviewPaymentService, never())
                .approve(any(Long.class), any(Long.class), any(Long.class), any());
    }

    @Test
    void authenticatedManagerRejectWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(post("/api/payment-requests/5/manager-reject")
                        .with(user(principal(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        verify(managerReviewPaymentService, never())
                .reject(any(Long.class), any(Long.class), any(Long.class), any());
    }

    @Test
    void managerOwnershipErrorReturnsDomainForbidden() throws Exception {
        when(managerReviewPaymentService.approve(5L, 1L, 0L, null))
                .thenThrow(new tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_MANAGER_FORBIDDEN",
                        "只有目前主管快照對應的主管可以複核此請款單"
                ));

        mockMvc.perform(post("/api/payment-requests/5/manager-approve")
                        .with(user(principal(1L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_MANAGER_FORBIDDEN"));
    }

    @Test
    void authenticatedCashierApproveUsesPrincipalUserId() throws Exception {
        CashierReviewPaymentRequest request =
                new CashierReviewPaymentRequest(2L, "ok");
        when(cashierReviewPaymentService.approve(5L, 6L, request))
                .thenReturn(cashierResponse(ApprovalAction.CASHIER_APPROVE));

        mockMvc.perform(post("/api/payment-requests/5/cashier-approve")
                        .with(user(cashierPrincipal(6L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2,\"comment\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("CASHIER_APPROVE"));

        verify(cashierReviewPaymentService).approve(5L, 6L, request);
    }

    @Test
    void authenticatedCashierRejectUsesPrincipalUserId() throws Exception {
        CashierReviewPaymentRequest request =
                new CashierReviewPaymentRequest(2L, "no");
        when(cashierReviewPaymentService.reject(5L, 6L, request))
                .thenReturn(cashierResponse(ApprovalAction.CASHIER_REJECT));

        mockMvc.perform(post("/api/payment-requests/5/cashier-reject")
                        .with(user(cashierPrincipal(6L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2,\"comment\":\"no\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("CASHIER_REJECT"));

        verify(cashierReviewPaymentService).reject(5L, 6L, request);
    }

    @Test
    void unauthenticatedCashierApproveWithCsrfReturns401() throws Exception {
        mockMvc.perform(post("/api/payment-requests/5/cashier-approve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(cashierReviewPaymentService, never())
                .approve(any(Long.class), any(Long.class),
                        any(CashierReviewPaymentRequest.class));
    }

    @Test
    void authenticatedCashierRejectWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(post("/api/payment-requests/5/cashier-reject")
                        .with(user(cashierPrincipal(6L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        verify(cashierReviewPaymentService, never())
                .reject(any(Long.class), any(Long.class),
                        any(CashierReviewPaymentRequest.class));
    }

    @Test
    void authenticatedUserWithoutCashierAuthorityReturns403() throws Exception {
        mockMvc.perform(post("/api/payment-requests/5/cashier-approve")
                        .with(user(principal(6L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("沒有權限執行此操作"));

        verify(cashierReviewPaymentService, never())
                .approve(any(Long.class), any(Long.class),
                        any(CashierReviewPaymentRequest.class));
    }

    @Test
    void applicantDoesNotHaveCashierAuthority() throws Exception {
        mockMvc.perform(post("/api/payment-requests/5/cashier-reject")
                        .with(user(principal(7L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(cashierReviewPaymentService, never())
                .reject(any(Long.class), any(Long.class),
                        any(CashierReviewPaymentRequest.class));
    }

    @Test
    void masterDataAdminDoesNotHaveCashierOrPaymentOperatorAuthority()
            throws Exception {
        AuthenticatedUserPrincipal masterDataAdmin =
                principalWithAuthorities(7L, "MASTER_DATA_ADMIN");

        mockMvc.perform(post("/api/payment-requests/5/cashier-reject")
                        .with(user(masterDataAdmin))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/payment-requests/5/record-payment")
                        .with(user(masterDataAdmin))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":3,"+
                                "\"paidAt\":\"2026-07-31T13:30:00+08:00\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(cashierReviewPaymentService, never())
                .reject(any(Long.class), any(Long.class),
                        any(CashierReviewPaymentRequest.class));
        verify(recordPaymentService, never()).recordPayment(
                any(Long.class), any(Long.class), any(RecordPaymentRequest.class));
    }

    @Test
    void masterDataAdminDoesNotBypassManagerBusinessAuthorization()
            throws Exception {
        when(managerReviewPaymentService.approve(5L, 7L, 0L, null))
                .thenThrow(new tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_MANAGER_FORBIDDEN",
                        "manager authorization required"
                ));

        mockMvc.perform(post("/api/payment-requests/5/manager-approve")
                        .with(user(principalWithAuthorities(
                                7L,
                                "MASTER_DATA_ADMIN"
                        )))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_MANAGER_FORBIDDEN"));

        verify(managerReviewPaymentService).approve(5L, 7L, 0L, null);
    }

    @Test
    void authenticatedPaymentOperatorUsesPrincipalUserId() throws Exception {
        RecordPaymentRequest request = new RecordPaymentRequest(
                3L,
                OffsetDateTime.parse("2026-07-31T05:30:00Z"),
                PaymentMethod.BANK_TRANSFER,
                "E2E-TRANSFER-001",
                "已完成銀行轉帳"
        );
        when(recordPaymentService.recordPayment(5L, 6L, request))
                .thenReturn(null);

        mockMvc.perform(post("/api/payment-requests/5/record-payment")
                        .with(user(principalWithAuthorities(6L, "CASHIER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":3,\"paidAt\":\"2026-07-31T13:30:00+08:00\","
                                + "\"paymentMethod\":\"BANK_TRANSFER\","
                                + "\"paymentReference\":\"E2E-TRANSFER-001\","
                                + "\"paymentNote\":\"已完成銀行轉帳\"}"))
                .andExpect(status().isOk());

        verify(recordPaymentService).recordPayment(5L, 6L, request);
    }

    @Test
    void unauthenticatedRecordPaymentWithCsrfReturns401() throws Exception {
        mockMvc.perform(post("/api/payment-requests/5/record-payment")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":3,\"paidAt\":\"2026-07-31T13:30:00+08:00\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(recordPaymentService, never()).recordPayment(
                any(Long.class), any(Long.class), any(RecordPaymentRequest.class));
    }

    @Test
    void paymentOperatorWithoutCsrfReturnsInvalidCsrf() throws Exception {
        mockMvc.perform(post("/api/payment-requests/5/record-payment")
                        .with(user(principalWithAuthorities(6L, "CASHIER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":3,\"paidAt\":\"2026-07-31T13:30:00+08:00\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        verify(recordPaymentService, never()).recordPayment(
                any(Long.class), any(Long.class), any(RecordPaymentRequest.class));
    }

    @Test
    void authenticatedUserWithoutPaymentOperatorReturnsAccessDenied() throws Exception {
        mockMvc.perform(post("/api/payment-requests/5/record-payment")
                        .with(user(principal(6L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":3,\"paidAt\":\"2026-07-31T13:30:00+08:00\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("沒有權限執行此操作"));

        verify(recordPaymentService, never()).recordPayment(
                any(Long.class), any(Long.class), any(RecordPaymentRequest.class));
    }

    private AuthenticatedUserPrincipal principal(Long userId) {
        return principalWithAuthorities(userId, "APPLICANT");
    }

    private AuthenticatedUserPrincipal cashierPrincipal(Long userId) {
        return principalWithAuthorities(userId, "CASHIER");
    }

    private AuthenticatedUserPrincipal principalWithAuthorities(
            Long userId,
            String... authorityNames
    ) {
        return new AuthenticatedUserPrincipal(
                userId,
                "e2e.applicant",
                "{bcrypt}hash",
                "E2E Applicant",
                true,
                java.util.Arrays.stream(authorityNames)
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
    }

    private CashierReviewPaymentResponse cashierResponse(
            ApprovalAction action
    ) {
        return new CashierReviewPaymentResponse(
                5L,
                "PAY-20260803-000005",
                action,
                action == ApprovalAction.CASHIER_APPROVE
                        ? ApprovalStatus.APPROVED
                        : ApprovalStatus.REJECTED_CLOSED,
                PaymentStatus.UNPAID,
                6L,
                "E2E Cashier",
                null,
                OffsetDateTime.parse("2026-08-03T10:00:00+08:00"),
                3L
        );
    }

    private CreatePaymentDraftRequest validRequest() {
        return new CreatePaymentDraftRequest(
                2L,
                3L,
                RequestCategory.EXPENSE,
                "test reason",
                List.of(new CreatePaymentDraftItemRequest(
                        10L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("100.00"),
                        null,
                        null
                ))
        );
    }

    private String validRequestJson() {
        return """
                {
                  "companyId": 2,
                  "customerId": 3,
                  "requestCategory": "EXPENSE",
                  "reason": "test reason",
                  "items": [{"expenseTypeId": 10, "manualAmount": 100.00}]
                }
                """;
    }

    private CreatePaymentDraftResponse successResponse() {
        return new CreatePaymentDraftResponse(
                100L,
                "PAY-20260803-000001",
                1L,
                4L,
                2L,
                3L,
                RequestCategory.EXPENSE,
                "test reason",
                ApprovalStatus.DRAFT,
                PaymentStatus.UNPAID,
                BigDecimal.ZERO,
                List.of(),
                OffsetDateTime.parse("2026-08-03T10:00:00+08:00"),
                0L
        );
    }

    private SubmitPaymentDraftResponse submitResponse() {
        return new SubmitPaymentDraftResponse(
                5L,
                "PAY-20260803-000005",
                ApprovalStatus.PENDING_MANAGER,
                PaymentStatus.UNPAID,
                2L,
                "E2E Supervisor",
                OffsetDateTime.parse("2026-08-03T10:00:00+08:00"),
                1L
        );
    }

    private ManagerReviewPaymentResponse managerResponse(ApprovalAction action) {
        return new ManagerReviewPaymentResponse(
                5L,
                "PAY-20260803-000005",
                action,
                action == ApprovalAction.MANAGER_APPROVE
                        ? ApprovalStatus.PENDING_CASHIER
                        : ApprovalStatus.REJECTED_CLOSED,
                PaymentStatus.UNPAID,
                1L,
                "E2E Applicant",
                null,
                OffsetDateTime.parse("2026-08-03T10:00:00+08:00"),
                1L
        );
    }
}
