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
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.CashierReviewPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@Service
@Transactional
public class CashierReviewPaymentService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final PaymentRequestRepository paymentRequestRepository;
    private final AppUserRepository appUserRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final Clock clock;

    @Autowired
    public CashierReviewPaymentService(
            PaymentRequestRepository paymentRequestRepository,
            AppUserRepository appUserRepository,
            ApprovalHistoryRepository approvalHistoryRepository
    ) {
        this(
                paymentRequestRepository,
                appUserRepository,
                approvalHistoryRepository,
                Clock.system(BUSINESS_ZONE)
        );
    }

    CashierReviewPaymentService(
            PaymentRequestRepository paymentRequestRepository,
            AppUserRepository appUserRepository,
            ApprovalHistoryRepository approvalHistoryRepository,
            Clock clock
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.appUserRepository = appUserRepository;
        this.approvalHistoryRepository = approvalHistoryRepository;
        this.clock = clock;
    }

    public CashierReviewPaymentResponse approve(
            Long paymentRequestId,
            Long cashierId,
            Long expectedVersion,
            String comment
    ) {
        ReviewContext context = validateReviewRequest(
                paymentRequestId,
                cashierId,
                expectedVersion
        );
        PaymentRequest paymentRequest = context.paymentRequest();
        AppUser cashier = context.cashier();
        PaymentStatus paymentStatus = paymentRequest.getPaymentStatus();
        OffsetDateTime actedAt = OffsetDateTime.now(clock);

        paymentRequest.setApprovalStatus(ApprovalStatus.APPROVED);
        paymentRequest.setApprovedAt(actedAt);
        paymentRequest.setApprovedBy(cashier);

        PaymentRequest savedPaymentRequest = savePaymentRequest(
                paymentRequestId,
                paymentRequest
        );

        ApprovalHistory approvalHistory = new ApprovalHistory();
        approvalHistory.setPaymentRequest(savedPaymentRequest);
        approvalHistory.setActor(cashier);
        approvalHistory.setAction(ApprovalAction.CASHIER_APPROVE);
        approvalHistory.setFromApprovalStatus(ApprovalStatus.PENDING_CASHIER);
        approvalHistory.setToApprovalStatus(ApprovalStatus.APPROVED);
        approvalHistory.setFromPaymentStatus(paymentStatus);
        approvalHistory.setToPaymentStatus(paymentStatus);
        approvalHistory.setComment(comment);
        approvalHistory.setActedAt(actedAt);
        approvalHistoryRepository.save(approvalHistory);

        return toResponse(
                savedPaymentRequest,
                cashier,
                ApprovalAction.CASHIER_APPROVE,
                comment,
                actedAt
        );
    }

    public CashierReviewPaymentResponse reject(
            Long paymentRequestId,
            Long cashierId,
            Long expectedVersion,
            String comment
    ) {
        ReviewContext context = validateReviewRequest(
                paymentRequestId,
                cashierId,
                expectedVersion
        );
        PaymentRequest paymentRequest = context.paymentRequest();
        AppUser cashier = context.cashier();
        PaymentStatus paymentStatus = paymentRequest.getPaymentStatus();
        OffsetDateTime actedAt = OffsetDateTime.now(clock);

        paymentRequest.setApprovalStatus(ApprovalStatus.REJECTED_CLOSED);
        paymentRequest.setApprovedAt(null);
        paymentRequest.setApprovedBy(null);
        paymentRequest.setRejectedAt(actedAt);
        paymentRequest.setClosedAt(actedAt);

        PaymentRequest savedPaymentRequest = savePaymentRequest(
                paymentRequestId,
                paymentRequest
        );

        ApprovalHistory approvalHistory = new ApprovalHistory();
        approvalHistory.setPaymentRequest(savedPaymentRequest);
        approvalHistory.setActor(cashier);
        approvalHistory.setAction(ApprovalAction.CASHIER_REJECT);
        approvalHistory.setFromApprovalStatus(ApprovalStatus.PENDING_CASHIER);
        approvalHistory.setToApprovalStatus(ApprovalStatus.REJECTED_CLOSED);
        approvalHistory.setFromPaymentStatus(paymentStatus);
        approvalHistory.setToPaymentStatus(paymentStatus);
        approvalHistory.setComment(comment);
        approvalHistory.setActedAt(actedAt);
        approvalHistoryRepository.save(approvalHistory);

        return toResponse(
                savedPaymentRequest,
                cashier,
                ApprovalAction.CASHIER_REJECT,
                comment,
                actedAt
        );
    }

    private ReviewContext validateReviewRequest(
            Long paymentRequestId,
            Long cashierId,
            Long expectedVersion
    ) {
        validatePaymentRequestId(paymentRequestId);
        validateCashierId(cashierId);
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
                != ApprovalStatus.PENDING_CASHIER) {
            throw businessError(
                    "PAYMENT_REQUEST_NOT_PENDING_CASHIER",
                    "Payment request is not PENDING_CASHIER: "
                            + paymentRequest.getApprovalStatus()
            );
        }

        AppUser cashier = appUserRepository.findById(cashierId)
                .orElseThrow(() -> businessError(
                        "CASHIER_NOT_FOUND",
                        "Cashier not found: " + cashierId
                ));

        if (!Boolean.TRUE.equals(cashier.getActive())) {
            throw businessError(
                    "CASHIER_INACTIVE",
                    "Cashier is inactive: " + cashierId
            );
        }

        return new ReviewContext(paymentRequest, cashier);
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

    private CashierReviewPaymentResponse toResponse(
            PaymentRequest paymentRequest,
            AppUser cashier,
            ApprovalAction action,
            String comment,
            OffsetDateTime actedAt
    ) {
        return new CashierReviewPaymentResponse(
                paymentRequest.getId(),
                paymentRequest.getRequestNo(),
                action,
                paymentRequest.getApprovalStatus(),
                paymentRequest.getPaymentStatus(),
                cashier.getId(),
                cashier.getDisplayName(),
                comment,
                actedAt,
                paymentRequest.getVersion()
        );
    }

    private void validatePaymentRequestId(Long paymentRequestId) {
        if (paymentRequestId == null || paymentRequestId <= 0) {
            throw businessError(
                    "INVALID_PAYMENT_REQUEST_ID",
                    "Payment request id must be greater than zero"
            );
        }
    }

    private void validateCashierId(Long cashierId) {
        if (cashierId == null || cashierId <= 0) {
            throw businessError(
                    "INVALID_CASHIER_ID",
                    "Cashier id must be greater than zero"
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

    private record ReviewContext(
            PaymentRequest paymentRequest,
            AppUser cashier
    ) {
    }
}
