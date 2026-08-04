package tw.com.jsgcpa.paymentapproval.payment.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import tw.com.jsgcpa.paymentapproval.approval.entity.ApprovalHistory;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalAction;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.approval.repository.ApprovalHistoryRepository;
import tw.com.jsgcpa.paymentapproval.attachment.storage.AttachmentStorageService;
import tw.com.jsgcpa.paymentapproval.attachment.storage.StoredAttachmentFile;
import tw.com.jsgcpa.paymentapproval.attachment.validation.AttachmentFileValidator;
import tw.com.jsgcpa.paymentapproval.attachment.validation.ValidatedAttachmentFile;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.RecordPaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.RecordPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestAttachmentRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@Service
@Transactional
public class RecordPaymentService {

    private static final Logger log = LoggerFactory.getLogger(
            RecordPaymentService.class
    );
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final PaymentRequestRepository paymentRequestRepository;
    private final AppUserRepository appUserRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final PaymentRequestAttachmentRepository attachmentRepository;
    private final AttachmentFileValidator attachmentFileValidator;
    private final AttachmentStorageService attachmentStorageService;
    private final Clock clock;

    @Autowired
    public RecordPaymentService(
            PaymentRequestRepository paymentRequestRepository,
            AppUserRepository appUserRepository,
            ApprovalHistoryRepository approvalHistoryRepository,
            PaymentRequestAttachmentRepository attachmentRepository,
            AttachmentFileValidator attachmentFileValidator,
            AttachmentStorageService attachmentStorageService
    ) {
        this(
                paymentRequestRepository,
                appUserRepository,
                approvalHistoryRepository,
                attachmentRepository,
                attachmentFileValidator,
                attachmentStorageService,
                Clock.system(BUSINESS_ZONE)
        );
    }

    RecordPaymentService(
            PaymentRequestRepository paymentRequestRepository,
            AppUserRepository appUserRepository,
            ApprovalHistoryRepository approvalHistoryRepository,
            PaymentRequestAttachmentRepository attachmentRepository,
            AttachmentFileValidator attachmentFileValidator,
            AttachmentStorageService attachmentStorageService,
            Clock clock
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.appUserRepository = appUserRepository;
        this.approvalHistoryRepository = approvalHistoryRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentFileValidator = attachmentFileValidator;
        this.attachmentStorageService = attachmentStorageService;
        this.clock = clock;
    }

    public RecordPaymentResponse recordPayment(
            Long paymentRequestId,
            RecordPaymentRequest request,
            MultipartFile paymentProofFile,
            Long authenticatedUserId
    ) {
        validatePaymentRequestId(paymentRequestId);
        validateAuthenticatedUserId(authenticatedUserId);
        validateRequest(request);

        PaymentRequest paymentRequest = paymentRequestRepository
                .findById(paymentRequestId)
                .orElseThrow(() -> businessError(
                        "PAYMENT_REQUEST_NOT_FOUND",
                        "Payment request not found: " + paymentRequestId
                ));

        if (!request.version().equals(paymentRequest.getVersion())) {
            throw businessError(
                    "PAYMENT_REQUEST_VERSION_CONFLICT",
                    "Payment request version conflict for id " + paymentRequestId
            );
        }
        if (paymentRequest.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw businessError(
                    "PAYMENT_REQUEST_NOT_APPROVED",
                    "Payment request is not APPROVED"
            );
        }
        if (paymentRequest.getPaymentStatus() == PaymentStatus.PAID) {
            throw businessError(
                    "PAYMENT_REQUEST_ALREADY_PAID",
                    "Payment request is already PAID"
            );
        }
        if (paymentProofFile == null) {
            throw businessError(
                    "PAYMENT_PROOF_REQUIRED",
                    "Payment proof file is required"
            );
        }
        if (attachmentRepository.existsByPaymentRequest_IdAndAttachmentType(
                paymentRequestId,
                AttachmentType.PAYMENT_PROOF
        )) {
            throw businessError(
                    "PAYMENT_PROOF_ALREADY_EXISTS",
                    "Payment proof already exists"
            );
        }

        AppUser paidBy = appUserRepository.findById(authenticatedUserId)
                .orElseThrow(() -> businessError(
                        "PAID_BY_NOT_FOUND",
                        "Payment user not found: " + authenticatedUserId
                ));
        if (!Boolean.TRUE.equals(paidBy.getActive())) {
            throw businessError(
                    "PAID_BY_INACTIVE",
                    "Payment user is inactive: " + authenticatedUserId
            );
        }

        ValidatedAttachmentFile validatedFile = attachmentFileValidator
                .validate(paymentProofFile);
        StoredAttachmentFile storedFile = attachmentStorageService.store(
                paymentRequestId,
                validatedFile
        );
        AtomicBoolean cleaned = new AtomicBoolean(false);
        AtomicReference<RuntimeException> operationFailure =
                new AtomicReference<>();
        Runnable rollbackCleanup = () -> cleanupStoredFile(
                storedFile.relativeStoragePath(),
                cleaned,
                operationFailure.get()
        );
        registerRollbackCleanup(rollbackCleanup);

        try {
            PaymentRequestAttachment attachment = new PaymentRequestAttachment();
            attachment.setPaymentRequest(paymentRequest);
            attachment.setUploadedBy(paidBy);
            attachment.setAttachmentType(AttachmentType.PAYMENT_PROOF);
            attachment.setOriginalFilename(validatedFile.safeOriginalFilename());
            attachment.setStoredFilename(storedFile.storedFilename());
            attachment.setStoragePath(storedFile.relativeStoragePath());
            attachment.setContentType(storedFile.contentType());
            attachment.setFileSize(storedFile.fileSize());
            attachmentRepository.saveAndFlush(attachment);

            ApprovalStatus fromApprovalStatus = paymentRequest.getApprovalStatus();
            PaymentStatus fromPaymentStatus = paymentRequest.getPaymentStatus();
            paymentRequest.setApprovalStatus(ApprovalStatus.APPROVED);
            paymentRequest.setPaymentStatus(PaymentStatus.PAID);
            paymentRequest.setPaidAt(request.paidAt());
            paymentRequest.setPaidBy(paidBy);
            paymentRequest.setPaymentMethod(request.paymentMethod());
            paymentRequest.setPaymentReference(normalize(request.paymentReference()));
            paymentRequest.setPaymentNote(normalize(request.paymentNote()));

            PaymentRequest savedPaymentRequest = paymentRequestRepository
                    .saveAndFlush(paymentRequest);
            if (savedPaymentRequest == null) {
                savedPaymentRequest = paymentRequest;
            }

            OffsetDateTime recordedAt = OffsetDateTime.now(clock);
            ApprovalHistory history = new ApprovalHistory();
            history.setPaymentRequest(savedPaymentRequest);
            history.setActor(paidBy);
            history.setAction(ApprovalAction.PAYMENT_RECORDED);
            history.setFromApprovalStatus(fromApprovalStatus);
            history.setToApprovalStatus(ApprovalStatus.APPROVED);
            history.setFromPaymentStatus(fromPaymentStatus);
            history.setToPaymentStatus(PaymentStatus.PAID);
            history.setComment(normalize(request.paymentNote()));
            history.setActedAt(recordedAt);
            approvalHistoryRepository.save(history);

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
        } catch (OptimisticLockingFailureException exception) {
            PaymentDraftBusinessException conflict = businessError(
                    "PAYMENT_REQUEST_VERSION_CONFLICT",
                    "Payment request version conflict: " + paymentRequestId
            );
            operationFailure.set(conflict);
            cleanupStoredFile(
                    storedFile.relativeStoragePath(),
                    cleaned,
                    conflict
            );
            throw conflict;
        } catch (RuntimeException exception) {
            operationFailure.set(exception);
            cleanupStoredFile(
                    storedFile.relativeStoragePath(),
                    cleaned,
                    exception
            );
            throw exception;
        }
    }

    /**
     * Legacy JSON contract adapter. It intentionally cannot record a payment
     * because a PAYMENT_PROOF is mandatory for every payment registration.
     */
    @Deprecated
    public RecordPaymentResponse recordPayment(
            Long paymentRequestId,
            Long authenticatedUserId,
            RecordPaymentRequest request
    ) {
        throw businessError(
                "PAYMENT_PROOF_REQUIRED",
                "Payment proof file is required"
        );
    }

    private void registerRollbackCleanup(
            Runnable cleanup
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            cleanup.run();
                        }
                    }
                }
        );
    }

    private void cleanupStoredFile(
            String storagePath,
            AtomicBoolean cleaned,
            RuntimeException originalFailure
    ) {
        if (!cleaned.compareAndSet(false, true)) {
            return;
        }
        try {
            attachmentStorageService.delete(storagePath);
        } catch (RuntimeException cleanupFailure) {
            log.error(
                    "Unable to clean up payment proof storage path {}",
                    storagePath,
                    cleanupFailure
            );
            if (originalFailure != null) {
                originalFailure.addSuppressed(cleanupFailure);
            }
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

    private void validateAuthenticatedUserId(Long authenticatedUserId) {
        if (authenticatedUserId == null || authenticatedUserId <= 0) {
            throw businessError(
                    "INVALID_PAID_BY_ID",
                    "Authenticated user id must be greater than zero"
            );
        }
    }

    private void validateRequest(RecordPaymentRequest request) {
        if (request == null) {
            throw businessError(
                    "INVALID_PAYMENT_REQUEST",
                    "Payment request input must not be null"
            );
        }
        if (request.version() == null || request.version() < 0) {
            throw businessError(
                    "INVALID_PAYMENT_REQUEST_VERSION",
                    "Payment request version must be zero or greater"
            );
        }
        if (request.paidAt() == null) {
            throw businessError(
                    "INVALID_PAYMENT_DATE",
                    "Payment date must not be null"
            );
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private PaymentDraftBusinessException businessError(
            String code,
            String message
    ) {
        return new PaymentDraftBusinessException(code, message);
    }
}
