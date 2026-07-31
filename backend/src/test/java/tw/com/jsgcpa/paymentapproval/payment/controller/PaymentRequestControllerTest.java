package tw.com.jsgcpa.paymentapproval.payment.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalAction;
import tw.com.jsgcpa.paymentapproval.common.exception.GlobalExceptionHandler;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CreatePaymentDraftItemRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CreatePaymentDraftRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.CashierReviewPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.CreatePaymentDraftItemResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.CreatePaymentDraftResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.ManagerReviewPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestListItemResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestPageResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.RecordPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.SubmitPaymentDraftResponse;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.service.CreatePaymentDraftService;
import tw.com.jsgcpa.paymentapproval.payment.service.CashierReviewPaymentService;
import tw.com.jsgcpa.paymentapproval.payment.service.ManagerReviewPaymentService;
import tw.com.jsgcpa.paymentapproval.payment.service.RecordPaymentService;
import tw.com.jsgcpa.paymentapproval.payment.service.SubmitPaymentDraftService;
import tw.com.jsgcpa.paymentapproval.payment.service.GetPaymentRequestDetailService;
import tw.com.jsgcpa.paymentapproval.payment.service.ListPaymentRequestsService;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebMvcTest(PaymentRequestController.class)
@Import(GlobalExceptionHandler.class)
class PaymentRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private static final OffsetDateTime PAYMENT_PAID_AT = OffsetDateTime.parse(
            "2026-07-31T05:30:00Z"
    );
    private static final OffsetDateTime RESPONSE_PAID_AT = OffsetDateTime.parse(
            "2026-07-31T13:30:00+08:00"
    );

    @Test
    void listsPaymentRequestsWithDefaultQuery() throws Exception {
        when(listPaymentRequestsService.list(
                0, 20, null, null, null, null, null, null, null, null, null, null
        )).thenReturn(listPageResponse());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content").isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.page").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(20))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalPages").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.first").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.last").value(true));

        verify(listPaymentRequestsService).list(
                0, 20, null, null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    void listsPaymentRequestsWithCompleteFilters() throws Exception {
        when(listPaymentRequestsService.list(
                1,
                10,
                "000005",
                ApprovalStatus.APPROVED,
                PaymentStatus.PAID,
                RequestCategory.EXPENSE,
                1L,
                1L,
                1L,
                1L,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        )).thenReturn(listPageResponse());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests")
                        .param("page", "1")
                        .param("size", "10")
                        .param("requestNo", "000005")
                        .param("approvalStatus", "APPROVED")
                        .param("paymentStatus", "PAID")
                        .param("requestCategory", "EXPENSE")
                        .param("applicantId", "1")
                        .param("departmentId", "1")
                        .param("companyId", "1")
                        .param("customerId", "1")
                        .param("createdFrom", "2026-07-01")
                        .param("createdTo", "2026-07-31"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].id").value(5))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].requestNo")
                        .value("PAY-20260731-000005"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].approvalStatus")
                        .value("APPROVED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].paymentStatus")
                        .value("PAID"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.content[0].totalAmount")
                        .value(1620.50));

        verify(listPaymentRequestsService).list(
                1,
                10,
                "000005",
                ApprovalStatus.APPROVED,
                PaymentStatus.PAID,
                RequestCategory.EXPENSE,
                1L,
                1L,
                1L,
                1L,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        );
    }

    @Test
    void returnsEmptyPaymentRequestPage() throws Exception {
        when(listPaymentRequestsService.list(
                0, 20, null, null, null, null, null, null, null, null, null, null
        )).thenReturn(new PaymentRequestPageResponse(
                List.of(), 0, 20, 0, 0, true, true
        ));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.content").isEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalElements").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalPages").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.first").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.last").value(true));
    }

    @Test
    void rejectsInvalidListEnumQueryParameter() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests")
                        .param("approvalStatus", "UNKNOWN"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_QUERY_PARAMETER"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors").isEmpty());

        verifyListServiceNotCalled();
    }

    @Test
    void rejectsInvalidListDateQueryParameter() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests")
                        .param("createdFrom", "abc"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_QUERY_PARAMETER"));

        verifyListServiceNotCalled();
    }

    @Test
    void rejectsInvalidListNumberQueryParameter() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests")
                        .param("applicantId", "abc"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_QUERY_PARAMETER"));

        verifyListServiceNotCalled();
    }

    @Test
    void mapsInvalidListPageBusinessErrorTo400() throws Exception {
        when(listPaymentRequestsService.list(
                -1, 20, null, null, null, null, null, null, null, null, null, null
        )).thenThrow(new PaymentDraftBusinessException("INVALID_PAGE", "invalid page"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests")
                        .param("page", "-1"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_PAGE"));
    }

    @Test
    void mapsInvalidListSizeBusinessErrorTo400() throws Exception {
        when(listPaymentRequestsService.list(
                0, 101, null, null, null, null, null, null, null, null, null, null
        )).thenThrow(new PaymentDraftBusinessException("INVALID_PAGE_SIZE", "invalid size"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests")
                        .param("size", "101"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_PAGE_SIZE"));
    }

    @Test
    void mapsInvalidListDateRangeBusinessErrorTo400() throws Exception {
        when(listPaymentRequestsService.list(
                0,
                20,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 7, 1)
        )).thenThrow(new PaymentDraftBusinessException(
                "INVALID_DATE_RANGE", "invalid date range"
        ));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests")
                        .param("createdFrom", "2026-08-01")
                        .param("createdTo", "2026-07-01"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_DATE_RANGE"));
    }

    @Test
    void hidesUnexpectedListException() throws Exception {
        when(listPaymentRequestsService.list(
                0, 20, null, null, null, null, null, null, null, null, null, null
        )).thenThrow(new RuntimeException("sensitive list database details"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests"))
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INTERNAL_SERVER_ERROR"))
                .andExpect(MockMvcResultMatchers.content()
                        .string(not(containsString("sensitive list database details"))));
    }

    @Test
    void getsPaymentRequestDetailWithNestedDataAndWithoutStoragePath() throws Exception {
        when(getPaymentRequestDetailService.getDetail(5L)).thenReturn(detailResponse());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests/5"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(5))
                .andExpect(MockMvcResultMatchers.jsonPath("$.requestNo")
                        .value("PAY-20260731-000005"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.applicant.id")
                        .value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.applicant.username")
                        .value("applicant"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.department.id")
                        .value(7))
                .andExpect(MockMvcResultMatchers.jsonPath("$.department.code")
                        .value("FIN"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.supervisor.id")
                        .value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.company.id")
                        .value(8))
                .andExpect(MockMvcResultMatchers.jsonPath("$.customer.id")
                        .value(9))
                .andExpect(MockMvcResultMatchers.jsonPath("$.approvalStatus")
                        .value("APPROVED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentStatus")
                        .value("PAID"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalAmount")
                        .value(456.78))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].unitPrice")
                        .value(123.45))
                .andExpect(MockMvcResultMatchers.jsonPath("$.approvalHistories[0].action")
                        .value("PAYMENT_RECORDED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.attachments[0].originalFilename")
                        .value("receipt.pdf"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.attachments[0].storagePath")
                        .doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentMethod")
                        .value("BANK_TRANSFER"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paidAt")
                        .value("2026-07-31T13:30:00+08:00"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.version").value(4));

        verify(getPaymentRequestDetailService).getDetail(5L);
    }

    @Test
    void getsDraftDetailWithNullOptionalFieldsAndEmptyCollections() throws Exception {
        when(getPaymentRequestDetailService.getDetail(10L)).thenReturn(draftDetailResponse());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests/10"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.approvalStatus")
                        .value("DRAFT"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.supervisor")
                        .doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.submittedAt")
                        .doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.items").isEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.approvalHistories").isEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.attachments").isEmpty());

        verify(getPaymentRequestDetailService).getDetail(10L);
    }

    @Test
    void mapsDetailNotFoundTo404() throws Exception {
        when(getPaymentRequestDetailService.getDetail(99L)).thenThrow(
                new PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_NOT_FOUND", "payment request not found"
                )
        );

        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests/99"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAYMENT_REQUEST_NOT_FOUND"));
    }

    @Test
    void mapsInvalidDetailIdTo400() throws Exception {
        when(getPaymentRequestDetailService.getDetail(0L)).thenThrow(
                new PaymentDraftBusinessException(
                        "INVALID_PAYMENT_REQUEST_ID", "invalid payment request id"
                )
        );

        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests/0"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_PAYMENT_REQUEST_ID"));
    }

    @Test
    void hidesUnexpectedDetailException() throws Exception {
        when(getPaymentRequestDetailService.getDetail(5L)).thenThrow(
                new RuntimeException("sensitive detail database information")
        );

        mockMvc.perform(MockMvcRequestBuilders.get("/api/payment-requests/5"))
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INTERNAL_SERVER_ERROR"))
                .andExpect(MockMvcResultMatchers.content()
                        .string(not(containsString(
                                "sensitive detail database information"
                        ))));
    }

    @Test
    void recordsPaymentAndReturns200() throws Exception {
        when(recordPaymentService.record(
                5L,
                6L,
                3L,
                PAYMENT_PAID_AT,
                PaymentMethod.BANK_TRANSFER,
                "E2E-TRANSFER-001",
                "已完成銀行轉帳"
        )).thenReturn(recordPaymentResponse());

        performRecordPaymentRequest("""
                {
                  "paidById": 6,
                  "version": 3,
                  "paidAt": "2026-07-31T13:30:00+08:00",
                  "paymentMethod": "BANK_TRANSFER",
                  "paymentReference": "E2E-TRANSFER-001",
                  "paymentNote": "已完成銀行轉帳"
                }
                """)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(5))
                .andExpect(MockMvcResultMatchers.jsonPath("$.requestNo")
                        .value("PAY-20260731-000005"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.action")
                        .value("PAYMENT_RECORDED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.approvalStatus")
                        .value("APPROVED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentStatus")
                        .value("PAID"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paidById")
                        .value(6))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paidByName")
                        .value("E2E 測試出納"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paidAt")
                        .value("2026-07-31T13:30:00+08:00"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentMethod")
                        .value("BANK_TRANSFER"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentReference")
                        .value("E2E-TRANSFER-001"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentNote")
                        .value("已完成銀行轉帳"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.recordedAt")
                        .exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.version")
                        .value(4));

        verify(recordPaymentService, times(1)).record(
                5L,
                6L,
                3L,
                PAYMENT_PAID_AT,
                PaymentMethod.BANK_TRANSFER,
                "E2E-TRANSFER-001",
                "已完成銀行轉帳"
        );
    }

    @Test
    void acceptsNullOptionalPaymentFields() throws Exception {
        when(recordPaymentService.record(
                5L, 6L, 3L, PAYMENT_PAID_AT, null, null, null
        )).thenReturn(new RecordPaymentResponse(
                5L,
                "PAY-20260731-000005",
                ApprovalAction.PAYMENT_RECORDED,
                ApprovalStatus.APPROVED,
                PaymentStatus.PAID,
                6L,
                "E2E 測試出納",
                RESPONSE_PAID_AT,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-07-31T14:00:00+08:00"),
                4L
        ));

        performRecordPaymentRequest("""
                {
                  "paidById": 6,
                  "version": 3,
                  "paidAt": "2026-07-31T13:30:00+08:00",
                  "paymentMethod": null,
                  "paymentReference": null,
                  "paymentNote": null
                }
                """)
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentMethod")
                        .doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentReference")
                        .doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentNote")
                        .doesNotExist());

        verify(recordPaymentService).record(
                5L, 6L, 3L, PAYMENT_PAID_AT, null, null, null
        );
    }

    @Test
    void rejectsNullPaidByIdWithValidationError() throws Exception {
        performRecordPaymentRequest("""
                {"paidById":null,"version":3,
                 "paidAt":"2026-07-31T13:30:00+08:00"}
                """)
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value(hasItems("paidById")));

        verify(recordPaymentService, never()).record(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsZeroPaidByIdWithValidationError() throws Exception {
        performRecordPaymentRequest("""
                {"paidById":0,"version":3,
                 "paidAt":"2026-07-31T13:30:00+08:00"}
                """)
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"));

        verify(recordPaymentService, never()).record(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsNullPaymentVersionWithValidationError() throws Exception {
        performRecordPaymentRequest("""
                {"paidById":6,"version":null,
                 "paidAt":"2026-07-31T13:30:00+08:00"}
                """)
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value(hasItems("version")));

        verify(recordPaymentService, never()).record(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsNegativePaymentVersionWithValidationError() throws Exception {
        performRecordPaymentRequest("""
                {"paidById":6,"version":-1,
                 "paidAt":"2026-07-31T13:30:00+08:00"}
                """)
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"));

        verify(recordPaymentService, never()).record(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsNullPaidAtWithValidationError() throws Exception {
        performRecordPaymentRequest("""
                {"paidById":6,"version":3,"paidAt":null}
                """)
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value(hasItems("paidAt")));

        verify(recordPaymentService, never()).record(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsPaymentReferenceLongerThan100Characters() throws Exception {
        String reference = "x".repeat(101);

        performRecordPaymentRequest("{" +
                "\"paidById\":6,\"version\":3," +
                "\"paidAt\":\"2026-07-31T13:30:00+08:00\"," +
                "\"paymentReference\":\"" + reference + "\"}")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value(hasItems("paymentReference")));

        verify(recordPaymentService, never()).record(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsInvalidPaymentMethodAsInvalidRequestBody() throws Exception {
        performRecordPaymentRequest("""
                {"paidById":6,"version":3,
                 "paidAt":"2026-07-31T13:30:00+08:00",
                 "paymentMethod":"CREDIT_CARD"}
                """)
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("Request body is missing or invalid"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isEmpty());

        verify(recordPaymentService, never()).record(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsInvalidPaidAtAsInvalidRequestBody() throws Exception {
        performRecordPaymentRequest("""
                {"paidById":6,"version":3,
                 "paidAt":"not-a-date","paymentMethod":"BANK_TRANSFER"}
                """)
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isEmpty());

        verify(recordPaymentService, never()).record(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsEmptyRecordPaymentBody() throws Exception {
        performRecordPaymentRequest("")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("Request body is missing or invalid"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isEmpty());

        verify(recordPaymentService, never()).record(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void mapsPaymentRequestNotFoundTo404() throws Exception {
        stubRecordPaymentBusinessError(
                "PAYMENT_REQUEST_NOT_FOUND", "payment request not found"
        );

        performValidRecordPaymentRequest()
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAYMENT_REQUEST_NOT_FOUND"));
    }

    @Test
    void mapsPaidByNotFoundTo404() throws Exception {
        stubRecordPaymentBusinessError(
                "PAID_BY_NOT_FOUND", "paid by not found"
        );

        performValidRecordPaymentRequest()
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAID_BY_NOT_FOUND"));
    }

    @Test
    void mapsPaymentVersionConflictTo409() throws Exception {
        stubRecordPaymentBusinessError(
                "PAYMENT_REQUEST_VERSION_CONFLICT", "version conflict"
        );

        performValidRecordPaymentRequest()
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAYMENT_REQUEST_VERSION_CONFLICT"));
    }

    @Test
    void mapsPaymentNotApprovedTo409() throws Exception {
        stubRecordPaymentBusinessError(
                "PAYMENT_REQUEST_NOT_APPROVED", "not approved"
        );

        performValidRecordPaymentRequest()
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAYMENT_REQUEST_NOT_APPROVED"));
    }

    @Test
    void mapsAlreadyPaidTo409() throws Exception {
        stubRecordPaymentBusinessError(
                "PAYMENT_REQUEST_ALREADY_PAID", "already paid"
        );

        performValidRecordPaymentRequest()
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAYMENT_REQUEST_ALREADY_PAID"));
    }

    @Test
    void mapsInactivePaidByTo409() throws Exception {
        stubRecordPaymentBusinessError(
                "PAID_BY_INACTIVE", "paid by inactive"
        );

        performValidRecordPaymentRequest()
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAID_BY_INACTIVE"));
    }

    @Test
    void mapsInvalidPaidByIdTo400() throws Exception {
        stubRecordPaymentBusinessError(
                "INVALID_PAID_BY_ID", "invalid paid by id"
        );

        performValidRecordPaymentRequest()
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_PAID_BY_ID"));
    }

    @Test
    void mapsInvalidPaymentDateTo400() throws Exception {
        stubRecordPaymentBusinessError(
                "INVALID_PAYMENT_DATE", "invalid payment date"
        );

        performValidRecordPaymentRequest()
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_PAYMENT_DATE"));
    }

    @Test
    void hidesRecordPaymentUnexpectedExceptionDetails() throws Exception {
        when(recordPaymentService.record(
                5L, 6L, 3L, PAYMENT_PAID_AT,
                PaymentMethod.BANK_TRANSFER,
                "E2E-TRANSFER-001",
                "已完成銀行轉帳"
        )).thenThrow(new RuntimeException(
                "sensitive payment database details"
        ));

        performValidRecordPaymentRequest()
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INTERNAL_SERVER_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("An unexpected error occurred"))
                .andExpect(MockMvcResultMatchers.content()
                        .string(not(containsString(
                                "sensitive payment database details"
                        ))));
    }

    @Test
    void returnsCompleteRecordPaymentErrorFields() throws Exception {
        stubRecordPaymentBusinessError(
                "PAID_BY_INACTIVE", "Paid by is inactive"
        );

        performValidRecordPaymentRequest()
                .andExpect(MockMvcResultMatchers.jsonPath("$.timestamp")
                        .exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status")
                        .value(409))
                .andExpect(MockMvcResultMatchers.jsonPath("$.error")
                        .value("Conflict"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.path")
                        .value("/api/payment-requests/5/record-payment"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.rejectedValue")
                        .doesNotExist());
    }

    @Test
    void createsDraftAndReturns201() throws Exception {
        CreatePaymentDraftRequest request = validRequest();
        when(createPaymentDraftService.createDraft(any(CreatePaymentDraftRequest.class)))
                .thenReturn(successResponse());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/payment-requests/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(100))
                .andExpect(MockMvcResultMatchers.jsonPath("$.requestNo")
                        .value("PAY-20260730-000001"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.approvalStatus")
                        .value("DRAFT"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentStatus")
                        .value("UNPAID"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.totalAmount")
                        .value(100.00))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].amount")
                        .value(100.00));

        verify(createPaymentDraftService).createDraft(any(CreatePaymentDraftRequest.class));
    }

    @Test
    void returnsValidationErrorsForInvalidTopLevelRequest() throws Exception {
        String requestBody = """
                {
                  "applicantId": null,
                  "companyId": 2,
                  "customerId": 3,
                  "requestCategory": "EXPENSE",
                  "reason": "test",
                  "items": []
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/payment-requests/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.path")
                        .value("/api/payment-requests/drafts"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value(hasItems("applicantId", "items")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.rejectedValue")
                        .doesNotExist());

        verify(createPaymentDraftService, never())
                .createDraft(any(CreatePaymentDraftRequest.class));
    }

    @Test
    void returnsNestedValidationFieldPath() throws Exception {
        String requestBody = """
                {
                  "applicantId": 1,
                  "companyId": 2,
                  "customerId": 3,
                  "requestCategory": "EXPENSE",
                  "reason": "test",
                  "items": [{"expenseTypeId": null}]
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/payment-requests/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[0].field")
                        .value("items[0].expenseTypeId"));

        verify(createPaymentDraftService, never())
                .createDraft(any(CreatePaymentDraftRequest.class));
    }

    @Test
    void returnsInvalidRequestBodyForUnknownEnum() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/payment-requests/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestCategory\":\"UNKNOWN\"}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("Request body is missing or invalid"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isEmpty());

        verify(createPaymentDraftService, never())
                .createDraft(any(CreatePaymentDraftRequest.class));
    }

    @Test
    void mapsNotFoundBusinessExceptionTo404() throws Exception {
        when(createPaymentDraftService.createDraft(any(CreatePaymentDraftRequest.class)))
                .thenThrow(new PaymentDraftBusinessException(
                        "APPLICANT_NOT_FOUND",
                        "Applicant not found"
                ));

        performValidRequest()
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("APPLICANT_NOT_FOUND"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("Applicant not found"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.path")
                        .value("/api/payment-requests/drafts"));
    }

    @Test
    void mapsConflictBusinessExceptionTo409() throws Exception {
        when(createPaymentDraftService.createDraft(any(CreatePaymentDraftRequest.class)))
                .thenThrow(new PaymentDraftBusinessException(
                        "CUSTOMER_CATEGORY_MISMATCH",
                        "Customer category does not match"
                ));

        performValidRequest()
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("CUSTOMER_CATEGORY_MISMATCH"));
    }

    @Test
    void mapsInvalidCalculationBusinessExceptionTo400() throws Exception {
        when(createPaymentDraftService.createDraft(any(CreatePaymentDraftRequest.class)))
                .thenThrow(new PaymentDraftBusinessException(
                        "INVALID_CALCULATION_INPUT",
                        "Invalid calculation input"
                ));

        performValidRequest()
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_CALCULATION_INPUT"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        when(createPaymentDraftService.createDraft(any(CreatePaymentDraftRequest.class)))
                .thenThrow(new RuntimeException(
                        "database password or sensitive internal message"
                ));

        performValidRequest()
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INTERNAL_SERVER_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("An unexpected error occurred"))
                .andExpect(MockMvcResultMatchers.content()
                        .string(not(containsString("database password"))));
    }

    @Test
    void returnsStandardErrorResponseFields() throws Exception {
        when(createPaymentDraftService.createDraft(any(CreatePaymentDraftRequest.class)))
                .thenThrow(new PaymentDraftBusinessException(
                        "CUSTOMER_CATEGORY_MISMATCH",
                        "Customer category does not match"
                ));

        performValidRequest()
                .andExpect(MockMvcResultMatchers.jsonPath("$.timestamp").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(409))
                .andExpect(MockMvcResultMatchers.jsonPath("$.error")
                        .value("Conflict"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.path").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isArray());
    }

    @Test
    void submitsDraftAndReturns200() throws Exception {
        when(submitPaymentDraftService.submit(1L, 0L))
                .thenReturn(submitResponse());

        performSubmitRequest("{\"version\":0}")
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.requestNo")
                        .value("PAY-20260731-000001"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.approvalStatus")
                        .value("PENDING_MANAGER"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentStatus")
                        .value("UNPAID"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.supervisorId")
                        .value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.supervisorName")
                        .value("測試主管"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.submittedAt")
                        .exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.version")
                        .value(1));

        verify(submitPaymentDraftService, times(1)).submit(1L, 0L);
    }

    @Test
    void rejectsNullSubmitVersion() throws Exception {
        performSubmitRequest("{\"version\":null}")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value("version"));

        verify(submitPaymentDraftService, never()).submit(any(), any());
    }

    @Test
    void rejectsNegativeSubmitVersion() throws Exception {
        performSubmitRequest("{\"version\":-1}")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value("version"));

        verify(submitPaymentDraftService, never()).submit(any(), any());
    }

    @Test
    void rejectsInvalidSubmitRequestBody() throws Exception {
        performSubmitRequest("{\"version\":\"abc\"}")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("Request body is missing or invalid"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isEmpty());

        verify(submitPaymentDraftService, never()).submit(any(), any());
    }

    @Test
    void rejectsEmptySubmitRequestBody() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(
                                "/api/payment-requests/1/submit"
                        )
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isEmpty());

        verify(submitPaymentDraftService, never()).submit(any(), any());
    }

    @Test
    void mapsSubmitNotFoundTo404() throws Exception {
        when(submitPaymentDraftService.submit(1L, 0L))
                .thenThrow(new PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_NOT_FOUND",
                        "Payment request not found"
                ));

        performSubmitRequest("{\"version\":0}")
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAYMENT_REQUEST_NOT_FOUND"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("Payment request not found"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.path")
                        .value("/api/payment-requests/1/submit"));
    }

    @Test
    void mapsSubmitVersionConflictTo409() throws Exception {
        mapsSubmitBusinessError(
                "PAYMENT_REQUEST_VERSION_CONFLICT",
                "Payment request version conflict"
        );
    }

    @Test
    void mapsSubmitNotDraftTo409() throws Exception {
        mapsSubmitBusinessError(
                "PAYMENT_REQUEST_NOT_DRAFT",
                "Payment request is not in DRAFT status"
        );
    }

    @Test
    void mapsSupervisorNotFoundTo409() throws Exception {
        mapsSubmitBusinessError(
                "SUPERVISOR_NOT_FOUND",
                "No effective supervisor was found"
        );
    }

    @Test
    void mapsSupervisorConflictTo409() throws Exception {
        mapsSubmitBusinessError(
                "SUPERVISOR_CONFLICT",
                "Multiple effective supervisors were found"
        );
    }

    @Test
    void mapsSupervisorInactiveTo409() throws Exception {
        mapsSubmitBusinessError(
                "SUPERVISOR_INACTIVE",
                "Supervisor is inactive"
        );
    }

    @Test
    void hidesSubmitUnexpectedExceptionDetails() throws Exception {
        when(submitPaymentDraftService.submit(1L, 0L))
                .thenThrow(new RuntimeException("sensitive database information"));

        performSubmitRequest("{\"version\":0}")
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INTERNAL_SERVER_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("An unexpected error occurred"))
                .andExpect(MockMvcResultMatchers.content()
                        .string(not(containsString("sensitive database information"))));
    }

    @Test
    void returnsSubmitErrorResponseFields() throws Exception {
        when(submitPaymentDraftService.submit(1L, 0L))
                .thenThrow(new PaymentDraftBusinessException(
                        "SUPERVISOR_CONFLICT",
                        "Multiple effective supervisors were found"
                ));

        performSubmitRequest("{\"version\":0}")
                .andExpect(MockMvcResultMatchers.jsonPath("$.timestamp").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(409))
                .andExpect(MockMvcResultMatchers.jsonPath("$.error")
                        .value("Conflict"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.path")
                        .value("/api/payment-requests/1/submit"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isArray());
    }

    @Test
    void managerApprovesPaymentRequestAndReturns200() throws Exception {
        when(managerReviewPaymentService.approve(
                3L,
                2L,
                1L,
                "確認無誤"
        )).thenReturn(managerApproveResponse());

        performManagerApproveRequest(
                """
                {
                  "managerId": 2,
                  "version": 1,
                  "comment": "確認無誤"
                }
                """
        )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(3))
                .andExpect(MockMvcResultMatchers.jsonPath("$.requestNo")
                        .value("PAY-20260731-000003"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.action")
                        .value("MANAGER_APPROVE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.approvalStatus")
                        .value("PENDING_CASHIER"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentStatus")
                        .value("UNPAID"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.managerId")
                        .value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.managerName")
                        .value("E2E 測試主管"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.comment")
                        .value("確認無誤"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.actedAt")
                        .exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.version")
                        .value(2));

        verify(managerReviewPaymentService, times(1)).approve(
                3L,
                2L,
                1L,
                "確認無誤"
        );
        verify(managerReviewPaymentService, never())
                .reject(any(), any(), any(), any());
    }

    @Test
    void mapsManagerApproveVersionConflictTo409() throws Exception {
        mapsManagerApproveBusinessError(
                "PAYMENT_REQUEST_VERSION_CONFLICT",
                "Payment request version conflict"
        );
    }

    @Test
    void mapsManagerApproveNotPendingManagerTo409() throws Exception {
        mapsManagerApproveBusinessError(
                "PAYMENT_REQUEST_NOT_PENDING_MANAGER",
                "Payment request is not PENDING_MANAGER"
        );
    }

    @Test
    void mapsManagerApproveUnauthorizedTo403() throws Exception {
        when(managerReviewPaymentService.approve(3L, 9L, 1L, null))
                .thenThrow(new PaymentDraftBusinessException(
                        "MANAGER_NOT_AUTHORIZED",
                        "Manager is not authorized"
                ));

        performManagerApproveRequest(
                "{\"managerId\":9,\"version\":1,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status")
                        .value(403))
                .andExpect(MockMvcResultMatchers.jsonPath("$.error")
                        .value("Forbidden"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("MANAGER_NOT_AUTHORIZED"));
    }

    @Test
    void managerRejectsPaymentRequestAndReturns200() throws Exception {
        when(managerReviewPaymentService.reject(
                5L,
                2L,
                1L,
                "資料不完整"
        )).thenReturn(managerRejectResponse());

        performManagerRejectRequest(
                """
                {
                  "managerId": 2,
                  "version": 1,
                  "comment": "資料不完整"
                }
                """
        )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(5))
                .andExpect(MockMvcResultMatchers.jsonPath("$.action")
                        .value("MANAGER_REJECT"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.approvalStatus")
                        .value("REJECTED_CLOSED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentStatus")
                        .value("UNPAID"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.managerId")
                        .value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.comment")
                        .value("資料不完整"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.actedAt")
                        .exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.version")
                        .value(2));

        verify(managerReviewPaymentService, times(1)).reject(
                5L,
                2L,
                1L,
                "資料不完整"
        );
        verify(managerReviewPaymentService, never())
                .approve(any(), any(), any(), any());
    }

    @Test
    void mapsManagerRejectSnapshotMissingTo409() throws Exception {
        mapsManagerRejectBusinessError(
                "SUPERVISOR_SNAPSHOT_MISSING",
                "Payment request supervisor snapshot is missing"
        );
    }

    @Test
    void mapsManagerRejectNotPendingManagerTo409() throws Exception {
        mapsManagerRejectBusinessError(
                "PAYMENT_REQUEST_NOT_PENDING_MANAGER",
                "Payment request is not PENDING_MANAGER"
        );
    }

    @Test
    void rejectsNullManagerIdWithValidationError() throws Exception {
        performManagerApproveRequest(
                "{\"managerId\":null,\"version\":1,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value("managerId"));

        verify(managerReviewPaymentService, never())
                .approve(any(), any(), any(), any());
    }

    @Test
    void rejectsZeroManagerIdWithValidationError() throws Exception {
        performManagerApproveRequest(
                "{\"managerId\":0,\"version\":1,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"));

        verify(managerReviewPaymentService, never())
                .approve(any(), any(), any(), any());
    }

    @Test
    void rejectsNullVersionWithValidationErrorOnManagerReject() throws Exception {
        performManagerRejectRequest(
                "{\"managerId\":2,\"version\":null,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value("version"));

        verify(managerReviewPaymentService, never())
                .reject(any(), any(), any(), any());
    }

    @Test
    void rejectsNegativeVersionWithValidationError() throws Exception {
        performManagerApproveRequest(
                "{\"managerId\":2,\"version\":-1,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"));

        verify(managerReviewPaymentService, never())
                .approve(any(), any(), any(), any());
    }

    @Test
    void rejectsCommentLongerThan2000Characters() throws Exception {
        String body = "{\"managerId\":2,\"version\":1,\"comment\":\""
                + "x".repeat(2001)
                + "\"}";

        performManagerApproveRequest(body)
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value("comment"));

        verify(managerReviewPaymentService, never())
                .approve(any(), any(), any(), any());
    }

    @Test
    void rejectsInvalidManagerReviewBody() throws Exception {
        performManagerApproveRequest(
                "{\"managerId\":2,\"version\":\"abc\",\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("Request body is missing or invalid"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isEmpty());

        verify(managerReviewPaymentService, never())
                .approve(any(), any(), any(), any());
    }

    @Test
    void rejectsEmptyManagerRejectBody() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post(
                                "/api/payment-requests/5/manager-reject"
                        )
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isEmpty());

        verify(managerReviewPaymentService, never())
                .reject(any(), any(), any(), any());
    }

    @Test
    void mapsManagerApproveNotFoundTo404() throws Exception {
        when(managerReviewPaymentService.approve(3L, 2L, 1L, null))
                .thenThrow(new PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_NOT_FOUND",
                        "Payment request not found"
                ));

        performManagerApproveRequest(
                "{\"managerId\":2,\"version\":1,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAYMENT_REQUEST_NOT_FOUND"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.path")
                        .value("/api/payment-requests/3/manager-approve"));
    }

    @Test
    void mapsManagerRejectNotFoundTo404() throws Exception {
        when(managerReviewPaymentService.reject(5L, 2L, 1L, null))
                .thenThrow(new PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_NOT_FOUND",
                        "Payment request not found"
                ));

        performManagerRejectRequest(
                "{\"managerId\":2,\"version\":1,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAYMENT_REQUEST_NOT_FOUND"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.path")
                        .value("/api/payment-requests/5/manager-reject"));
    }

    @Test
    void hidesManagerRejectUnexpectedExceptionDetails() throws Exception {
        when(managerReviewPaymentService.reject(5L, 2L, 1L, null))
                .thenThrow(new RuntimeException(
                        "sensitive manager approval database details"
                ));

        performManagerRejectRequest(
                "{\"managerId\":2,\"version\":1,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INTERNAL_SERVER_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("An unexpected error occurred"))
                .andExpect(MockMvcResultMatchers.content()
                        .string(not(containsString(
                                "sensitive manager approval database details"
                        ))));
    }

    @Test
    void returnsCompleteManagerReviewErrorFields() throws Exception {
        when(managerReviewPaymentService.approve(3L, 9L, 1L, null))
                .thenThrow(new PaymentDraftBusinessException(
                        "MANAGER_NOT_AUTHORIZED",
                        "Manager is not authorized"
                ));

        performManagerApproveRequest(
                "{\"managerId\":9,\"version\":1,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.jsonPath("$.timestamp").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(403))
                .andExpect(MockMvcResultMatchers.jsonPath("$.error")
                        .value("Forbidden"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.path").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.rejectedValue")
                        .doesNotExist());
    }

    @Test
    void cashierApprovesPaymentRequestAndReturns200() throws Exception {
        when(cashierReviewPaymentService.approve(
                5L,
                3L,
                2L,
                "出納確認完成"
        )).thenReturn(cashierApproveResponse());

        performCashierApproveRequest(
                "{\"cashierId\":3,\"version\":2,"
                        + "\"comment\":\"出納確認完成\"}"
        )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(5))
                .andExpect(MockMvcResultMatchers.jsonPath("$.requestNo")
                        .value("PAY-20260731-000005"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.action")
                        .value("CASHIER_APPROVE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.approvalStatus")
                        .value("APPROVED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentStatus")
                        .value("UNPAID"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.cashierId")
                        .value(3))
                .andExpect(MockMvcResultMatchers.jsonPath("$.cashierName")
                        .value("E2E 測試出納"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.comment")
                        .value("出納確認完成"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.actedAt")
                        .exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.version")
                        .value(3));

        verify(cashierReviewPaymentService, times(1)).approve(
                5L,
                3L,
                2L,
                "出納確認完成"
        );
        verify(cashierReviewPaymentService, never()).reject(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void mapsCashierApproveVersionConflictTo409() throws Exception {
        when(cashierReviewPaymentService.approve(5L, 3L, 2L, null))
                .thenThrow(new PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_VERSION_CONFLICT",
                        "version conflict"
                ));

        performCashierApproveRequest(
                "{\"cashierId\":3,\"version\":2,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAYMENT_REQUEST_VERSION_CONFLICT"));
    }

    @Test
    void mapsCashierApproveNotPendingCashierTo409() throws Exception {
        when(cashierReviewPaymentService.approve(5L, 3L, 2L, null))
                .thenThrow(new PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_NOT_PENDING_CASHIER",
                        "not pending cashier"
                ));

        performCashierApproveRequest(
                "{\"cashierId\":3,\"version\":2,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAYMENT_REQUEST_NOT_PENDING_CASHIER"));
    }

    @Test
    void mapsCashierApproveNotFoundTo404() throws Exception {
        when(cashierReviewPaymentService.approve(5L, 3L, 2L, null))
                .thenThrow(new PaymentDraftBusinessException(
                        "CASHIER_NOT_FOUND",
                        "cashier not found"
                ));

        performCashierApproveRequest(
                "{\"cashierId\":3,\"version\":2,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("CASHIER_NOT_FOUND"));
    }

    @Test
    void mapsCashierApproveInactiveTo409() throws Exception {
        when(cashierReviewPaymentService.approve(5L, 3L, 2L, null))
                .thenThrow(new PaymentDraftBusinessException(
                        "CASHIER_INACTIVE",
                        "cashier inactive"
                ));

        performCashierApproveRequest(
                "{\"cashierId\":3,\"version\":2,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("CASHIER_INACTIVE"));
    }

    @Test
    void cashierRejectsPaymentRequestAndReturns200() throws Exception {
        when(cashierReviewPaymentService.reject(
                7L,
                3L,
                2L,
                "資料仍不完整"
        )).thenReturn(cashierRejectResponse());

        performCashierRejectRequest(
                "{\"cashierId\":3,\"version\":2,"
                        + "\"comment\":\"資料仍不完整\"}"
        )
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(7))
                .andExpect(MockMvcResultMatchers.jsonPath("$.requestNo")
                        .value("PAY-20260731-000007"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.action")
                        .value("CASHIER_REJECT"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.approvalStatus")
                        .value("REJECTED_CLOSED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.paymentStatus")
                        .value("UNPAID"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.cashierId")
                        .value(3))
                .andExpect(MockMvcResultMatchers.jsonPath("$.cashierName")
                        .value("E2E 測試出納"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.comment")
                        .value("資料仍不完整"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.actedAt")
                        .exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.version")
                        .value(3));

        verify(cashierReviewPaymentService, times(1)).reject(
                7L,
                3L,
                2L,
                "資料仍不完整"
        );
        verify(cashierReviewPaymentService, never()).approve(
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void mapsCashierRejectNotPendingCashierTo409() throws Exception {
        when(cashierReviewPaymentService.reject(7L, 3L, 2L, null))
                .thenThrow(new PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_NOT_PENDING_CASHIER",
                        "not pending cashier"
                ));

        performCashierRejectRequest(
                "{\"cashierId\":3,\"version\":2,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAYMENT_REQUEST_NOT_PENDING_CASHIER"));
    }

    @Test
    void mapsCashierRejectNotFoundTo404() throws Exception {
        when(cashierReviewPaymentService.reject(7L, 3L, 2L, null))
                .thenThrow(new PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_NOT_FOUND",
                        "payment request not found"
                ));

        performCashierRejectRequest(
                "{\"cashierId\":3,\"version\":2,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("PAYMENT_REQUEST_NOT_FOUND"));
    }

    @Test
    void rejectsNullCashierIdWithValidationError() throws Exception {
        performCashierApproveRequest(
                "{\"cashierId\":null,\"version\":2,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value(hasItems("cashierId")));

        verifyCashierServiceNotCalled();
    }

    @Test
    void rejectsZeroCashierIdWithValidationError() throws Exception {
        performCashierRejectRequest(
                "{\"cashierId\":0,\"version\":2,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"));

        verifyCashierServiceNotCalled();
    }

    @Test
    void rejectsNullCashierVersionWithValidationError() throws Exception {
        performCashierRejectRequest(
                "{\"cashierId\":3,\"version\":null,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value(hasItems("version")));

        verifyCashierServiceNotCalled();
    }

    @Test
    void rejectsNegativeCashierVersionWithValidationError() throws Exception {
        performCashierApproveRequest(
                "{\"cashierId\":3,\"version\":-1,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"));

        verifyCashierServiceNotCalled();
    }

    @Test
    void rejectsCashierCommentLongerThan2000Characters() throws Exception {
        String comment = "x".repeat(2001);

        performCashierRejectRequest(
                "{\"cashierId\":3,\"version\":2,\"comment\":\""
                        + comment
                        + "\"}"
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors[*].field")
                        .value(hasItems("comment")));

        verifyCashierServiceNotCalled();
    }

    @Test
    void rejectsInvalidCashierReviewBody() throws Exception {
        performCashierApproveRequest(
                "{\"cashierId\":3,\"version\":\"abc\",\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("Request body is missing or invalid"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isEmpty());

        verifyCashierServiceNotCalled();
    }

    @Test
    void rejectsEmptyCashierReviewBody() throws Exception {
        performCashierRejectRequest("")
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isEmpty());

        verifyCashierServiceNotCalled();
    }

    @Test
    void hidesCashierUnexpectedExceptionDetails() throws Exception {
        when(cashierReviewPaymentService.reject(7L, 3L, 2L, null))
                .thenThrow(new RuntimeException(
                        "sensitive cashier approval database details"
                ));

        performCashierRejectRequest(
                "{\"cashierId\":3,\"version\":2,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value("INTERNAL_SERVER_ERROR"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("An unexpected error occurred"))
                .andExpect(MockMvcResultMatchers.content()
                        .string(not(containsString(
                                "sensitive cashier approval database details"
                        ))));
    }

    @Test
    void returnsCompleteCashierReviewErrorFields() throws Exception {
        when(cashierReviewPaymentService.approve(5L, 3L, 2L, null))
                .thenThrow(new PaymentDraftBusinessException(
                        "CASHIER_INACTIVE",
                        "Cashier is inactive"
                ));

        performCashierApproveRequest(
                "{\"cashierId\":3,\"version\":2,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.jsonPath("$.timestamp")
                        .exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status")
                        .value(409))
                .andExpect(MockMvcResultMatchers.jsonPath("$.error")
                        .value("Conflict"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.path")
                        .value("/api/payment-requests/5/cashier-approve"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.fieldErrors")
                        .isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.rejectedValue")
                        .doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions performRecordPaymentRequest(
            String body
    ) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(
                        "/api/payment-requests/5/record-payment"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performValidRecordPaymentRequest()
            throws Exception {
        return performRecordPaymentRequest("""
                {
                  "paidById": 6,
                  "version": 3,
                  "paidAt": "2026-07-31T13:30:00+08:00",
                  "paymentMethod": "BANK_TRANSFER",
                  "paymentReference": "E2E-TRANSFER-001",
                  "paymentNote": "已完成銀行轉帳"
                }
                """);
    }

    private void stubRecordPaymentBusinessError(
            String code,
            String message
    ) {
        when(recordPaymentService.record(
                5L,
                6L,
                3L,
                PAYMENT_PAID_AT,
                PaymentMethod.BANK_TRANSFER,
                "E2E-TRANSFER-001",
                "已完成銀行轉帳"
        )).thenThrow(new PaymentDraftBusinessException(code, message));
    }

    private RecordPaymentResponse recordPaymentResponse() {
        return new RecordPaymentResponse(
                5L,
                "PAY-20260731-000005",
                ApprovalAction.PAYMENT_RECORDED,
                ApprovalStatus.APPROVED,
                PaymentStatus.PAID,
                6L,
                "E2E 測試出納",
                RESPONSE_PAID_AT,
                PaymentMethod.BANK_TRANSFER,
                "E2E-TRANSFER-001",
                "已完成銀行轉帳",
                OffsetDateTime.parse("2026-07-31T14:00:00+08:00"),
                4L
        );
    }

    private org.springframework.test.web.servlet.ResultActions performValidRequest()
            throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/payment-requests/drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())));
    }

    private CreatePaymentDraftRequest validRequest() {
        return new CreatePaymentDraftRequest(
                1L,
                2L,
                3L,
                RequestCategory.EXPENSE,
                "test reason",
                List.of(new CreatePaymentDraftItemRequest(
                        10L,
                        null,
                        "description",
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

    private CreatePaymentDraftResponse successResponse() {
        return new CreatePaymentDraftResponse(
                100L,
                "PAY-20260730-000001",
                1L,
                4L,
                2L,
                3L,
                RequestCategory.EXPENSE,
                "test reason",
                ApprovalStatus.DRAFT,
                PaymentStatus.UNPAID,
                new BigDecimal("100.00"),
                List.of(new CreatePaymentDraftItemResponse(
                        200L,
                        10L,
                        "MANUAL",
                        "Manual",
                        CalculationType.MANUAL,
                        null,
                        null,
                        null,
                        "description",
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.ONE,
                        new BigDecimal("100.00"),
                        java.util.Map.of(),
                        1
                )),
                OffsetDateTime.parse("2026-07-30T10:00:00+08:00"),
                0L
        );
    }

    private org.springframework.test.web.servlet.ResultActions performSubmitRequest(
            String body
    ) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(
                        "/api/payment-requests/1/submit"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void mapsSubmitBusinessError(String code, String message)
            throws Exception {
        when(submitPaymentDraftService.submit(1L, 0L))
                .thenThrow(new PaymentDraftBusinessException(code, message));

        performSubmitRequest("{\"version\":0}")
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value(code));
    }

    private SubmitPaymentDraftResponse submitResponse() {
        return new SubmitPaymentDraftResponse(
                1L,
                "PAY-20260731-000001",
                ApprovalStatus.PENDING_MANAGER,
                PaymentStatus.UNPAID,
                2L,
                "測試主管",
                OffsetDateTime.parse("2026-07-31T10:30:00+08:00"),
                1L
        );
    }

    private org.springframework.test.web.servlet.ResultActions performManagerApproveRequest(
            String body
    ) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(
                        "/api/payment-requests/3/manager-approve"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performManagerRejectRequest(
            String body
    ) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(
                        "/api/payment-requests/5/manager-reject"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performCashierApproveRequest(
            String body
    ) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(
                        "/api/payment-requests/5/cashier-approve"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performCashierRejectRequest(
            String body
    ) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(
                        "/api/payment-requests/7/cashier-reject"
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void verifyCashierServiceNotCalled() {
        verify(cashierReviewPaymentService, never()).approve(
                any(),
                any(),
                any(),
                any()
        );
        verify(cashierReviewPaymentService, never()).reject(
                any(),
                any(),
                any(),
                any()
        );
    }

    private void mapsManagerApproveBusinessError(
            String code,
            String message
    ) throws Exception {
        when(managerReviewPaymentService.approve(3L, 2L, 1L, null))
                .thenThrow(new PaymentDraftBusinessException(code, message));

        performManagerApproveRequest(
                "{\"managerId\":2,\"version\":1,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value(code));
    }

    private void mapsManagerRejectBusinessError(
            String code,
            String message
    ) throws Exception {
        when(managerReviewPaymentService.reject(5L, 2L, 1L, null))
                .thenThrow(new PaymentDraftBusinessException(code, message));

        performManagerRejectRequest(
                "{\"managerId\":2,\"version\":1,\"comment\":null}"
        )
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code")
                        .value(code));
    }

    private ManagerReviewPaymentResponse managerApproveResponse() {
        return new ManagerReviewPaymentResponse(
                3L,
                "PAY-20260731-000003",
                ApprovalAction.MANAGER_APPROVE,
                ApprovalStatus.PENDING_CASHIER,
                PaymentStatus.UNPAID,
                2L,
                "E2E 測試主管",
                "確認無誤",
                OffsetDateTime.parse("2026-07-31T11:30:00+08:00"),
                2L
        );
    }

    private ManagerReviewPaymentResponse managerRejectResponse() {
        return new ManagerReviewPaymentResponse(
                5L,
                "PAY-20260731-000005",
                ApprovalAction.MANAGER_REJECT,
                ApprovalStatus.REJECTED_CLOSED,
                PaymentStatus.UNPAID,
                2L,
                "E2E 測試主管",
                "資料不完整",
                OffsetDateTime.parse("2026-07-31T11:35:00+08:00"),
                2L
        );
    }

    private CashierReviewPaymentResponse cashierApproveResponse() {
        return new CashierReviewPaymentResponse(
                5L,
                "PAY-20260731-000005",
                ApprovalAction.CASHIER_APPROVE,
                ApprovalStatus.APPROVED,
                PaymentStatus.UNPAID,
                3L,
                "E2E 測試出納",
                "出納確認完成",
                OffsetDateTime.parse("2026-07-31T13:30:00+08:00"),
                3L
        );
    }

    private CashierReviewPaymentResponse cashierRejectResponse() {
        return new CashierReviewPaymentResponse(
                7L,
                "PAY-20260731-000007",
                ApprovalAction.CASHIER_REJECT,
                ApprovalStatus.REJECTED_CLOSED,
                PaymentStatus.UNPAID,
                3L,
                "E2E 測試出納",
                "資料仍不完整",
                OffsetDateTime.parse("2026-07-31T13:35:00+08:00"),
                3L
        );
    }

    private PaymentRequestDetailResponse detailResponse() {
        return new PaymentRequestDetailResponse(
                5L,
                "PAY-20260731-000005",
                new PaymentRequestDetailResponse.UserSummary(1L, "applicant", "申請人"),
                new PaymentRequestDetailResponse.DepartmentSummary(7L, "FIN", "財務部"),
                new PaymentRequestDetailResponse.UserSummary(2L, "supervisor", "主管"),
                new PaymentRequestDetailResponse.CompanySummary(8L, "COMPANY", "公司"),
                new PaymentRequestDetailResponse.CustomerSummary(9L, "CUSTOMER", "客戶"),
                RequestCategory.EXPENSE,
                "測試請款",
                ApprovalStatus.APPROVED,
                PaymentStatus.PAID,
                new BigDecimal("456.78"),
                OffsetDateTime.parse("2026-07-31T10:00:00+08:00"),
                OffsetDateTime.parse("2026-07-31T11:00:00+08:00"),
                new PaymentRequestDetailResponse.UserSummary(2L, "supervisor", "主管"),
                null,
                null,
                OffsetDateTime.parse("2026-07-31T13:30:00+08:00"),
                new PaymentRequestDetailResponse.UserSummary(3L, "cashier", "出納"),
                PaymentMethod.BANK_TRANSFER,
                "BANK-001",
                "已付款",
                List.of(new PaymentRequestDetailResponse.ItemDetail(
                        20L,
                        30L,
                        "TRAVEL",
                        "交通費",
                        CalculationType.TRAVEL,
                        null,
                        null,
                        null,
                        "計程車",
                        null,
                        null,
                        new BigDecimal("1.00"),
                        new BigDecimal("123.45"),
                        BigDecimal.ONE,
                        new BigDecimal("123.45"),
                        java.util.Map.of("city", "Taipei"),
                        1
                )),
                List.of(new PaymentRequestDetailResponse.ApprovalHistoryDetail(
                        3L,
                        new PaymentRequestDetailResponse.UserSummary(3L, "cashier", "出納"),
                        ApprovalAction.PAYMENT_RECORDED,
                        ApprovalStatus.APPROVED,
                        ApprovalStatus.APPROVED,
                        PaymentStatus.UNPAID,
                        PaymentStatus.PAID,
                        "完成付款",
                        OffsetDateTime.parse("2026-07-31T13:30:00+08:00")
                )),
                List.of(new PaymentRequestDetailResponse.AttachmentDetail(
                        4L,
                        AttachmentType.RECEIPT,
                        "receipt.pdf",
                        "application/pdf",
                        1024L,
                        OffsetDateTime.parse("2026-07-31T10:05:00+08:00")
                )),
                OffsetDateTime.parse("2026-07-31T09:00:00+08:00"),
                OffsetDateTime.parse("2026-07-31T13:30:00+08:00"),
                4L
        );
    }

    private PaymentRequestDetailResponse draftDetailResponse() {
        return new PaymentRequestDetailResponse(
                10L,
                "PAY-20260731-000010",
                new PaymentRequestDetailResponse.UserSummary(1L, "applicant", "申請人"),
                new PaymentRequestDetailResponse.DepartmentSummary(7L, "FIN", "財務部"),
                null,
                new PaymentRequestDetailResponse.CompanySummary(8L, "COMPANY", "公司"),
                new PaymentRequestDetailResponse.CustomerSummary(9L, "CUSTOMER", "客戶"),
                RequestCategory.EXPENSE,
                "草稿",
                ApprovalStatus.DRAFT,
                PaymentStatus.UNPAID,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                OffsetDateTime.parse("2026-07-31T09:00:00+08:00"),
                OffsetDateTime.parse("2026-07-31T09:00:00+08:00"),
                0L
        );
    }

    private PaymentRequestPageResponse listPageResponse() {
        return new PaymentRequestPageResponse(
                List.of(new PaymentRequestListItemResponse(
                        5L,
                        "PAY-20260731-000005",
                        1L,
                        "E2E 驗收申請人",
                        1L,
                        "E2E 驗收部門",
                        2L,
                        "E2E 測試主管",
                        1L,
                        "E2E 驗收公司",
                        1L,
                        "E2E 驗收客戶",
                        RequestCategory.EXPENSE,
                        ApprovalStatus.APPROVED,
                        PaymentStatus.PAID,
                        new BigDecimal("1620.50"),
                        OffsetDateTime.parse("2026-07-31T10:00:00+08:00"),
                        OffsetDateTime.parse("2026-07-31T12:30:00+08:00"),
                        OffsetDateTime.parse("2026-07-31T15:00:00+08:00"),
                        OffsetDateTime.parse("2026-07-31T09:00:00+08:00"),
                        OffsetDateTime.parse("2026-07-31T15:00:00+08:00"),
                        4L
                )),
                0,
                20,
                1,
                1,
                true,
                true
        );
    }

    private void verifyListServiceNotCalled() {
        verify(listPaymentRequestsService, never()).list(
                any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()
        );
    }
}
