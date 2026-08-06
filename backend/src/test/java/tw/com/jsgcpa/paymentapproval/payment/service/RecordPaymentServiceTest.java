package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import tw.com.jsgcpa.paymentapproval.approval.entity.ApprovalHistory;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalAction;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.approval.repository.ApprovalHistoryRepository;
import tw.com.jsgcpa.paymentapproval.attachment.storage.AttachmentStorageService;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.RecordPaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.RecordPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@ExtendWith(MockitoExtension.class)
class RecordPaymentServiceTest {

    private static final OffsetDateTime PAID_AT =
            OffsetDateTime.parse("2026-08-04T10:00:00+08:00");
    private static final OffsetDateTime RECORDED_AT =
            OffsetDateTime.parse("2026-08-04T14:00:00+08:00");

    @Mock private PaymentRequestRepository paymentRequestRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ApprovalHistoryRepository approvalHistoryRepository;
    @Mock private PaymentMaintenanceService paymentMaintenanceService;
    @Mock private AttachmentStorageService storage;
    @Mock private TransactionRollbackCleanupRegistrar cleanupRegistrar;

    private RecordPaymentService service;
    private MultipartFile file;
    private AppUser operator;

    @BeforeEach
    void setUp() {
        service = new RecordPaymentService(
                paymentRequestRepository,
                appUserRepository,
                approvalHistoryRepository,
                paymentMaintenanceService,
                storage,
                cleanupRegistrar,
                Clock.fixed(
                        Instant.parse("2026-08-04T06:00:00Z"),
                        ZoneId.of("Asia/Taipei")
                )
        );
        file = new MockMultipartFile(
                "file", "payment-proof.pdf", "application/pdf", "%PDF".getBytes()
        );
        operator = user(9L, "Payment Operator");
    }

    @Test
    void recordsPaymentWithProofAndAuthenticatedOperator() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubValidOperation(paymentRequest);

        RecordPaymentResponse response = service.recordPayment(
                1L,
                paymentRequest("  E2E-001  ", "  payment proof  "),
                file,
                9L
        );

        assertEquals(PaymentStatus.PAID, paymentRequest.getPaymentStatus());
        assertSame(operator, paymentRequest.getPaidBy());
        assertEquals("E2E-001", paymentRequest.getPaymentReference());
        assertEquals("payment proof", paymentRequest.getPaymentNote());
        assertEquals(ApprovalAction.PAYMENT_RECORDED, response.action());
        assertEquals(RECORDED_AT, response.recordedAt());
        assertEquals(4L, response.version());
        verify(paymentMaintenanceService).persistProofFiles(
                eq(paymentRequest), any(), eq(operator)
        );
    }

    @Test
    void createsCompletePaymentRecordedHistory() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubValidOperation(paymentRequest);

        service.recordPayment(1L, paymentRequest(null, "  proof note  "), file, 9L);

        ApprovalHistory history = capturedHistory();
        assertSame(operator, history.getActor());
        assertEquals(ApprovalAction.PAYMENT_RECORDED, history.getAction());
        assertEquals(ApprovalStatus.APPROVED, history.getFromApprovalStatus());
        assertEquals(ApprovalStatus.APPROVED, history.getToApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, history.getFromPaymentStatus());
        assertEquals(PaymentStatus.PAID, history.getToPaymentStatus());
        assertEquals("proof note", history.getComment());
        assertEquals(RECORDED_AT, history.getActedAt());

        verify(paymentMaintenanceService).persistProofFiles(
                eq(paymentRequest),
                any(),
                eq(operator)
        );
    }

    @Test
    void flushesPaymentAndHistoryInOrder() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubValidOperation(paymentRequest);

        service.recordPayment(1L, paymentRequest(), file, 9L);

        InOrder order = inOrder(
                paymentMaintenanceService,
                paymentRequestRepository,
                approvalHistoryRepository
        );
        order.verify(paymentMaintenanceService).persistProofFiles(
                eq(paymentRequest), any(), eq(operator)
        );
        order.verify(paymentRequestRepository).saveAndFlush(paymentRequest);
        order.verify(approvalHistoryRepository).saveAndFlush(any(ApprovalHistory.class));
    }

    @Test
    void preservesApprovalDataAndPaymentRequestStatus() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        OffsetDateTime approvedAt = paymentRequest.getApprovedAt();
        AppUser approvedBy = paymentRequest.getApprovedBy();
        OffsetDateTime submittedAt = paymentRequest.getSubmittedAt();
        BigDecimal totalAmount = paymentRequest.getTotalAmount();
        stubValidOperation(paymentRequest);

        service.recordPayment(1L, paymentRequest(), file, 9L);

        assertEquals(ApprovalStatus.APPROVED, paymentRequest.getApprovalStatus());
        assertEquals(approvedAt, paymentRequest.getApprovedAt());
        assertSame(approvedBy, paymentRequest.getApprovedBy());
        assertEquals(submittedAt, paymentRequest.getSubmittedAt());
        assertEquals(totalAmount, paymentRequest.getTotalAmount());
    }

    @Test
    void rejectsLegacyJsonAdapter() {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.recordPayment(1L, 9L, paymentRequest())
        );
        assertEquals("PAYMENT_PROOF_REQUIRED", exception.getCode());
        verifyNoWrites();
    }

    @Test
    void rejectsMissingPaymentRequestBeforeOperatorAndStorage() {
        when(paymentRequestRepository.findById(1L)).thenReturn(Optional.empty());

        assertCode("PAYMENT_REQUEST_NOT_FOUND", () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));

        verify(appUserRepository, never()).findById(any());
        verify(paymentMaintenanceService, never()).persistProofFiles(any(), any(), any());
        verifyNoWrites();
    }

    @Test
    void rejectsVersionConflictBeforeBinaryWrite() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        when(paymentRequestRepository.findById(1L)).thenReturn(Optional.of(paymentRequest));

        assertCode("PAYMENT_REQUEST_VERSION_CONFLICT", () ->
                service.recordPayment(1L, new RecordPaymentRequest(
                        2L, PAID_AT, null, null, null
                ), file, 9L));

        verify(paymentMaintenanceService, never()).persistProofFiles(any(), any(), any());
        verify(appUserRepository, never()).findById(any());
    }

    @Test
    void rejectsMissingProofBeforePersist() {
        assertCode("PAYMENT_PROOF_REQUIRED", () ->
                service.recordPayment(1L, paymentRequest(), List.of(), 9L));

        verifyNoInteractions(paymentRequestRepository, paymentMaintenanceService);
    }

    @Test
    void rejectsInvalidPaymentStatesWithoutWritingProof() {
        for (ApprovalStatus status : new ApprovalStatus[]{
                ApprovalStatus.DRAFT,
                ApprovalStatus.PENDING_MANAGER,
                ApprovalStatus.PENDING_CASHIER,
                ApprovalStatus.REJECTED_CLOSED
        }) {
            PaymentRequest paymentRequest = approvedUnpaidRequest();
            paymentRequest.setApprovalStatus(status);
            when(paymentRequestRepository.findById(1L)).thenReturn(Optional.of(paymentRequest));
            assertCode("PAYMENT_REQUEST_NOT_APPROVED", () ->
                    service.recordPayment(1L, paymentRequest(), file, 9L));
        }
        verify(paymentMaintenanceService, never()).persistProofFiles(any(), any(), any());
    }

    @Test
    void rejectsAlreadyPaidWithoutOverwritingExistingData() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        AppUser original = user(8L, "Original payer");
        paymentRequest.setPaymentStatus(PaymentStatus.PAID);
        paymentRequest.setPaidBy(original);
        paymentRequest.setPaidAt(PAID_AT);
        paymentRequest.setPaymentMethod(PaymentMethod.CASH);
        paymentRequest.setPaymentReference("ORIGINAL");
        paymentRequest.setPaymentNote("Original");
        when(paymentRequestRepository.findById(1L)).thenReturn(Optional.of(paymentRequest));

        assertCode("PAYMENT_REQUEST_ALREADY_PAID", () ->
                service.recordPayment(1L, paymentRequest("NEW", "NEW"), file, 9L));

        assertSame(original, paymentRequest.getPaidBy());
        assertEquals("ORIGINAL", paymentRequest.getPaymentReference());
        verify(paymentMaintenanceService, never()).persistProofFiles(any(), any(), any());
    }

    @Test
    void rejectsMissingOrInactiveAuthenticatedOperator() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubPaymentAndDuplicateCheck(paymentRequest);
        when(appUserRepository.findById(9L)).thenReturn(Optional.empty());
        assertCode("PAID_BY_NOT_FOUND", () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));

        operator.setActive(false);
        when(appUserRepository.findById(9L)).thenReturn(Optional.of(operator));
        assertCode("PAID_BY_INACTIVE", () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));
        verify(paymentMaintenanceService, never()).persistProofFiles(any(), any(), any());
    }

    @Test
    void propagatesProofPersistFailureWithoutPaymentSave() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubPaymentAndDuplicateCheck(paymentRequest);
        RuntimeException failure = new RuntimeException("proof persist failure");
        org.mockito.Mockito.doThrow(failure).when(paymentMaintenanceService)
                .persistProofFiles(eq(paymentRequest), any(), eq(operator));

        assertSame(failure, assertThrows(RuntimeException.class, () ->
                service.recordPayment(1L, paymentRequest(), file, 9L)));

        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void cleansUpOnPaymentOptimisticLockFailureAfterProofPersist() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubPaymentAndDuplicateCheck(paymentRequest);
        org.mockito.Mockito.doNothing().when(paymentMaintenanceService)
                .persistProofFiles(eq(paymentRequest), any(), eq(operator));
        when(paymentRequestRepository.saveAndFlush(any(PaymentRequest.class)))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        assertCode("PAYMENT_REQUEST_VERSION_CONFLICT", () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));

        verify(approvalHistoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void propagatesHistoryFailureAfterProofPersist() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubPaymentAndDuplicateCheck(paymentRequest);
        org.mockito.Mockito.doNothing().when(paymentMaintenanceService)
                .persistProofFiles(eq(paymentRequest), any(), eq(operator));
        when(paymentRequestRepository.saveAndFlush(paymentRequest)).thenAnswer(invocation -> {
            setField(paymentRequest, "version", 4L);
            return paymentRequest;
        });
        when(approvalHistoryRepository.saveAndFlush(any(ApprovalHistory.class)))
                .thenThrow(new RuntimeException("history failure"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));

        assertEquals("history failure", exception.getMessage());
    }

    @Test
    void keepsPaymentMethodAndPaidAtFromRequest() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubValidOperation(paymentRequest);
        RecordPaymentRequest request = new RecordPaymentRequest(
                3L,
                OffsetDateTime.parse("2026-08-05T09:30:00+08:00"),
                PaymentMethod.CASH,
                null,
                null
        );

        service.recordPayment(1L, request, file, 9L);

        assertEquals(PaymentMethod.CASH, paymentRequest.getPaymentMethod());
        assertEquals(request.paidAt(), paymentRequest.getPaidAt());
        assertEquals(null, paymentRequest.getPaymentReference());
        assertEquals(null, paymentRequest.getPaymentNote());
    }

    @Test
    void validatesInvalidIdentifiersAndRequest() {
        assertCode("INVALID_PAYMENT_REQUEST_ID", () ->
                service.recordPayment(0L, paymentRequest(), file, 9L));
        assertCode("INVALID_PAID_BY_ID", () ->
                service.recordPayment(1L, paymentRequest(), file, 0L));
        assertCode("INVALID_PAYMENT_REQUEST_VERSION", () ->
                service.recordPayment(1L, new RecordPaymentRequest(
                        -1L, PAID_AT, null, null, null
                ), file, 9L));
        assertCode("INVALID_PAYMENT_DATE", () ->
                service.recordPayment(1L, new RecordPaymentRequest(
                        3L, null, null, null, null
                ), file, 9L));
        verifyNoInteractions(paymentRequestRepository);
    }

    private void stubValidOperation(PaymentRequest request) {
        stubBeforeHistorySave(request);
    }

    private void stubBeforeHistorySave(PaymentRequest request) {
        stubPaymentAndDuplicateCheck(request);
        org.mockito.Mockito.doNothing().when(paymentMaintenanceService)
                .persistProofFiles(eq(request), any(), eq(operator));
        when(paymentRequestRepository.saveAndFlush(request)).thenAnswer(invocation -> {
            setField(request, "version", 4L);
            return request;
        });
        when(approvalHistoryRepository.saveAndFlush(any(ApprovalHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubPaymentAndDuplicateCheck(PaymentRequest request) {
        when(paymentRequestRepository.findById(1L)).thenReturn(Optional.of(request));
        when(appUserRepository.findById(9L)).thenReturn(Optional.of(operator));
    }

    private ApprovalHistory capturedHistory() {
        ArgumentCaptor<ApprovalHistory> captor =
                ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(approvalHistoryRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private void verifyNoWrites() {
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(paymentMaintenanceService, never()).persistProofFiles(any(), any(), any());
        verify(approvalHistoryRepository, never()).saveAndFlush(any());
    }

    private PaymentRequest approvedUnpaidRequest() {
        PaymentRequest request = new PaymentRequest();
        setField(request, "id", 1L);
        setField(request, "version", 3L);
        request.setRequestNo("PAY-20260804-000001");
        request.setApprovalStatus(ApprovalStatus.APPROVED);
        request.setPaymentStatus(PaymentStatus.UNPAID);
        request.setTotalAmount(new BigDecimal("100.00"));
        request.setSubmittedAt(PAID_AT.minusDays(1));
        request.setApprovedAt(PAID_AT.minusHours(1));
        request.setApprovedBy(user(2L, "Manager"));
        request.setSupervisorSnapshot(user(3L, "Supervisor"));
        return request;
    }

    private RecordPaymentRequest paymentRequest() {
        return paymentRequest("E2E-001", "payment proof");
    }

    private RecordPaymentRequest paymentRequest(String reference, String note) {
        return new RecordPaymentRequest(
                3L,
                PAID_AT,
                PaymentMethod.BANK_TRANSFER,
                reference,
                note
        );
    }

    private AppUser user(Long id, String name) {
        AppUser user = new AppUser();
        setField(user, "id", id);
        user.setDisplayName(name);
        user.setActive(true);
        return user;
    }

    private void setField(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }

    private void assertCode(String code, org.junit.jupiter.api.function.Executable executable) {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                executable
        );
        assertEquals(code, exception.getCode());
    }
}
