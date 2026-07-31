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
import tw.com.jsgcpa.paymentapproval.payment.dto.response.RecordPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@Service
@Transactional
public class RecordPaymentService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final PaymentRequestRepository paymentRequestRepository;
    private final AppUserRepository appUserRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final Clock clock;

    @Autowired
    public RecordPaymentService(
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

    RecordPaymentService(
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

    public RecordPaymentResponse record(
            Long paymentRequestId,
            Long paidById,
            Long expectedVersion,
            OffsetDateTime paidAt,
            PaymentMethod paymentMethod,
            String paymentReference,
            String paymentNote
    ) {
        validatePaymentRequestId(paymentRequestId);
        validatePaidById(paidById);
        validateExpectedVersion(expectedVersion);
        validatePaidAt(paidAt);

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

        if (paymentRequest.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw businessError(
                    "PAYMENT_REQUEST_NOT_APPROVED",
                    "Payment request is not APPROVED: "
                            + paymentRequest.getApprovalStatus()
            );
        }

        if (paymentRequest.getPaymentStatus() == PaymentStatus.PAID) {
            throw businessError(
                    "PAYMENT_REQUEST_ALREADY_PAID",
                    "Payment request is already PAID: " + paymentRequestId
            );
        }

        AppUser paidBy = appUserRepository.findById(paidById)
                .orElseThrow(() -> businessError(
                        "PAID_BY_NOT_FOUND",
                        "Payment user not found: " + paidById
                ));

        if (!Boolean.TRUE.equals(paidBy.getActive())) {
            throw businessError(
                    "PAID_BY_INACTIVE",
                    "Payment user is inactive: " + paidById
            );
        }

        OffsetDateTime recordedAt = OffsetDateTime.now(clock);
        ApprovalStatus fromApprovalStatus = paymentRequest.getApprovalStatus();
        PaymentStatus fromPaymentStatus = paymentRequest.getPaymentStatus();

        paymentRequest.setApprovalStatus(ApprovalStatus.APPROVED);
        paymentRequest.setPaymentStatus(PaymentStatus.PAID);
        paymentRequest.setPaidAt(paidAt);
        paymentRequest.setPaidBy(paidBy);
        paymentRequest.setPaymentMethod(paymentMethod);
        paymentRequest.setPaymentReference(paymentReference);
        paymentRequest.setPaymentNote(paymentNote);

        PaymentRequest savedPaymentRequest = savePaymentRequest(
                paymentRequestId,
                paymentRequest
        );

        ApprovalHistory approvalHistory = new ApprovalHistory();
        approvalHistory.setPaymentRequest(savedPaymentRequest);
        approvalHistory.setActor(paidBy);
        approvalHistory.setAction(ApprovalAction.PAYMENT_RECORDED);
        approvalHistory.setFromApprovalStatus(fromApprovalStatus);
        approvalHistory.setToApprovalStatus(ApprovalStatus.APPROVED);
        approvalHistory.setFromPaymentStatus(fromPaymentStatus);
        approvalHistory.setToPaymentStatus(PaymentStatus.PAID);
        approvalHistory.setComment(paymentNote);
        approvalHistory.setActedAt(recordedAt);
        approvalHistoryRepository.save(approvalHistory);

        return new RecordPaymentResponse(
                savedPaymentRequest.getId(),
                savedPaymentRequest.getRequestNo(),
                ApprovalAction.PAYMENT_RECORDED,
                savedPaymentRequest.getApprovalStatus(),
                savedPaymentRequest.getPaymentStatus(),
                paidBy.getId(),
                paidBy.getDisplayName(),
                savedPaymentRequest.getPaidAt(),
                savedPaymentRequest.getPaymentMethod(),
                savedPaymentRequest.getPaymentReference(),
                savedPaymentRequest.getPaymentNote(),
                recordedAt,
                savedPaymentRequest.getVersion()
        );
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

    private void validatePaidById(Long paidById) {
        if (paidById == null || paidById <= 0) {
            throw businessError(
                    "INVALID_PAID_BY_ID",
                    "Paid by id must be greater than zero"
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

    private void validatePaidAt(OffsetDateTime paidAt) {
        if (paidAt == null) {
            throw businessError(
                    "INVALID_PAYMENT_DATE",
                    "Payment date must not be null"
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
