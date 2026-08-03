package tw.com.jsgcpa.paymentapproval.payment.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
public class SubmitPaymentDraftService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final PaymentRequestRepository paymentRequestRepository;
    private final DepartmentSupervisorRepository departmentSupervisorRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final Clock clock;

    @Autowired
    public SubmitPaymentDraftService(
            PaymentRequestRepository paymentRequestRepository,
            DepartmentSupervisorRepository departmentSupervisorRepository,
            ApprovalHistoryRepository approvalHistoryRepository
    ) {
        this(
                paymentRequestRepository,
                departmentSupervisorRepository,
                approvalHistoryRepository,
                Clock.system(BUSINESS_ZONE)
        );
    }

    SubmitPaymentDraftService(
            PaymentRequestRepository paymentRequestRepository,
            DepartmentSupervisorRepository departmentSupervisorRepository,
            ApprovalHistoryRepository approvalHistoryRepository,
            Clock clock
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.departmentSupervisorRepository = departmentSupervisorRepository;
        this.approvalHistoryRepository = approvalHistoryRepository;
        this.clock = clock;
    }

    @Transactional
    public SubmitPaymentDraftResponse submit(
            Long paymentRequestId,
            Long authenticatedUserId,
            Long expectedVersion
    ) {
        validatePaymentRequestId(paymentRequestId);
        validateAuthenticatedUserId(authenticatedUserId);

        PaymentRequest paymentRequest = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> businessError(
                        "PAYMENT_REQUEST_NOT_FOUND",
                        "Payment request not found: " + paymentRequestId
                ));

        validateOwnership(paymentRequest, authenticatedUserId);
        validateExpectedVersion(expectedVersion);
        validateVersion(paymentRequestId, expectedVersion, paymentRequest.getVersion());
        validateDraftStatus(paymentRequest.getApprovalStatus());

        Department department = paymentRequest.getDepartment();
        if (department == null) {
            throw businessError(
                    "PAYMENT_REQUEST_DEPARTMENT_MISSING",
                    "Payment request department is missing: " + paymentRequestId
            );
        }

        LocalDate effectiveDate = LocalDate.now(clock);
        List<DepartmentSupervisor> supervisors =
                departmentSupervisorRepository.findEffectiveSupervisors(
                        department.getId(),
                        effectiveDate
                );
        DepartmentSupervisor departmentSupervisor = resolveSupervisor(supervisors);
        AppUser supervisor = departmentSupervisor.getSupervisor();
        if (supervisor == null) {
            throw businessError(
                    "SUPERVISOR_NOT_FOUND",
                    "Effective supervisor not found for department: "
                            + department.getId()
            );
        }
        if (!Boolean.TRUE.equals(supervisor.getActive())) {
            throw businessError(
                    "SUPERVISOR_INACTIVE",
                    "Effective supervisor is inactive: " + supervisor.getId()
            );
        }

        ApprovalStatus fromApprovalStatus = paymentRequest.getApprovalStatus();
        PaymentStatus fromPaymentStatus = paymentRequest.getPaymentStatus();
        OffsetDateTime submittedAt = OffsetDateTime.now(clock);

        paymentRequest.setSupervisorSnapshot(supervisor);
        paymentRequest.setApprovalStatus(ApprovalStatus.PENDING_MANAGER);
        paymentRequest.setSubmittedAt(submittedAt);

        PaymentRequest savedPaymentRequest;
        try {
            savedPaymentRequest = paymentRequestRepository.saveAndFlush(paymentRequest);
        } catch (OptimisticLockingFailureException exception) {
            throw businessError(
                    "PAYMENT_REQUEST_VERSION_CONFLICT",
                    "Payment request version conflict: " + paymentRequestId
            );
        }
        if (savedPaymentRequest == null) {
            savedPaymentRequest = paymentRequest;
        }

        ApprovalHistory approvalHistory = new ApprovalHistory();
        approvalHistory.setPaymentRequest(savedPaymentRequest);
        approvalHistory.setActor(savedPaymentRequest.getApplicant());
        approvalHistory.setAction(ApprovalAction.SUBMIT);
        approvalHistory.setFromApprovalStatus(fromApprovalStatus);
        approvalHistory.setToApprovalStatus(savedPaymentRequest.getApprovalStatus());
        approvalHistory.setFromPaymentStatus(fromPaymentStatus);
        approvalHistory.setToPaymentStatus(savedPaymentRequest.getPaymentStatus());
        approvalHistory.setComment(null);
        approvalHistory.setActedAt(submittedAt);
        approvalHistoryRepository.save(approvalHistory);

        return toResponse(savedPaymentRequest, supervisor);
    }

    private void validatePaymentRequestId(Long paymentRequestId) {
        if (paymentRequestId == null || paymentRequestId <= 0) {
            throw businessError(
                    "INVALID_PAYMENT_REQUEST_ID",
                    "Payment request id must be greater than zero"
            );
        }
    }

    private void validateExpectedVersion(Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw businessError(
                    "INVALID_PAYMENT_REQUEST_VERSION",
                    "Payment request version must be zero or greater"
            );
        }
    }

    private void validateAuthenticatedUserId(Long authenticatedUserId) {
        if (authenticatedUserId == null || authenticatedUserId <= 0) {
            throw businessError(
                    "INVALID_AUTHENTICATED_USER_ID",
                    "Authenticated user id must be greater than zero"
            );
        }
    }

    private void validateOwnership(
            PaymentRequest paymentRequest,
            Long authenticatedUserId
    ) {
        AppUser applicant = paymentRequest.getApplicant();
        if (applicant == null
                || !Objects.equals(applicant.getId(), authenticatedUserId)) {
            throw businessError(
                    "PAYMENT_REQUEST_SUBMIT_FORBIDDEN",
                    "只有原申請人可以送出此請款草稿"
            );
        }
    }

    private void validateVersion(
            Long paymentRequestId,
            Long expectedVersion,
            Long currentVersion
    ) {
        if (!expectedVersion.equals(currentVersion)) {
            throw businessError(
                    "PAYMENT_REQUEST_VERSION_CONFLICT",
                    "Payment request version conflict for id " + paymentRequestId
                            + ": expectedVersion=" + expectedVersion
                            + ", currentVersion=" + currentVersion
            );
        }
    }

    private void validateDraftStatus(ApprovalStatus approvalStatus) {
        if (approvalStatus != ApprovalStatus.DRAFT) {
            throw businessError(
                    "PAYMENT_REQUEST_NOT_DRAFT",
                    "Payment request is not DRAFT: " + approvalStatus
            );
        }
    }

    private DepartmentSupervisor resolveSupervisor(
            List<DepartmentSupervisor> supervisors
    ) {
        if (supervisors == null || supervisors.isEmpty()) {
            throw businessError(
                    "SUPERVISOR_NOT_FOUND",
                    "No effective supervisor found"
            );
        }
        if (supervisors.size() > 1) {
            throw businessError(
                    "SUPERVISOR_CONFLICT",
                    "Multiple effective supervisors found"
            );
        }
        return supervisors.get(0);
    }

    private SubmitPaymentDraftResponse toResponse(
            PaymentRequest paymentRequest,
            AppUser supervisor
    ) {
        return new SubmitPaymentDraftResponse(
                paymentRequest.getId(),
                paymentRequest.getRequestNo(),
                paymentRequest.getApprovalStatus(),
                paymentRequest.getPaymentStatus(),
                supervisor.getId(),
                supervisor.getDisplayName(),
                paymentRequest.getSubmittedAt(),
                paymentRequest.getVersion()
        );
    }

    private PaymentDraftBusinessException businessError(
            String code,
            String message
    ) {
        return new PaymentDraftBusinessException(code, message);
    }
}
