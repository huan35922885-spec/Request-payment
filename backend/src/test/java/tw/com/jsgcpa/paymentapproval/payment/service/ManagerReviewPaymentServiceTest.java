package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import tw.com.jsgcpa.paymentapproval.payment.dto.response.ManagerReviewPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@ExtendWith(MockitoExtension.class)
class ManagerReviewPaymentServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");
    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-07-31T01:30:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, BUSINESS_ZONE);
    private static final OffsetDateTime ACTED_AT =
            OffsetDateTime.of(2026, 7, 31, 9, 30, 0, 0, BUSINESS_ZONE.getRules()
                    .getOffset(FIXED_INSTANT));

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private ApprovalHistoryRepository approvalHistoryRepository;

    private ManagerReviewPaymentService service;

    @BeforeEach
    void setUp() {
        service = new ManagerReviewPaymentService(
                paymentRequestRepository,
                approvalHistoryRepository,
                FIXED_CLOCK
        );
    }

    @Test
    void approvesPendingManagerPaymentRequest() {
        PaymentRequest paymentRequest = pendingManagerPaymentRequest(
                PaymentStatus.UNPAID
        );
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        stubSaveAndFlushIncrementsVersion();

        ManagerReviewPaymentResponse response = service.approve(
                1L,
                2L,
                0L,
                "主管同意"
        );

        assertEquals(ApprovalStatus.PENDING_CASHIER,
                paymentRequest.getApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, paymentRequest.getPaymentStatus());
        assertEquals(ACTED_AT, paymentRequest.getSubmittedAt());
        assertEquals(null, paymentRequest.getApprovedAt());
        assertEquals(null, paymentRequest.getApprovedBy());
        assertEquals(1L, response.version());
        assertEquals(ApprovalAction.MANAGER_APPROVE, response.action());
        assertEquals(ApprovalStatus.PENDING_CASHIER, response.approvalStatus());
        assertEquals(PaymentStatus.UNPAID, response.paymentStatus());
        assertEquals(2L, response.managerId());
        assertEquals("主管 A", response.managerName());
        assertEquals("主管同意", response.comment());
        assertEquals(ACTED_AT, response.actedAt());
        verify(paymentRequestRepository, times(1)).saveAndFlush(paymentRequest);
        verify(approvalHistoryRepository, times(1)).save(any(ApprovalHistory.class));
    }

    @Test
    void createsCompleteManagerApproveHistory() {
        PaymentRequest paymentRequest = pendingManagerPaymentRequest(
                PaymentStatus.UNPAID
        );
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        stubSaveAndFlushIncrementsVersion();

        service.approve(1L, 2L, 0L, "核准明細");

        ApprovalHistory history = capturedHistory();
        assertSame(paymentRequest, history.getPaymentRequest());
        assertSame(paymentRequest.getSupervisorSnapshot(), history.getActor());
        assertEquals(ApprovalAction.MANAGER_APPROVE, history.getAction());
        assertEquals(ApprovalStatus.PENDING_MANAGER,
                history.getFromApprovalStatus());
        assertEquals(ApprovalStatus.PENDING_CASHIER,
                history.getToApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, history.getFromPaymentStatus());
        assertEquals(PaymentStatus.UNPAID, history.getToPaymentStatus());
        assertEquals("核准明細", history.getComment());
        assertEquals(ACTED_AT, history.getActedAt());
    }

    @Test
    void rejectsPendingManagerPaymentRequestAndClosesIt() {
        PaymentRequest paymentRequest = pendingManagerPaymentRequest(
                PaymentStatus.PAID
        );
        OffsetDateTime submittedAt = OffsetDateTime.of(
                2026, 7, 30, 17, 0, 0, 0,
                BUSINESS_ZONE.getRules().getOffset(FIXED_INSTANT)
        );
        paymentRequest.setSubmittedAt(submittedAt);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        stubSaveAndFlushIncrementsVersion();

        ManagerReviewPaymentResponse response = service.reject(
                1L,
                2L,
                0L,
                "資料不完整"
        );

        assertEquals(ApprovalStatus.REJECTED_CLOSED,
                paymentRequest.getApprovalStatus());
        assertEquals(PaymentStatus.PAID, paymentRequest.getPaymentStatus());
        assertEquals(ACTED_AT, paymentRequest.getRejectedAt());
        assertEquals(ACTED_AT, paymentRequest.getClosedAt());
        assertEquals(submittedAt, paymentRequest.getSubmittedAt());
        assertEquals(null, paymentRequest.getApprovedAt());
        assertEquals(null, paymentRequest.getApprovedBy());
        assertEquals(ApprovalAction.MANAGER_REJECT, response.action());
        assertEquals(ApprovalStatus.REJECTED_CLOSED, response.approvalStatus());
        assertEquals(PaymentStatus.PAID, response.paymentStatus());
        assertEquals(ACTED_AT, response.actedAt());
        assertEquals(1L, response.version());
        verify(paymentRequestRepository, times(1)).saveAndFlush(paymentRequest);
        verify(approvalHistoryRepository, times(1)).save(any(ApprovalHistory.class));
    }

    @Test
    void createsCompleteManagerRejectHistory() {
        PaymentRequest paymentRequest = pendingManagerPaymentRequest(
                PaymentStatus.UNPAID
        );
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        stubSaveAndFlushIncrementsVersion();

        service.reject(1L, 2L, 0L, "退回補件");

        ApprovalHistory history = capturedHistory();
        assertSame(paymentRequest, history.getPaymentRequest());
        assertSame(paymentRequest.getSupervisorSnapshot(), history.getActor());
        assertEquals(ApprovalAction.MANAGER_REJECT, history.getAction());
        assertEquals(ApprovalStatus.PENDING_MANAGER,
                history.getFromApprovalStatus());
        assertEquals(ApprovalStatus.REJECTED_CLOSED,
                history.getToApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, history.getFromPaymentStatus());
        assertEquals(PaymentStatus.UNPAID, history.getToPaymentStatus());
        assertEquals("退回補件", history.getComment());
        assertEquals(ACTED_AT, history.getActedAt());
    }

    @Test
    void rejectsMissingPaymentRequest() {
        when(paymentRequestRepository.findById(1L)).thenReturn(Optional.empty());

        assertCode(
                "PAYMENT_REQUEST_NOT_FOUND",
                () -> service.approve(1L, 2L, 0L, null)
        );

        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsVersionConflict() {
        PaymentRequest paymentRequest = pendingManagerPaymentRequest(
                PaymentStatus.UNPAID
        );
        setField(paymentRequest, "version", 1L);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode(
                "PAYMENT_REQUEST_VERSION_CONFLICT",
                () -> service.approve(1L, 2L, 0L, null)
        );

        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsStatusesOtherThanPendingManager() {
        for (ApprovalStatus status : List.of(
                ApprovalStatus.DRAFT,
                ApprovalStatus.PENDING_CASHIER,
                ApprovalStatus.REJECTED_CLOSED
        )) {
            PaymentRequest paymentRequest = pendingManagerPaymentRequest(
                    PaymentStatus.UNPAID
            );
            paymentRequest.setApprovalStatus(status);
            when(paymentRequestRepository.findById(1L))
                    .thenReturn(Optional.of(paymentRequest));

            assertCode(
                    "PAYMENT_REQUEST_NOT_PENDING_MANAGER",
                    () -> service.reject(1L, 2L, 0L, null)
            );
        }

        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsMissingSupervisorSnapshot() {
        PaymentRequest paymentRequest = pendingManagerPaymentRequest(
                PaymentStatus.UNPAID
        );
        paymentRequest.setSupervisorSnapshot(null);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode(
                "PAYMENT_REQUEST_MANAGER_FORBIDDEN",
                () -> service.approve(1L, 2L, 0L, null)
        );

        verify(paymentRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsManagerNotMatchingSupervisorSnapshot() {
        PaymentRequest paymentRequest = pendingManagerPaymentRequest(
                PaymentStatus.UNPAID
        );
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode(
                "PAYMENT_REQUEST_MANAGER_FORBIDDEN",
                () -> service.reject(1L, 99L, 0L, null)
        );

        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsNonOwnerBeforeVersionCheckAndMutation() {
        PaymentRequest paymentRequest = pendingManagerPaymentRequest(
                PaymentStatus.UNPAID
        );
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode(
                "PAYMENT_REQUEST_MANAGER_FORBIDDEN",
                () -> service.approve(1L, 99L, 999L, null)
        );

        assertEquals(ApprovalStatus.PENDING_MANAGER,
                paymentRequest.getApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, paymentRequest.getPaymentStatus());
        assertEquals(0L, paymentRequest.getVersion());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void convertsOptimisticLockingFailureAndDoesNotCreateHistory() {
        PaymentRequest paymentRequest = pendingManagerPaymentRequest(
                PaymentStatus.UNPAID
        );
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(paymentRequestRepository.saveAndFlush(paymentRequest))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        PaymentRequest.class,
                        1L
                ));

        assertCode(
                "PAYMENT_REQUEST_VERSION_CONFLICT",
                () -> service.approve(1L, 2L, 0L, null)
        );

        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void propagatesApprovalHistorySaveFailure() {
        PaymentRequest paymentRequest = pendingManagerPaymentRequest(
                PaymentStatus.UNPAID
        );
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        stubSaveAndFlushIncrementsVersion();
        when(approvalHistoryRepository.save(any(ApprovalHistory.class)))
                .thenThrow(new RuntimeException("history save failed"));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.reject(1L, 2L, 0L, "退回")
        );

        assertEquals("history save failed", exception.getMessage());
        verify(paymentRequestRepository, times(1)).saveAndFlush(paymentRequest);
        verify(approvalHistoryRepository, times(1)).save(any(ApprovalHistory.class));
    }

    @Test
    void rejectsInvalidPaymentRequestIds() {
        assertCode("INVALID_PAYMENT_REQUEST_ID",
                () -> service.approve(null, 2L, 0L, null));
        assertCode("INVALID_PAYMENT_REQUEST_ID",
                () -> service.approve(0L, 2L, 0L, null));
        assertCode("INVALID_PAYMENT_REQUEST_ID",
                () -> service.approve(-1L, 2L, 0L, null));

        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void rejectsInvalidManagerIds() {
        assertCode("INVALID_AUTHENTICATED_USER_ID",
                () -> service.approve(1L, null, 0L, null));
        assertCode("INVALID_AUTHENTICATED_USER_ID",
                () -> service.approve(1L, 0L, 0L, null));
        assertCode("INVALID_AUTHENTICATED_USER_ID",
                () -> service.approve(1L, -1L, 0L, null));

        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void rejectsInvalidExpectedVersions() {
        PaymentRequest paymentRequest = pendingManagerPaymentRequest(
                PaymentStatus.UNPAID
        );
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode("INVALID_PAYMENT_REQUEST_VERSION",
                () -> service.approve(1L, 2L, null, null));
        assertCode("INVALID_PAYMENT_REQUEST_VERSION",
                () -> service.approve(1L, 2L, -1L, null));

        verify(paymentRequestRepository, times(2)).findById(1L);
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    private PaymentRequest pendingManagerPaymentRequest(
            PaymentStatus paymentStatus
    ) {
        PaymentRequest paymentRequest = new PaymentRequest();
        setField(paymentRequest, "id", 1L);
        setField(paymentRequest, "version", 0L);
        paymentRequest.setRequestNo("PAY-20260731-000001");
        paymentRequest.setApprovalStatus(ApprovalStatus.PENDING_MANAGER);
        paymentRequest.setPaymentStatus(paymentStatus);
        paymentRequest.setSubmittedAt(ACTED_AT);
        paymentRequest.setTotalAmount(new java.math.BigDecimal("1620.50"));
        paymentRequest.setSupervisorSnapshot(supervisor());
        return paymentRequest;
    }

    private AppUser supervisor() {
        AppUser supervisor = new AppUser();
        setField(supervisor, "id", 2L);
        supervisor.setDisplayName("主管 A");
        supervisor.setActive(false);
        return supervisor;
    }

    private void stubSaveAndFlushIncrementsVersion() {
        when(paymentRequestRepository.saveAndFlush(any(PaymentRequest.class)))
                .thenAnswer(invocation -> {
                    PaymentRequest paymentRequest = invocation.getArgument(0);
                    setField(paymentRequest, "version", 1L);
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
            Executable executable
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
