package tw.com.jsgcpa.paymentapproval.payment.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tw.com.jsgcpa.paymentapproval.attachment.policy.PaymentProofMaintenancePolicy;
import tw.com.jsgcpa.paymentapproval.attachment.service.DeletePaymentRequestAttachmentService;
import tw.com.jsgcpa.paymentapproval.attachment.storage.AttachmentStorageService;
import tw.com.jsgcpa.paymentapproval.attachment.storage.StoredAttachmentFile;
import tw.com.jsgcpa.paymentapproval.attachment.validation.AttachmentFileValidator;
import tw.com.jsgcpa.paymentapproval.attachment.validation.ValidatedAttachmentFile;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.PatchPaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestAttachmentRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@Service
@Transactional
public class PaymentMaintenanceService {

    private final PaymentRequestRepository paymentRequestRepository;
    private final AppUserRepository appUserRepository;
    private final PaymentRequestAttachmentRepository attachmentRepository;
    private final AttachmentFileValidator attachmentFileValidator;
    private final AttachmentStorageService attachmentStorageService;
    private final TransactionRollbackCleanupRegistrar cleanupRegistrar;
    private final PaymentProofMaintenancePolicy maintenancePolicy;
    private final DeletePaymentRequestAttachmentService deleteAttachmentService;
    private final GetPaymentRequestDetailService detailService;

    public PaymentMaintenanceService(
            PaymentRequestRepository paymentRequestRepository,
            AppUserRepository appUserRepository,
            PaymentRequestAttachmentRepository attachmentRepository,
            AttachmentFileValidator attachmentFileValidator,
            AttachmentStorageService attachmentStorageService,
            TransactionRollbackCleanupRegistrar cleanupRegistrar,
            PaymentProofMaintenancePolicy maintenancePolicy,
            DeletePaymentRequestAttachmentService deleteAttachmentService,
            GetPaymentRequestDetailService detailService
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.appUserRepository = appUserRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentFileValidator = attachmentFileValidator;
        this.attachmentStorageService = attachmentStorageService;
        this.cleanupRegistrar = cleanupRegistrar;
        this.maintenancePolicy = maintenancePolicy;
        this.deleteAttachmentService = deleteAttachmentService;
        this.detailService = detailService;
    }

    public PaymentRequestDetailResponse patchPayment(
            Long paymentRequestId,
            PatchPaymentRequest request,
            Long authenticatedUserId
    ) {
        PaymentRequest paymentRequest = loadApproved(paymentRequestId, request.version());
        maintenancePolicy.requireApproved(paymentRequest);
        if (paymentRequest.getPaymentStatus() != PaymentStatus.PAID) {
            throw businessError(
                    "PAYMENT_REQUEST_NOT_PAID",
                    "僅已付款案件可更新付款資料"
            );
        }
        paymentRequest.setPaidAt(request.paidAt());
        paymentRequest.setPaymentMethod(request.paymentMethod());
        paymentRequest.setPaymentReference(normalize(request.paymentReference()));
        paymentRequest.setPaymentNote(normalize(request.paymentNote()));
        savePaymentRequest(paymentRequestId, paymentRequest);
        return detailService.getDetail(paymentRequestId, authenticatedUserId, true, false);
    }

    public PaymentRequestDetailResponse uploadPaymentProofs(
            Long paymentRequestId,
            List<MultipartFile> files,
            Long authenticatedUserId
    ) {
        if (files == null || files.isEmpty()) {
            throw businessError("PAYMENT_PROOF_REQUIRED", "請上傳至少一份付款證明");
        }
        PaymentRequest paymentRequest = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> businessError(
                        "PAYMENT_REQUEST_NOT_FOUND",
                        "Payment request not found: " + paymentRequestId
                ));
        maintenancePolicy.requireApproved(paymentRequest);
        AppUser uploader = loadActiveUser(authenticatedUserId);
        persistProofFiles(paymentRequest, files, uploader);
        return detailService.getDetail(paymentRequestId, authenticatedUserId, true, false);
    }

    public void deletePaymentProof(
            Long paymentRequestId,
            Long attachmentId,
            Long authenticatedUserId
    ) {
        PaymentRequest paymentRequest = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> businessError(
                        "PAYMENT_REQUEST_NOT_FOUND",
                        "Payment request not found: " + paymentRequestId
                ));
        maintenancePolicy.requireApproved(paymentRequest);
        PaymentRequestAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> businessError(
                        "PAYMENT_PROOF_NOT_FOUND",
                        "找不到付款證明"
                ));
        maintenancePolicy.requirePaymentProof(attachment, paymentRequestId);
        deleteAttachmentService.deleteForCashier(paymentRequestId, attachmentId, authenticatedUserId);
    }

    void persistProofFiles(
            PaymentRequest paymentRequest,
            List<MultipartFile> files,
            AppUser uploadedBy
    ) {
        List<String> storedPaths = new ArrayList<>();
        AtomicBoolean cleaned = new AtomicBoolean(false);
        RuntimeException[] failure = new RuntimeException[1];
        Runnable cleanupAll = () -> {
            if (!cleaned.compareAndSet(false, true)) {
                return;
            }
            for (String path : storedPaths) {
                try {
                    attachmentStorageService.delete(path);
                } catch (RuntimeException ignored) {
                    // best effort rollback
                }
            }
        };
        try {
            cleanupRegistrar.register(cleanupAll);
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                ValidatedAttachmentFile validated = attachmentFileValidator.validate(file);
                StoredAttachmentFile stored = attachmentStorageService.store(
                        paymentRequest.getId(),
                        validated
                );
                storedPaths.add(stored.relativeStoragePath());
                PaymentRequestAttachment attachment = new PaymentRequestAttachment();
                attachment.setPaymentRequest(paymentRequest);
                attachment.setUploadedBy(uploadedBy);
                attachment.setAttachmentType(AttachmentType.PAYMENT_PROOF);
                attachment.setOriginalFilename(validated.safeOriginalFilename());
                attachment.setStoredFilename(stored.storedFilename());
                attachment.setStoragePath(stored.relativeStoragePath());
                attachment.setContentType(stored.contentType());
                attachment.setFileSize(stored.fileSize());
                attachmentRepository.save(attachment);
            }
            if (storedPaths.isEmpty()) {
                throw businessError("PAYMENT_PROOF_REQUIRED", "請上傳至少一份付款證明");
            }
            attachmentRepository.flush();
        } catch (RuntimeException exception) {
            failure[0] = exception;
            cleanupAll.run();
            throw exception;
        }
    }

    private PaymentRequest loadApproved(Long paymentRequestId, Long version) {
        PaymentRequest paymentRequest = paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> businessError(
                        "PAYMENT_REQUEST_NOT_FOUND",
                        "Payment request not found: " + paymentRequestId
                ));
        if (!version.equals(paymentRequest.getVersion())) {
            throw businessError(
                    "PAYMENT_REQUEST_VERSION_CONFLICT",
                    "Payment request version conflict for id " + paymentRequestId
            );
        }
        maintenancePolicy.requireApproved(paymentRequest);
        return paymentRequest;
    }

    private AppUser loadActiveUser(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> businessError(
                        "PAID_BY_NOT_FOUND",
                        "Payment user not found: " + userId
                ));
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw businessError("PAID_BY_INACTIVE", "Payment user is inactive: " + userId);
        }
        return user;
    }

    private PaymentRequest savePaymentRequest(Long paymentRequestId, PaymentRequest paymentRequest) {
        try {
            return paymentRequestRepository.saveAndFlush(paymentRequest);
        } catch (OptimisticLockingFailureException exception) {
            throw businessError(
                    "PAYMENT_REQUEST_VERSION_CONFLICT",
                    "Payment request version conflict: " + paymentRequestId
            );
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private PaymentDraftBusinessException businessError(String code, String message) {
        return new PaymentDraftBusinessException(code, message);
    }
}
