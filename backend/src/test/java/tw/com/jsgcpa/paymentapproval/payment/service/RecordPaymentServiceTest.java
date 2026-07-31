package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import tw.com.jsgcpa.paymentapproval.approval.entity.ApprovalHistory;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalAction;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.approval.repository.ApprovalHistoryRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.RecordPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@ExtendWith(MockitoExtension.class)
class RecordPaymentServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");
    private static final Instant RECORDED_INSTANT =
            Instant.parse("2026-07-31T06:00:00Z");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(RECORDED_INSTANT, BUSINESS_ZONE);
    private static final OffsetDateTime PAID_AT = OffsetDateTime.parse(
            "2026-07-30T15:00:00+08:00"
    );
    private static final OffsetDateTime RECORDED_AT = OffsetDateTime.parse(
            "2026-07-31T14:00:00+08:00"
    );

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private ApprovalHistoryRepository approvalHistoryRepository;

    private RecordPaymentService service;

    @BeforeEach
    void setUp() {
        service = new RecordPaymentService(
                paymentRequestRepository,
                appUserRepository,
                approvalHistoryRepository,
                FIXED_CLOCK
        );
    }

    @Test
    void recordsPaymentAndPreservesApprovalStatus() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        AppUser paidBy = paidBy();
        stubValidPayment(paymentRequest, paidBy);
        stubSaveAndFlushVersion(4L);

        RecordPaymentResponse response = service.record(
                1L,
                9L,
                3L,
                PAID_AT,
                PaymentMethod.BANK_TRANSFER,
                "BANK-20260730-001",
                "已完成匯款"
        );

        assertEquals(ApprovalStatus.APPROVED,
                paymentRequest.getApprovalStatus());
        assertEquals(PaymentStatus.PAID, paymentRequest.getPaymentStatus());
        assertEquals(PAID_AT, paymentRequest.getPaidAt());
        assertSame(paidBy, paymentRequest.getPaidBy());
        assertEquals(PaymentMethod.BANK_TRANSFER,
                paymentRequest.getPaymentMethod());
        assertEquals("BANK-20260730-001",
                paymentRequest.getPaymentReference());
        assertEquals("已完成匯款", paymentRequest.getPaymentNote());
        assertEquals(ApprovalAction.PAYMENT_RECORDED, response.action());
        assertEquals(ApprovalStatus.APPROVED, response.approvalStatus());
        assertEquals(PaymentStatus.PAID, response.paymentStatus());
        assertEquals(9L, response.paidById());
        assertEquals("付款人 A", response.paidByName());
        assertEquals(PAID_AT, response.paidAt());
        assertEquals(PaymentMethod.BANK_TRANSFER, response.paymentMethod());
        assertEquals("BANK-20260730-001", response.paymentReference());
        assertEquals("已完成匯款", response.paymentNote());
        assertEquals(RECORDED_AT, response.recordedAt());
        assertEquals(4L, response.version());
    }

    @Test
    void keepsPaidAtSeparateFromRecordedAt() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        AppUser paidBy = paidBy();
        stubValidPayment(paymentRequest, paidBy);
        stubSaveAndFlushVersion(4L);

        RecordPaymentResponse response = service.record(
                1L, 9L, 3L, PAID_AT, null, null, null
        );

        ApprovalHistory history = capturedHistory();
        assertEquals(PAID_AT, paymentRequest.getPaidAt());
        assertEquals(PAID_AT, response.paidAt());
        assertEquals(RECORDED_AT, history.getActedAt());
        assertEquals(RECORDED_AT, response.recordedAt());
    }

    @Test
    void createsCompletePaymentRecordedHistory() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        AppUser paidBy = paidBy();
        stubValidPayment(paymentRequest, paidBy);
        stubSaveAndFlushVersion(4L);

        service.record(1L, 9L, 3L, PAID_AT, PaymentMethod.CASH,
                "CASH-001", "付款備註");

        ApprovalHistory history = capturedHistory();
        assertSame(paymentRequest, history.getPaymentRequest());
        assertSame(paidBy, history.getActor());
        assertEquals(ApprovalAction.PAYMENT_RECORDED, history.getAction());
        assertEquals(ApprovalStatus.APPROVED,
                history.getFromApprovalStatus());
        assertEquals(ApprovalStatus.APPROVED,
                history.getToApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, history.getFromPaymentStatus());
        assertEquals(PaymentStatus.PAID, history.getToPaymentStatus());
        assertEquals("付款備註", history.getComment());
        assertEquals(RECORDED_AT, history.getActedAt());
    }

    @Test
    void allowsOptionalPaymentFieldsToBeNull() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        AppUser paidBy = paidBy();
        stubValidPayment(paymentRequest, paidBy);
        stubSaveAndFlushVersion(4L);

        service.record(1L, 9L, 3L, PAID_AT, null, null, null);

        assertEquals(PaymentStatus.PAID, paymentRequest.getPaymentStatus());
        assertEquals(PAID_AT, paymentRequest.getPaidAt());
        assertSame(paidBy, paymentRequest.getPaidBy());
        assertEquals(null, paymentRequest.getPaymentMethod());
        assertEquals(null, paymentRequest.getPaymentReference());
        assertEquals(null, paymentRequest.getPaymentNote());
    }

    @Test
    void preservesApprovalDataAmountAndSubmittedAt() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        AppUser paidBy = paidBy();
        OffsetDateTime approvedAt = paymentRequest.getApprovedAt();
        AppUser approvedBy = paymentRequest.getApprovedBy();
        AppUser supervisorSnapshot = paymentRequest.getSupervisorSnapshot();
        OffsetDateTime submittedAt = paymentRequest.getSubmittedAt();
        BigDecimal totalAmount = paymentRequest.getTotalAmount();
        stubValidPayment(paymentRequest, paidBy);
        stubSaveAndFlushVersion(4L);

        service.record(1L, 9L, 3L, PAID_AT, null, null, null);

        assertEquals(approvedAt, paymentRequest.getApprovedAt());
        assertSame(approvedBy, paymentRequest.getApprovedBy());
        assertSame(supervisorSnapshot,
                paymentRequest.getSupervisorSnapshot());
        assertEquals(submittedAt, paymentRequest.getSubmittedAt());
        assertEquals(totalAmount, paymentRequest.getTotalAmount());
    }

    @Test
    void rejectsMissingPaymentRequestBeforeLookingUpPaidBy() {
        when(paymentRequestRepository.findById(1L)).thenReturn(Optional.empty());

        assertCode("PAYMENT_REQUEST_NOT_FOUND",
                () -> service.record(1L, 9L, 3L, PAID_AT, null, null, null));

        verify(appUserRepository, never()).findById(any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsVersionConflictBeforeLookingUpPaidBy() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode("PAYMENT_REQUEST_VERSION_CONFLICT",
                () -> service.record(1L, 9L, 2L, PAID_AT, null, null, null));

        verify(appUserRepository, never()).findById(any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalStatus.class, names = {
            "DRAFT", "PENDING_MANAGER", "PENDING_CASHIER", "REJECTED_CLOSED"
    })
    void rejectsStatusesOtherThanApproved(ApprovalStatus status) {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        paymentRequest.setApprovalStatus(status);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode("PAYMENT_REQUEST_NOT_APPROVED",
                () -> service.record(1L, 9L, 3L, PAID_AT, null, null, null));

        verify(appUserRepository, never()).findById(any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsAlreadyPaidWithoutOverwritingExistingPaymentData() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        AppUser existingPaidBy = new AppUser();
        setField(existingPaidBy, "id", 8L);
        existingPaidBy.setDisplayName("原付款人");
        paymentRequest.setPaymentStatus(PaymentStatus.PAID);
        paymentRequest.setPaidAt(OffsetDateTime.parse(
                "2026-07-29T10:00:00+08:00"));
        paymentRequest.setPaidBy(existingPaidBy);
        paymentRequest.setPaymentMethod(PaymentMethod.CASH);
        paymentRequest.setPaymentReference("ORIGINAL-001");
        paymentRequest.setPaymentNote("原付款資料");
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode("PAYMENT_REQUEST_ALREADY_PAID",
                () -> service.record(1L, 9L, 3L, PAID_AT,
                        PaymentMethod.BANK_TRANSFER, "NEW", "NEW"));

        assertEquals(8L, paymentRequest.getPaidBy().getId());
        assertEquals("ORIGINAL-001", paymentRequest.getPaymentReference());
        assertEquals("原付款資料", paymentRequest.getPaymentNote());
        verify(appUserRepository, never()).findById(any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsMissingPaidBy() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(appUserRepository.findById(9L)).thenReturn(Optional.empty());

        assertCode("PAID_BY_NOT_FOUND",
                () -> service.record(1L, 9L, 3L, PAID_AT, null, null, null));

        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsInactivePaidBy() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        AppUser paidBy = paidBy();
        paidBy.setActive(false);
        stubPaymentRequest(paymentRequest);
        when(appUserRepository.findById(9L)).thenReturn(Optional.of(paidBy));

        assertCode("PAID_BY_INACTIVE",
                () -> service.record(1L, 9L, 3L, PAID_AT, null, null, null));

        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void convertsOptimisticLockingFailureAndDoesNotCreateHistory() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        AppUser paidBy = paidBy();
        stubValidPayment(paymentRequest, paidBy);
        when(paymentRequestRepository.saveAndFlush(paymentRequest))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        PaymentRequest.class, 1L));

        assertCode("PAYMENT_REQUEST_VERSION_CONFLICT",
                () -> service.record(1L, 9L, 3L, PAID_AT, null, null, null));

        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void propagatesApprovalHistorySaveFailure() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        AppUser paidBy = paidBy();
        stubValidPayment(paymentRequest, paidBy);
        stubSaveAndFlushVersion(4L);
        when(approvalHistoryRepository.save(any(ApprovalHistory.class)))
                .thenThrow(new RuntimeException("history save failed"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.record(1L, 9L, 3L, PAID_AT,
                        PaymentMethod.CASH, null, "備註"));

        assertEquals("history save failed", exception.getMessage());
        verify(paymentRequestRepository, times(1))
                .saveAndFlush(paymentRequest);
        verify(approvalHistoryRepository, times(1)).save(any());
    }

    @Test
    void validatesSaveBeforeHistoryOrder() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        AppUser paidBy = paidBy();
        stubValidPayment(paymentRequest, paidBy);
        stubSaveAndFlushVersion(4L);

        service.record(1L, 9L, 3L, PAID_AT, null, null, null);

        InOrder order = inOrder(
                paymentRequestRepository,
                approvalHistoryRepository
        );
        order.verify(paymentRequestRepository).saveAndFlush(paymentRequest);
        order.verify(approvalHistoryRepository).save(any(ApprovalHistory.class));
    }

    @Test
    void rejectsInvalidPaymentRequestIds() {
        assertCode("INVALID_PAYMENT_REQUEST_ID",
                () -> service.record(null, 9L, 3L, PAID_AT, null, null, null));
        assertCode("INVALID_PAYMENT_REQUEST_ID",
                () -> service.record(0L, 9L, 3L, PAID_AT, null, null, null));
        assertCode("INVALID_PAYMENT_REQUEST_ID",
                () -> service.record(-1L, 9L, 3L, PAID_AT, null, null, null));

        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void rejectsInvalidPaidByIds() {
        assertCode("INVALID_PAID_BY_ID",
                () -> service.record(1L, null, 3L, PAID_AT, null, null, null));
        assertCode("INVALID_PAID_BY_ID",
                () -> service.record(1L, 0L, 3L, PAID_AT, null, null, null));
        assertCode("INVALID_PAID_BY_ID",
                () -> service.record(1L, -1L, 3L, PAID_AT, null, null, null));

        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void rejectsInvalidVersions() {
        assertCode("INVALID_PAYMENT_REQUEST_VERSION",
                () -> service.record(1L, 9L, null, PAID_AT, null, null, null));
        assertCode("INVALID_PAYMENT_REQUEST_VERSION",
                () -> service.record(1L, 9L, -1L, PAID_AT, null, null, null));

        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void rejectsNullPaidAt() {
        assertCode("INVALID_PAYMENT_DATE",
                () -> service.record(1L, 9L, 3L, null, null, null, null));

        verify(paymentRequestRepository, never()).findById(any());
    }

    private PaymentRequest approvedUnpaidRequest() {
        PaymentRequest paymentRequest = new PaymentRequest();
        setField(paymentRequest, "id", 1L);
        setField(paymentRequest, "version", 3L);
        paymentRequest.setRequestNo("PAY-20260731-000001");
        paymentRequest.setApprovalStatus(ApprovalStatus.APPROVED);
        paymentRequest.setPaymentStatus(PaymentStatus.UNPAID);
        paymentRequest.setTotalAmount(new BigDecimal("1620.50"));
        paymentRequest.setSubmittedAt(OffsetDateTime.parse(
                "2026-07-30T09:00:00+08:00"));
        paymentRequest.setApprovedAt(OffsetDateTime.parse(
                "2026-07-30T10:00:00+08:00"));
        AppUser approvedBy = new AppUser();
        setField(approvedBy, "id", 2L);
        approvedBy.setDisplayName("核准人");
        paymentRequest.setApprovedBy(approvedBy);
        AppUser supervisorSnapshot = new AppUser();
        setField(supervisorSnapshot, "id", 3L);
        supervisorSnapshot.setDisplayName("主管快照");
        paymentRequest.setSupervisorSnapshot(supervisorSnapshot);
        return paymentRequest;
    }

    private AppUser paidBy() {
        AppUser paidBy = new AppUser();
        setField(paidBy, "id", 9L);
        paidBy.setDisplayName("付款人 A");
        paidBy.setActive(true);
        return paidBy;
    }

    private void stubValidPayment(
            PaymentRequest paymentRequest,
            AppUser paidBy
    ) {
        stubPaymentRequest(paymentRequest);
        when(appUserRepository.findById(9L)).thenReturn(Optional.of(paidBy));
    }

    private void stubPaymentRequest(PaymentRequest paymentRequest) {
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
    }

    private void stubSaveAndFlushVersion(Long version) {
        when(paymentRequestRepository.saveAndFlush(any(PaymentRequest.class)))
                .thenAnswer(invocation -> {
                    PaymentRequest paymentRequest = invocation.getArgument(0);
                    setField(paymentRequest, "version", version);
                    return paymentRequest;
                });
    }

    private ApprovalHistory capturedHistory() {
        ArgumentCaptor<ApprovalHistory> captor =
                ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(approvalHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private PaymentDraftBusinessException assertCode(
            String expectedCode,
            org.junit.jupiter.api.function.Executable executable
    ) {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                executable
        );
        assertEquals(expectedCode, exception.getCode());
        return exception;
    }

    private void setField(Object target, String fieldName, Object value) {
        ReflectionTestUtils.setField(target, fieldName, value);
    }
}
