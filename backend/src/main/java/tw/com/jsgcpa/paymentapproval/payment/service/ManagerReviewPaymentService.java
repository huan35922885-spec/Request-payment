package tw.com.jsgcpa.paymentapproval.payment.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
@Transactional
public class ManagerReviewPaymentService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final PaymentRequestRepository paymentRequestRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final Clock clock;

    @Autowired
    public ManagerReviewPaymentService(
            PaymentRequestRepository paymentRequestRepository,
            ApprovalHistoryRepository approvalHistoryRepository
    ) {
        this(
                paymentRequestRepository,
                approvalHistoryRepository,
                Clock.system(BUSINESS_ZONE)
        );
    }

    ManagerReviewPaymentService(
            PaymentRequestRepository paymentRequestRepository,
            ApprovalHistoryRepository approvalHistoryRepository,
            Clock clock
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.approvalHistoryRepository = approvalHistoryRepository;
        this.clock = clock;
    }

    public ManagerReviewPaymentResponse approve(
            Long paymentRequestId,
            Long managerId,
            Long expectedVersion,
            String comment
    ) {
        PaymentRequest paymentRequest = validateReviewRequest(
                paymentRequestId,
                managerId,
                expectedVersion
        );
        AppUser manager = paymentRequest.getSupervisorSnapshot();
        PaymentStatus paymentStatus = paymentRequest.getPaymentStatus();
        OffsetDateTime actedAt = OffsetDateTime.now(clock);

        paymentRequest.setApprovalStatus(ApprovalStatus.PENDING_CASHIER);
        PaymentRequest savedPaymentRequest = savePaymentRequest(
                paymentRequestId,
                paymentRequest
        );

        ApprovalHistory approvalHistory = new ApprovalHistory();
        approvalHistory.setPaymentRequest(savedPaymentRequest);
        approvalHistory.setActor(manager);
        approvalHistory.setAction(ApprovalAction.MANAGER_APPROVE);
        approvalHistory.setFromApprovalStatus(ApprovalStatus.PENDING_MANAGER);
        approvalHistory.setToApprovalStatus(ApprovalStatus.PENDING_CASHIER);
        approvalHistory.setFromPaymentStatus(paymentStatus);
        approvalHistory.setToPaymentStatus(paymentStatus);
        approvalHistory.setComment(comment);
        approvalHistory.setActedAt(actedAt);
        approvalHistoryRepository.save(approvalHistory);

        return new ManagerReviewPaymentResponse(
                savedPaymentRequest.getId(),
                savedPaymentRequest.getRequestNo(),
                ApprovalAction.MANAGER_APPROVE,
                savedPaymentRequest.getApprovalStatus(),
                savedPaymentRequest.getPaymentStatus(),
                manager.getId(),
                manager.getDisplayName(),
                comment,
                actedAt,
                savedPaymentRequest.getVersion()
        );
    }

    public ManagerReviewPaymentResponse reject(
            Long paymentRequestId,
            Long managerId,
            Long expectedVersion,
            String comment
    ) {
        PaymentRequest paymentRequest = validateReviewRequest(
                paymentRequestId,
                managerId,
                expectedVersion
        );
        AppUser manager = paymentRequest.getSupervisorSnapshot();
        PaymentStatus paymentStatus = paymentRequest.getPaymentStatus();
        OffsetDateTime actedAt = OffsetDateTime.now(clock);

        paymentRequest.setApprovalStatus(ApprovalStatus.REJECTED_CLOSED);
        paymentRequest.setRejectedAt(actedAt);
        paymentRequest.setClosedAt(actedAt);
        PaymentRequest savedPaymentRequest = savePaymentRequest(
                paymentRequestId,
                paymentRequest
        );

        ApprovalHistory approvalHistory = new ApprovalHistory();
        approvalHistory.setPaymentRequest(savedPaymentRequest);
        approvalHistory.setActor(manager);
        approvalHistory.setAction(ApprovalAction.MANAGER_REJECT);
        approvalHistory.setFromApprovalStatus(ApprovalStatus.PENDING_MANAGER);
        approvalHistory.setToApprovalStatus(ApprovalStatus.REJECTED_CLOSED);
        approvalHistory.setFromPaymentStatus(paymentStatus);
        approvalHistory.setToPaymentStatus(paymentStatus);
        approvalHistory.setComment(comment);
        approvalHistory.setActedAt(actedAt);
        approvalHistoryRepository.save(approvalHistory);

        return new ManagerReviewPaymentResponse(
                savedPaymentRequest.getId(),
                savedPaymentRequest.getRequestNo(),
                ApprovalAction.MANAGER_REJECT,
                savedPaymentRequest.getApprovalStatus(),
                savedPaymentRequest.getPaymentStatus(),
                manager.getId(),
                manager.getDisplayName(),
                comment,
                actedAt,
                savedPaymentRequest.getVersion()
        );
    }

    private PaymentRequest validateReviewRequest(
            Long paymentRequestId,
            Long managerId,
            Long expectedVersion
    ) {
        validatePaymentRequestId(paymentRequestId);
        validateManagerId(managerId);
        validateExpectedVersion(expectedVersion);

        PaymentRequest paymentRequest = paymentRequestRepository
                .findById(paymentRequestId)
                .orElseThrow(() -> businessError(
                        "PAYMENT_REQUEST_NOT_FOUND",
                        "Payment request not found: " + paymentRequestId
                ));

        if (!expectedVersion.equals(paymentRequest.getVersion())) {
            throw businessError(
                    "PAYMENT_REQUEST_VERSION_CONFLICT",
                    "Payment request version conflict for id " + paymentRequestId
                            + ": expectedVersion=" + expectedVersion
                            + ", currentVersion=" + paymentRequest.getVersion()
            );
        }

        if (paymentRequest.getApprovalStatus()
                != ApprovalStatus.PENDING_MANAGER) {
            throw businessError(
                    "PAYMENT_REQUEST_NOT_PENDING_MANAGER",
                    "Payment request is not PENDING_MANAGER: "
                            + paymentRequest.getApprovalStatus()
            );
        }

        AppUser supervisorSnapshot = paymentRequest.getSupervisorSnapshot();
        if (supervisorSnapshot == null) {
            throw businessError(
                    "SUPERVISOR_SNAPSHOT_MISSING",
                    "Payment request supervisor snapshot is missing: "
                            + paymentRequestId
            );
        }

        if (!managerId.equals(supervisorSnapshot.getId())) {
            throw businessError(
                    "MANAGER_NOT_AUTHORIZED",
                    "Manager is not authorized for payment request: "
                            + paymentRequestId
            );
        }

        return paymentRequest;
    }

    private PaymentRequest savePaymentRequest(
            Long paymentRequestId,
            PaymentRequest paymentRequest
    ) {
        try {
            PaymentRequest savedPaymentRequest = paymentRequestRepository
                    .saveAndFlush(paymentRequest);
            return savedPaymentRequest == null
                    ? paymentRequest
                    : savedPaymentRequest;
        } catch (OptimisticLockingFailureException exception) {
            throw businessError(
                    "PAYMENT_REQUEST_VERSION_CONFLICT",
                    "Payment request version conflict: " + paymentRequestId
            );
        }
    }

    private void validatePaymentRequestId(Long paymentRequestId) {
        if (paymentRequestId == null || paymentRequestId <= 0) {
            throw businessError(
                    "INVALID_PAYMENT_REQUEST_ID",
                    "Payment request id must be greater than zero"
            );
        }
    }

    private void validateManagerId(Long managerId) {
        if (managerId == null || managerId <= 0) {
            throw businessError(
                    "INVALID_MANAGER_ID",
                    "Manager id must be greater than zero"
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

    private PaymentDraftBusinessException businessError(
            String code,
            String message
    ) {
        return new PaymentDraftBusinessException(code, message);
    }
}
