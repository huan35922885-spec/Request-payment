package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import tw.com.jsgcpa.paymentapproval.approval.entity.ApprovalHistory;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalAction;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.approval.repository.ApprovalHistoryRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.CashierReviewPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@ExtendWith(MockitoExtension.class)
class CashierReviewPaymentServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");
    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-07-31T01:30:00Z");
    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_INSTANT, BUSINESS_ZONE);
    private static final OffsetDateTime ACTED_AT = OffsetDateTime.of(
            2026,
            7,
            31,
            9,
            30,
            0,
            0,
            BUSINESS_ZONE.getRules().getOffset(FIXED_INSTANT)
    );

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private ApprovalHistoryRepository approvalHistoryRepository;

    private CashierReviewPaymentService service;

    @BeforeEach
    void setUp() {
        service = new CashierReviewPaymentService(
                paymentRequestRepository,
                appUserRepository,
                approvalHistoryRepository,
                FIXED_CLOCK
        );
    }

    @Test
    void approvesPendingCashierPaymentRequest() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        AppUser cashier = cashier();
        stubValidReview(paymentRequest, cashier);
        stubSaveAndFlushVersion(3L);

        CashierReviewPaymentResponse response = service.approve(
                1L,
                9L,
                2L,
                "出納確認"
        );

        assertEquals(ApprovalStatus.APPROVED,
                paymentRequest.getApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, paymentRequest.getPaymentStatus());
        assertEquals(ACTED_AT, paymentRequest.getApprovedAt());
        assertSame(cashier, paymentRequest.getApprovedBy());
        assertNull(paymentRequest.getClosedAt());
        assertNull(paymentRequest.getPaidAt());
        assertNull(paymentRequest.getPaidBy());
        assertNull(paymentRequest.getPaymentMethod());
        assertNull(paymentRequest.getPaymentReference());
        assertNull(paymentRequest.getPaymentNote());
        assertEquals(ApprovalAction.CASHIER_APPROVE, response.action());
        assertEquals(ApprovalStatus.APPROVED, response.approvalStatus());
        assertEquals(PaymentStatus.UNPAID, response.paymentStatus());
        assertEquals(9L, response.cashierId());
        assertEquals("出納 A", response.cashierName());
        assertEquals("出納確認", response.comment());
        assertEquals(ACTED_AT, response.actedAt());
        assertEquals(3L, response.version());
    }

    @Test
    void createsCompleteCashierApproveHistory() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        AppUser cashier = cashier();
        stubValidReview(paymentRequest, cashier);
        stubSaveAndFlushVersion(3L);

        service.approve(1L, 9L, 2L, "核准付款申請");

        ApprovalHistory history = capturedHistory();
        assertSame(paymentRequest, history.getPaymentRequest());
        assertSame(cashier, history.getActor());
        assertEquals(ApprovalAction.CASHIER_APPROVE, history.getAction());
        assertEquals(ApprovalStatus.PENDING_CASHIER,
                history.getFromApprovalStatus());
        assertEquals(ApprovalStatus.APPROVED,
                history.getToApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, history.getFromPaymentStatus());
        assertEquals(PaymentStatus.UNPAID, history.getToPaymentStatus());
        assertEquals("核准付款申請", history.getComment());
        assertEquals(ACTED_AT, history.getActedAt());
        assertEquals(paymentRequest.getApprovedAt(), history.getActedAt());
    }

    @Test
    void rejectsPendingCashierPaymentRequestAndClosesIt() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        AppUser cashier = cashier();
        stubValidReview(paymentRequest, cashier);
        stubSaveAndFlushVersion(3L);

        CashierReviewPaymentResponse response = service.reject(
                1L,
                9L,
                2L,
                "資料不完整"
        );

        assertEquals(ApprovalStatus.REJECTED_CLOSED,
                paymentRequest.getApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, paymentRequest.getPaymentStatus());
        assertEquals(ACTED_AT, paymentRequest.getRejectedAt());
        assertEquals(ACTED_AT, paymentRequest.getClosedAt());
        assertNull(paymentRequest.getApprovedAt());
        assertNull(paymentRequest.getApprovedBy());
        assertEquals(ApprovalAction.CASHIER_REJECT, response.action());
        assertEquals(ApprovalStatus.REJECTED_CLOSED,
                response.approvalStatus());
        assertEquals(PaymentStatus.UNPAID, response.paymentStatus());
        assertEquals(9L, response.cashierId());
        assertEquals("出納 A", response.cashierName());
        assertEquals("資料不完整", response.comment());
        assertEquals(ACTED_AT, response.actedAt());
        assertEquals(3L, response.version());
    }

    @Test
    void createsCompleteCashierRejectHistory() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        AppUser cashier = cashier();
        stubValidReview(paymentRequest, cashier);
        stubSaveAndFlushVersion(3L);

        service.reject(1L, 9L, 2L, "退回補件");

        ApprovalHistory history = capturedHistory();
        assertSame(paymentRequest, history.getPaymentRequest());
        assertSame(cashier, history.getActor());
        assertEquals(ApprovalAction.CASHIER_REJECT, history.getAction());
        assertEquals(ApprovalStatus.PENDING_CASHIER,
                history.getFromApprovalStatus());
        assertEquals(ApprovalStatus.REJECTED_CLOSED,
                history.getToApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, history.getFromPaymentStatus());
        assertEquals(PaymentStatus.UNPAID, history.getToPaymentStatus());
        assertEquals("退回補件", history.getComment());
        assertEquals(ACTED_AT, history.getActedAt());
        assertEquals(paymentRequest.getRejectedAt(), history.getActedAt());
        assertEquals(paymentRequest.getClosedAt(), history.getActedAt());
    }

    @Test
    void preservesPaymentStatusForApproveAndReject() {
        PaymentRequest approveRequest = pendingCashierPaymentRequest(
                PaymentStatus.PAID
        );
        AppUser cashier = cashier();
        stubValidReview(approveRequest, cashier);
        stubSaveAndFlushVersion(3L);

        service.approve(1L, 9L, 2L, null);

        assertEquals(PaymentStatus.PAID, approveRequest.getPaymentStatus());

        PaymentRequest rejectRequest = pendingCashierPaymentRequest(
                PaymentStatus.PAID
        );
        stubValidReview(rejectRequest, cashier);
        stubSaveAndFlushVersion(3L);

        service.reject(1L, 9L, 2L, null);

        assertEquals(PaymentStatus.PAID, rejectRequest.getPaymentStatus());
    }

    @Test
    void savesPaymentRequestBeforeCashierApproveHistory() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        stubValidReview(paymentRequest, cashier());
        stubSaveAndFlushVersion(3L);

        service.approve(1L, 9L, 2L, null);

        InOrder order = inOrder(
                paymentRequestRepository,
                approvalHistoryRepository
        );
        order.verify(paymentRequestRepository).saveAndFlush(paymentRequest);
        order.verify(approvalHistoryRepository).save(any(ApprovalHistory.class));
    }

    @Test
    void savesPaymentRequestBeforeCashierRejectHistory() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        stubValidReview(paymentRequest, cashier());
        stubSaveAndFlushVersion(3L);

        service.reject(1L, 9L, 2L, null);

        InOrder order = inOrder(
                paymentRequestRepository,
                approvalHistoryRepository
        );
        order.verify(paymentRequestRepository).saveAndFlush(paymentRequest);
        order.verify(approvalHistoryRepository).save(any(ApprovalHistory.class));
    }

    @Test
    void rejectsMissingPaymentRequestBeforeLookingUpCashier() {
        when(paymentRequestRepository.findById(1L)).thenReturn(Optional.empty());

        assertCode(
                "PAYMENT_REQUEST_NOT_FOUND",
                () -> service.approve(1L, 9L, 2L, null)
        );

        verify(appUserRepository, never()).findById(any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsVersionConflictBeforeLookingUpCashier() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        setField(paymentRequest, "version", 3L);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode(
                "PAYMENT_REQUEST_VERSION_CONFLICT",
                () -> service.approve(1L, 9L, 2L, null)
        );

        verify(appUserRepository, never()).findById(any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = ApprovalStatus.class,
            names = {
                    "DRAFT",
                    "PENDING_MANAGER",
                    "APPROVED",
                    "REJECTED_CLOSED"
            }
    )
    void rejectsStatusesOtherThanPendingCashier(ApprovalStatus status) {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        paymentRequest.setApprovalStatus(status);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode(
                "PAYMENT_REQUEST_NOT_PENDING_CASHIER",
                () -> service.reject(1L, 9L, 2L, null)
        );

        verify(appUserRepository, never()).findById(any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectedClosedCannotBeApprovedOrRejectedAgain() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        paymentRequest.setApprovalStatus(ApprovalStatus.REJECTED_CLOSED);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode(
                "PAYMENT_REQUEST_NOT_PENDING_CASHIER",
                () -> service.approve(1L, 9L, 2L, null)
        );
        assertCode(
                "PAYMENT_REQUEST_NOT_PENDING_CASHIER",
                () -> service.reject(1L, 9L, 2L, null)
        );

        verify(appUserRepository, never()).findById(any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsMissingCashier() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(appUserRepository.findById(9L)).thenReturn(Optional.empty());

        assertCode(
                "CASHIER_NOT_FOUND",
                () -> service.approve(1L, 9L, 2L, null)
        );

        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsInactiveCashier() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        AppUser cashier = cashier();
        cashier.setActive(false);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(appUserRepository.findById(9L))
                .thenReturn(Optional.of(cashier));

        assertCode(
                "CASHIER_INACTIVE",
                () -> service.reject(1L, 9L, 2L, null)
        );

        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void convertsApproveOptimisticLockingFailureAndDoesNotCreateHistory() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        stubValidReview(paymentRequest, cashier());
        when(paymentRequestRepository.saveAndFlush(paymentRequest))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        PaymentRequest.class,
                        1L
                ));

        assertCode(
                "PAYMENT_REQUEST_VERSION_CONFLICT",
                () -> service.approve(1L, 9L, 2L, null)
        );

        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void convertsRejectOptimisticLockingFailureAndDoesNotCreateHistory() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        stubValidReview(paymentRequest, cashier());
        when(paymentRequestRepository.saveAndFlush(paymentRequest))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        assertCode(
                "PAYMENT_REQUEST_VERSION_CONFLICT",
                () -> service.reject(1L, 9L, 2L, null)
        );

        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void propagatesApproveHistorySaveFailure() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        stubValidReview(paymentRequest, cashier());
        stubSaveAndFlushVersion(3L);
        when(approvalHistoryRepository.save(any(ApprovalHistory.class)))
                .thenThrow(new RuntimeException("history save failed"));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.approve(1L, 9L, 2L, "核准")
        );

        assertEquals("history save failed", exception.getMessage());
        verify(paymentRequestRepository, times(1)).saveAndFlush(paymentRequest);
        verify(approvalHistoryRepository, times(1)).save(any());
    }

    @Test
    void propagatesRejectHistorySaveFailure() {
        PaymentRequest paymentRequest = pendingCashierPaymentRequest(
                PaymentStatus.UNPAID
        );
        stubValidReview(paymentRequest, cashier());
        stubSaveAndFlushVersion(3L);
        when(approvalHistoryRepository.save(any(ApprovalHistory.class)))
                .thenThrow(new RuntimeException("history save failed"));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.reject(1L, 9L, 2L, "退回")
        );

        assertEquals("history save failed", exception.getMessage());
        verify(paymentRequestRepository, times(1)).saveAndFlush(paymentRequest);
        verify(approvalHistoryRepository, times(1)).save(any());
    }

    @Test
    void rejectsInvalidPaymentRequestIds() {
        assertCode("INVALID_PAYMENT_REQUEST_ID",
                () -> service.approve(null, 9L, 2L, null));
        assertCode("INVALID_PAYMENT_REQUEST_ID",
                () -> service.approve(0L, 9L, 2L, null));
        assertCode("INVALID_PAYMENT_REQUEST_ID",
                () -> service.approve(-1L, 9L, 2L, null));

        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void rejectsInvalidCashierIds() {
        assertCode("INVALID_CASHIER_ID",
                () -> service.approve(1L, null, 2L, null));
        assertCode("INVALID_CASHIER_ID",
                () -> service.approve(1L, 0L, 2L, null));
        assertCode("INVALID_CASHIER_ID",
                () -> service.approve(1L, -1L, 2L, null));

        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void rejectsInvalidExpectedVersions() {
        assertCode("INVALID_PAYMENT_REQUEST_VERSION",
                () -> service.approve(1L, 9L, null, null));
        assertCode("INVALID_PAYMENT_REQUEST_VERSION",
                () -> service.approve(1L, 9L, -1L, null));

        verify(paymentRequestRepository, never()).findById(any());
    }

    private PaymentRequest pendingCashierPaymentRequest(
            PaymentStatus paymentStatus
    ) {
        PaymentRequest paymentRequest = new PaymentRequest();
        setField(paymentRequest, "id", 1L);
        setField(paymentRequest, "version", 2L);
        paymentRequest.setRequestNo("PAY-20260731-000001");
        paymentRequest.setApprovalStatus(ApprovalStatus.PENDING_CASHIER);
        paymentRequest.setPaymentStatus(paymentStatus);
        paymentRequest.setTotalAmount(new java.math.BigDecimal("1620.50"));
        paymentRequest.setSubmittedAt(ACTED_AT);
        return paymentRequest;
    }

    private AppUser cashier() {
        AppUser cashier = new AppUser();
        setField(cashier, "id", 9L);
        cashier.setDisplayName("出納 A");
        cashier.setActive(true);
        return cashier;
    }

    private void stubValidReview(
            PaymentRequest paymentRequest,
            AppUser cashier
    ) {
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(appUserRepository.findById(9L))
                .thenReturn(Optional.of(cashier));
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
