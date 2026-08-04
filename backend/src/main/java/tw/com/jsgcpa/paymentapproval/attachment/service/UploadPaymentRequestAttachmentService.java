package tw.com.jsgcpa.paymentapproval.attachment.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import tw.com.jsgcpa.paymentapproval.attachment.dto.response.PaymentRequestAttachmentResponse;
import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentStorageException;
import tw.com.jsgcpa.paymentapproval.attachment.policy.PaymentRequestAttachmentUploadPolicy;
import tw.com.jsgcpa.paymentapproval.attachment.storage.AttachmentStorageService;
import tw.com.jsgcpa.paymentapproval.attachment.storage.StoredAttachmentFile;
import tw.com.jsgcpa.paymentapproval.attachment.validation.AttachmentFileValidator;
import tw.com.jsgcpa.paymentapproval.attachment.validation.ValidatedAttachmentFile;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestAttachmentRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;

@Service
public class UploadPaymentRequestAttachmentService {

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentRequestAttachmentRepository attachmentRepository;
    private final AttachmentFileValidator attachmentFileValidator;
    private final AttachmentStorageService attachmentStorageService;
    private final PaymentRequestAttachmentUploadPolicy uploadPolicy;

    public UploadPaymentRequestAttachmentService(
            PaymentRequestRepository paymentRequestRepository,
            PaymentRequestAttachmentRepository attachmentRepository,
            AttachmentFileValidator attachmentFileValidator,
            AttachmentStorageService attachmentStorageService,
            PaymentRequestAttachmentUploadPolicy uploadPolicy
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentFileValidator = attachmentFileValidator;
        this.attachmentStorageService = attachmentStorageService;
        this.uploadPolicy = uploadPolicy;
    }

    @Transactional
    public PaymentRequestAttachmentResponse upload(
            Long paymentRequestId,
            Long authenticatedUserId,
            AttachmentType attachmentType,
            MultipartFile file
    ) {
        PaymentRequest paymentRequest = findPaymentRequest(paymentRequestId);
        uploadPolicy.validate(
                authenticatedUserId,
                paymentRequest,
                attachmentType
        );

        ValidatedAttachmentFile validatedFile = attachmentFileValidator
                .validate(file);
        StoredAttachmentFile storedFile = attachmentStorageService.store(
                paymentRequestId,
                validatedFile
        );
        AtomicBoolean cleaned = new AtomicBoolean(false);
        Runnable cleanup = () -> cleanupStoredFile(
                storedFile.relativeStoragePath(),
                cleaned
        );
        registerRollbackCleanup(cleanup);

        try {
            PaymentRequestAttachment attachment = new PaymentRequestAttachment();
            attachment.setPaymentRequest(paymentRequest);
            attachment.setUploadedBy(paymentRequest.getApplicant());
            attachment.setAttachmentType(attachmentType);
            attachment.setOriginalFilename(validatedFile.safeOriginalFilename());
            attachment.setStoredFilename(storedFile.storedFilename());
            attachment.setStoragePath(storedFile.relativeStoragePath());
            attachment.setContentType(storedFile.contentType());
            attachment.setFileSize(storedFile.fileSize());

            PaymentRequestAttachment savedAttachment = attachmentRepository
                    .saveAndFlush(attachment);
            return toResponse(savedAttachment);
        } catch (RuntimeException exception) {
            cleanup.run();
            throw exception;
        }
    }

    private PaymentRequest findPaymentRequest(Long paymentRequestId) {
        if (paymentRequestId == null || paymentRequestId <= 0) {
            throw new PaymentDraftBusinessException(
                    "PAYMENT_REQUEST_NOT_FOUND",
                    "Payment request not found"
            );
        }
        return paymentRequestRepository.findById(paymentRequestId)
                .orElseThrow(() -> new PaymentDraftBusinessException(
                        "PAYMENT_REQUEST_NOT_FOUND",
                        "Payment request not found"
                ));
    }

    private void registerRollbackCleanup(Runnable cleanup) {
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
            AtomicBoolean cleaned
    ) {
        if (!cleaned.compareAndSet(false, true)) {
            return;
        }
        try {
            attachmentStorageService.delete(storagePath);
        } catch (AttachmentStorageException ignored) {
            // Keep the original transaction or persistence failure visible.
        }
    }

    private PaymentRequestAttachmentResponse toResponse(
            PaymentRequestAttachment attachment
    ) {
        return new PaymentRequestAttachmentResponse(
                attachment.getId(),
                attachment.getAttachmentType(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                attachment.getUploadedBy().getId(),
                attachment.getUploadedBy().getDisplayName(),
                attachment.getCreatedAt()
        );
    }
}
