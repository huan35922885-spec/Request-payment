package tw.com.jsgcpa.paymentapproval.payment.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@Service
@Transactional
public class RecordPaymentService {

    private static final Logger log = LoggerFactory.getLogger(RecordPaymentService.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final PaymentRequestRepository paymentRequestRepository;
    private final AppUserRepository appUserRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final PaymentMaintenanceService paymentMaintenanceService;
    private final AttachmentStorageService attachmentStorageService;
    private final TransactionRollbackCleanupRegistrar cleanupRegistrar;
    private final Clock clock;

    @Autowired
    public RecordPaymentService(
            PaymentRequestRepository paymentRequestRepository,
            AppUserRepository appUserRepository,
            ApprovalHistoryRepository approvalHistoryRepository,
            PaymentMaintenanceService paymentMaintenanceService,
            AttachmentStorageService attachmentStorageService,
            TransactionRollbackCleanupRegistrar cleanupRegistrar
    ) {
        this(
                paymentRequestRepository,
                appUserRepository,
                approvalHistoryRepository,
                paymentMaintenanceService,
                attachmentStorageService,
                cleanupRegistrar,
                Clock.system(BUSINESS_ZONE)
        );
    }

    RecordPaymentService(
            PaymentRequestRepository paymentRequestRepository,
            AppUserRepository appUserRepository,
            ApprovalHistoryRepository approvalHistoryRepository,
            PaymentMaintenanceService paymentMaintenanceService,
            AttachmentStorageService attachmentStorageService,
            TransactionRollbackCleanupRegistrar cleanupRegistrar,
            Clock clock
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.appUserRepository = appUserRepository;
        this.approvalHistoryRepository = approvalHistoryRepository;
        this.paymentMaintenanceService = paymentMaintenanceService;
        this.attachmentStorageService = attachmentStorageService;
        this.cleanupRegistrar = cleanupRegistrar;
        this.clock = clock;
    }

    public RecordPaymentResponse recordPayment(
            Long paymentRequestId,
            RecordPaymentRequest request,
            List<MultipartFile> paymentProofFiles,
            Long authenticatedUserId
    ) {
        List<MultipartFile> files = normalizeProofFiles(paymentProofFiles);
        if (files.isEmpty()) {
            throw businessError(
                    "PAYMENT_PROOF_REQUIRED",
                    "Payment proof file is required"
            );
        }
        return recordPaymentInternal(paymentRequestId, request, files, authenticatedUserId);
    }

    public RecordPaymentResponse recordPayment(
            Long paymentRequestId,
            RecordPaymentRequest request,
            MultipartFile paymentProofFile,
            Long authenticatedUserId
    ) {
        List<MultipartFile> files = new ArrayList<>();
        if (paymentProofFile != null && !paymentProofFile.isEmpty()) {
            files.add(paymentProofFile);
        }
        return recordPayment(paymentRequestId, request, files, authenticatedUserId);
    }

    private RecordPaymentResponse recordPaymentInternal(
            Long paymentRequestId,
            RecordPaymentRequest request,
            List<MultipartFile> paymentProofFiles,
            Long authenticatedUserId
    ) {
        validatePaymentRequestId(paymentRequestId);
        validateAuthenticatedUserId(authenticatedUserId);
        validateRequest(request);

        PaymentRequest paymentRequest = paymentRequestRepository.findById(paymentRequestId)
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
        if (paymentProofFiles == null || paymentProofFiles.isEmpty()) {
            throw businessError(
                    "PAYMENT_PROOF_REQUIRED",
                    "Payment proof file is required"
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

        try {
            cleanupRegistrar.register(() -> { });

            paymentMaintenanceService.persistProofFiles(
                    paymentRequest,
                    paymentProofFiles,
                    paidBy
            );

            ApprovalStatus fromApprovalStatus = paymentRequest.getApprovalStatus();
            PaymentStatus fromPaymentStatus = paymentRequest.getPaymentStatus();
            paymentRequest.setApprovalStatus(ApprovalStatus.APPROVED);
            paymentRequest.setPaymentStatus(PaymentStatus.PAID);
            paymentRequest.setPaidAt(request.paidAt());
            paymentRequest.setPaidBy(paidBy);
            paymentRequest.setPaymentMethod(request.paymentMethod());
            paymentRequest.setPaymentReference(normalize(request.paymentReference()));
            paymentRequest.setPaymentNote(normalize(request.paymentNote()));

            PaymentRequest savedPaymentRequest = savePaymentRequest(paymentRequestId, paymentRequest);
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
            approvalHistoryRepository.saveAndFlush(history);

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
            throw businessError(
                    "PAYMENT_REQUEST_VERSION_CONFLICT",
                    "Payment request version conflict: " + paymentRequestId
            );
        }
    }

    /** Legacy JSON adapter; a payment proof is mandatory for every payment. */
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

    private PaymentRequest savePaymentRequest(Long paymentRequestId, PaymentRequest paymentRequest) {
        try {
            PaymentRequest saved = paymentRequestRepository.saveAndFlush(paymentRequest);
            return saved == null ? paymentRequest : saved;
        } catch (OptimisticLockingFailureException exception) {
            throw businessError(
                    "PAYMENT_REQUEST_VERSION_CONFLICT",
                    "Payment request version conflict: " + paymentRequestId
            );
        }
    }

    private void cleanupStoredFile(String storagePath, AtomicBoolean cleaned, RuntimeException original) {
        if (!cleaned.compareAndSet(false, true)) {
            return;
        }
        try {
            attachmentStorageService.delete(storagePath);
        } catch (RuntimeException cleanupFailure) {
            log.error("Unable to clean up payment proof storage path {}", storagePath, cleanupFailure);
            if (original != null) {
                original.addSuppressed(cleanupFailure);
            }
        }
    }

    private void validatePaymentRequestId(Long id) {
        if (id == null || id <= 0) {
            throw businessError("INVALID_PAYMENT_REQUEST_ID", "Payment request id must be greater than zero");
        }
    }

    private void validateAuthenticatedUserId(Long id) {
        if (id == null || id <= 0) {
            throw businessError("INVALID_PAID_BY_ID", "Authenticated user id must be greater than zero");
        }
    }

    private void validateRequest(RecordPaymentRequest request) {
        if (request == null) {
            throw businessError("INVALID_PAYMENT_REQUEST", "Payment request input must not be null");
        }
        if (request.version() == null || request.version() < 0) {
            throw businessError("INVALID_PAYMENT_REQUEST_VERSION", "Payment request version must be zero or greater");
        }
        if (request.paidAt() == null) {
            throw businessError("INVALID_PAYMENT_DATE", "Payment date must not be null");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<MultipartFile> normalizeProofFiles(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
    }

    private PaymentDraftBusinessException businessError(String code, String message) {
        return new PaymentDraftBusinessException(code, message);
    }
}
