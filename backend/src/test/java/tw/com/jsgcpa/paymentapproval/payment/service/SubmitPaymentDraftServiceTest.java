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
import java.time.LocalDate;
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
import tw.com.jsgcpa.paymentapproval.organization.entity.Department;
import tw.com.jsgcpa.paymentapproval.organization.entity.DepartmentSupervisor;
import tw.com.jsgcpa.paymentapproval.organization.repository.DepartmentSupervisorRepository;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.SubmitPaymentDraftResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@ExtendWith(MockitoExtension.class)
class SubmitPaymentDraftServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");
    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-07-31T01:30:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, BUSINESS_ZONE);
    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 7, 31);
    private static final OffsetDateTime SUBMITTED_AT =
            OffsetDateTime.of(2026, 7, 31, 9, 30, 0, 0, BUSINESS_ZONE.getRules()
                    .getOffset(FIXED_INSTANT));

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private DepartmentSupervisorRepository departmentSupervisorRepository;

    @Mock
    private ApprovalHistoryRepository approvalHistoryRepository;

    private SubmitPaymentDraftService service;

    @BeforeEach
    void setUp() {
        service = new SubmitPaymentDraftService(
                paymentRequestRepository,
                departmentSupervisorRepository,
                approvalHistoryRepository,
                FIXED_CLOCK
        );
    }

    @Test
    void submitsDraftAndCreatesSubmitHistory() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        DepartmentSupervisor departmentSupervisor = effectiveSupervisor(true);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(departmentSupervisorRepository.findEffectiveSupervisors(
                10L,
                EFFECTIVE_DATE
        )).thenReturn(List.of(departmentSupervisor));
        stubSaveAndFlushIncrementsVersion();

        SubmitPaymentDraftResponse response = service.submit(1L, 3L, 0L);

        assertSame(departmentSupervisor.getSupervisor(), paymentRequest.getSupervisorSnapshot());
        assertEquals(ApprovalStatus.PENDING_MANAGER, paymentRequest.getApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, paymentRequest.getPaymentStatus());
        assertEquals(SUBMITTED_AT, paymentRequest.getSubmittedAt());
        assertEquals(1L, paymentRequest.getVersion());
        assertEquals(1L, response.id());
        assertEquals("PAY-20260731-000001", response.requestNo());
        assertEquals(ApprovalStatus.PENDING_MANAGER, response.approvalStatus());
        assertEquals(PaymentStatus.UNPAID, response.paymentStatus());
        assertEquals(2L, response.supervisorId());
        assertEquals("主管 A", response.supervisorName());
        assertEquals(SUBMITTED_AT, response.submittedAt());
        assertEquals(1L, response.version());
        verify(paymentRequestRepository, times(1)).saveAndFlush(paymentRequest);
        verify(approvalHistoryRepository, times(1)).save(any(ApprovalHistory.class));
    }

    @Test
    void createsHistoryWithCompleteSubmitTransition() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        DepartmentSupervisor departmentSupervisor = effectiveSupervisor(true);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(departmentSupervisorRepository.findEffectiveSupervisors(
                10L,
                EFFECTIVE_DATE
        )).thenReturn(List.of(departmentSupervisor));
        stubSaveAndFlushIncrementsVersion();

        service.submit(1L, 3L, 0L);

        ArgumentCaptor<ApprovalHistory> captor =
                ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(approvalHistoryRepository).save(captor.capture());
        ApprovalHistory history = captor.getValue();
        assertSame(paymentRequest, history.getPaymentRequest());
        assertSame(paymentRequest.getApplicant(), history.getActor());
        assertEquals(ApprovalAction.SUBMIT, history.getAction());
        assertEquals(ApprovalStatus.DRAFT, history.getFromApprovalStatus());
        assertEquals(ApprovalStatus.PENDING_MANAGER, history.getToApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, history.getFromPaymentStatus());
        assertEquals(PaymentStatus.UNPAID, history.getToPaymentStatus());
        assertEquals(null, history.getComment());
        assertEquals(SUBMITTED_AT, history.getActedAt());
    }

    @Test
    void rejectsNonApplicantBeforeVersionAndSupervisorProcessing() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode(
                "PAYMENT_REQUEST_SUBMIT_FORBIDDEN",
                () -> service.submit(1L, 999L, 99L)
        );

        assertEquals(ApprovalStatus.DRAFT, paymentRequest.getApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, paymentRequest.getPaymentStatus());
        assertEquals(0L, paymentRequest.getVersion());
        assertEquals(null, paymentRequest.getSupervisorSnapshot());
        assertEquals(null, paymentRequest.getSubmittedAt());
        verify(departmentSupervisorRepository, never())
                .findEffectiveSupervisors(any(), any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidAuthenticatedUserIdsBeforeLoadingRequest() {
        assertCode(
                "INVALID_AUTHENTICATED_USER_ID",
                () -> service.submit(1L, null, 0L)
        );
        assertCode(
                "INVALID_AUTHENTICATED_USER_ID",
                () -> service.submit(1L, 0L, 0L)
        );
        assertCode(
                "INVALID_AUTHENTICATED_USER_ID",
                () -> service.submit(1L, -1L, 0L)
        );

        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void rejectsMissingPaymentRequest() {
        when(paymentRequestRepository.findById(1L)).thenReturn(Optional.empty());

        assertCode(
                "PAYMENT_REQUEST_NOT_FOUND",
                () -> service.submit(1L, 3L, 0L)
        );

        verify(departmentSupervisorRepository, never())
                .findEffectiveSupervisors(any(), any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsVersionConflictBeforeSupervisorLookup() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        setField(paymentRequest, "version", 1L);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        PaymentDraftBusinessException exception = assertCode(
                "PAYMENT_REQUEST_VERSION_CONFLICT",
                () -> service.submit(1L, 3L, 0L)
        );

        assertEquals(true, exception.getMessage().contains("paymentRequestId")
                || exception.getMessage().contains("id 1"));
        assertEquals(true, exception.getMessage().contains("expectedVersion=0"));
        assertEquals(true, exception.getMessage().contains("currentVersion=1"));
        verify(departmentSupervisorRepository, never())
                .findEffectiveSupervisors(any(), any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsPendingManagerAndRejectedClosedStatuses() {
        for (ApprovalStatus status : List.of(
                ApprovalStatus.PENDING_MANAGER,
                ApprovalStatus.REJECTED_CLOSED
        )) {
            PaymentRequest paymentRequest = draftPaymentRequest();
            paymentRequest.setApprovalStatus(status);
            when(paymentRequestRepository.findById(1L))
                    .thenReturn(Optional.of(paymentRequest));

            assertCode(
                    "PAYMENT_REQUEST_NOT_DRAFT",
                    () -> service.submit(1L, 3L, 0L)
            );
        }

        verify(departmentSupervisorRepository, never())
                .findEffectiveSupervisors(any(), any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsMissingDepartment() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        paymentRequest.setDepartment(null);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode(
                "PAYMENT_REQUEST_DEPARTMENT_MISSING",
                () -> service.submit(1L, 3L, 0L)
        );

        verify(departmentSupervisorRepository, never())
                .findEffectiveSupervisors(any(), any());
    }

    @Test
    void rejectsWhenNoEffectiveSupervisorExists() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(departmentSupervisorRepository.findEffectiveSupervisors(
                10L,
                EFFECTIVE_DATE
        )).thenReturn(List.of());

        assertCode(
                "SUPERVISOR_NOT_FOUND",
                () -> service.submit(1L, 3L, 0L)
        );
    }

    @Test
    void rejectsMultipleEffectiveSupervisorsWithoutChoosingFirst() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        DepartmentSupervisor first = effectiveSupervisor(true);
        DepartmentSupervisor second = effectiveSupervisor(true);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(departmentSupervisorRepository.findEffectiveSupervisors(
                10L,
                EFFECTIVE_DATE
        )).thenReturn(List.of(first, second));

        assertCode(
                "SUPERVISOR_CONFLICT",
                () -> service.submit(1L, 3L, 0L)
        );

        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsInactiveSupervisor() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        DepartmentSupervisor departmentSupervisor = effectiveSupervisor(false);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(departmentSupervisorRepository.findEffectiveSupervisors(
                10L,
                EFFECTIVE_DATE
        )).thenReturn(List.of(departmentSupervisor));

        assertCode(
                "SUPERVISOR_INACTIVE",
                () -> service.submit(1L, 3L, 0L)
        );

        assertEquals(null, paymentRequest.getSupervisorSnapshot());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNullSupervisor() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        DepartmentSupervisor departmentSupervisor = new DepartmentSupervisor();
        departmentSupervisor.setDepartment(paymentRequest.getDepartment());
        departmentSupervisor.setSupervisor(null);
        departmentSupervisor.setActive(true);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(departmentSupervisorRepository.findEffectiveSupervisors(
                10L,
                EFFECTIVE_DATE
        )).thenReturn(List.of(departmentSupervisor));

        assertCode(
                "SUPERVISOR_NOT_FOUND",
                () -> service.submit(1L, 3L, 0L)
        );
    }

    @Test
    void passesEffectiveDateFromBusinessClock() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        DepartmentSupervisor departmentSupervisor = effectiveSupervisor(true);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(departmentSupervisorRepository.findEffectiveSupervisors(
                10L,
                EFFECTIVE_DATE
        )).thenReturn(List.of(departmentSupervisor));
        stubSaveAndFlushIncrementsVersion();

        service.submit(1L, 3L, 0L);

        verify(departmentSupervisorRepository).findEffectiveSupervisors(
                10L,
                EFFECTIVE_DATE
        );
    }

    @Test
    void convertsOptimisticLockingFailureToBusinessException() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        DepartmentSupervisor departmentSupervisor = effectiveSupervisor(true);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(departmentSupervisorRepository.findEffectiveSupervisors(
                10L,
                EFFECTIVE_DATE
        )).thenReturn(List.of(departmentSupervisor));
        when(paymentRequestRepository.saveAndFlush(paymentRequest))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        PaymentRequest.class,
                        1L
                ));

        assertCode(
                "PAYMENT_REQUEST_VERSION_CONFLICT",
                () -> service.submit(1L, 3L, 0L)
        );

        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void propagatesApprovalHistorySaveFailure() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        DepartmentSupervisor departmentSupervisor = effectiveSupervisor(true);
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
        when(departmentSupervisorRepository.findEffectiveSupervisors(
                10L,
                EFFECTIVE_DATE
        )).thenReturn(List.of(departmentSupervisor));
        stubSaveAndFlushIncrementsVersion();
        when(approvalHistoryRepository.save(any(ApprovalHistory.class)))
                .thenThrow(new RuntimeException("history save failed"));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> service.submit(1L, 3L, 0L)
        );

        assertEquals("history save failed", exception.getMessage());
    }

    @Test
    void rejectsInvalidPaymentRequestIds() {
        assertCode("INVALID_PAYMENT_REQUEST_ID", () -> service.submit(null, 3L, 0L));
        assertCode("INVALID_PAYMENT_REQUEST_ID", () -> service.submit(0L, 3L, 0L));
        assertCode("INVALID_PAYMENT_REQUEST_ID", () -> service.submit(-1L, 3L, 0L));

        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void rejectsInvalidExpectedVersions() {
        PaymentRequest paymentRequest = draftPaymentRequest();
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));

        assertCode(
                "INVALID_PAYMENT_REQUEST_VERSION",
                () -> service.submit(1L, 3L, null)
        );
        assertCode(
                "INVALID_PAYMENT_REQUEST_VERSION",
                () -> service.submit(1L, 3L, -1L)
        );

        verify(paymentRequestRepository, times(2)).findById(1L);
        verify(departmentSupervisorRepository, never())
                .findEffectiveSupervisors(any(), any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    private PaymentRequest draftPaymentRequest() {
        PaymentRequest paymentRequest = new PaymentRequest();
        setField(paymentRequest, "id", 1L);
        setField(paymentRequest, "version", 0L);
        paymentRequest.setRequestNo("PAY-20260731-000001");
        paymentRequest.setApplicant(applicant());
        paymentRequest.setDepartment(department());
        paymentRequest.setApprovalStatus(ApprovalStatus.DRAFT);
        paymentRequest.setPaymentStatus(PaymentStatus.UNPAID);
        return paymentRequest;
    }

    private Department department() {
        Department department = new Department();
        setField(department, "id", 10L);
        department.setCode("D001");
        department.setName("部門");
        department.setActive(true);
        return department;
    }

    private AppUser applicant() {
        AppUser applicant = new AppUser();
        setField(applicant, "id", 3L);
        applicant.setDisplayName("申請人");
        applicant.setActive(true);
        return applicant;
    }

    private DepartmentSupervisor effectiveSupervisor(boolean active) {
        DepartmentSupervisor departmentSupervisor = new DepartmentSupervisor();
        setField(departmentSupervisor, "id", 20L);
        departmentSupervisor.setDepartment(department());
        AppUser supervisor = new AppUser();
        setField(supervisor, "id", 2L);
        supervisor.setDisplayName("主管 A");
        supervisor.setActive(active);
        departmentSupervisor.setSupervisor(supervisor);
        departmentSupervisor.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        departmentSupervisor.setActive(true);
        return departmentSupervisor;
    }

    private void stubSaveAndFlushIncrementsVersion() {
        when(paymentRequestRepository.saveAndFlush(any(PaymentRequest.class)))
                .thenAnswer(invocation -> {
                    PaymentRequest paymentRequest = invocation.getArgument(0);
                    setField(paymentRequest, "version", 1L);
                    return paymentRequest;
                });
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
