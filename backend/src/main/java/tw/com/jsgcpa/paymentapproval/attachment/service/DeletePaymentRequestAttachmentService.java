package tw.com.jsgcpa.paymentapproval.attachment.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentNotFoundException;
import tw.com.jsgcpa.paymentapproval.attachment.policy.PaymentProofMaintenancePolicy;
import tw.com.jsgcpa.paymentapproval.attachment.policy.PaymentRequestAttachmentDeletePolicy;
import tw.com.jsgcpa.paymentapproval.attachment.storage.AttachmentStorageService;
import tw.com.jsgcpa.paymentapproval.attachment.storage.PreparedAttachmentDeletion;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestAttachmentRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@Service
@Transactional
public class DeletePaymentRequestAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(
            DeletePaymentRequestAttachmentService.class
    );

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentRequestAttachmentRepository attachmentRepository;
    private final PaymentRequestAttachmentDeletePolicy deletePolicy;
    private final AttachmentStorageService attachmentStorageService;

    private final PaymentProofMaintenancePolicy proofMaintenancePolicy;

    public DeletePaymentRequestAttachmentService(
            PaymentRequestRepository paymentRequestRepository,
            PaymentRequestAttachmentRepository attachmentRepository,
            PaymentRequestAttachmentDeletePolicy deletePolicy,
            AttachmentStorageService attachmentStorageService,
            PaymentProofMaintenancePolicy proofMaintenancePolicy
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.attachmentRepository = attachmentRepository;
        this.deletePolicy = deletePolicy;
        this.attachmentStorageService = attachmentStorageService;
        this.proofMaintenancePolicy = proofMaintenancePolicy;
    }

    public void deleteForCashier(
            Long paymentRequestId,
            Long attachmentId,
            Long authenticatedUserId
    ) {
        validateId(paymentRequestId);
        validateId(attachmentId);
        if (authenticatedUserId == null || authenticatedUserId <= 0) {
            throw new PaymentRequestAttachmentNotFoundException();
        }

        PaymentRequest paymentRequest = paymentRequestRepository
                .findById(paymentRequestId)
                .orElseThrow(PaymentRequestAttachmentNotFoundException::new);
        proofMaintenancePolicy.requireApproved(paymentRequest);

        PaymentRequestAttachment attachment = attachmentRepository
                .findById(attachmentId)
                .orElseThrow(PaymentRequestAttachmentNotFoundException::new);
        proofMaintenancePolicy.requirePaymentProof(attachment, paymentRequestId);
        if (attachment.getAttachmentType() != AttachmentType.PAYMENT_PROOF) {
            throw new PaymentRequestAttachmentNotFoundException();
        }

        deleteAttachmentMetadata(paymentRequestId, attachmentId, attachment);
    }

    public void delete(
            Long paymentRequestId,
            Long attachmentId,
            Long authenticatedUserId
    ) {
        validateId(paymentRequestId);
        validateId(attachmentId);

        PaymentRequest paymentRequest = paymentRequestRepository
                .findById(paymentRequestId)
                .orElseThrow(PaymentRequestAttachmentNotFoundException::new);

        // This check intentionally happens before the attachment lookup.
        deletePolicy.validate(authenticatedUserId, paymentRequest, null);

        PaymentRequestAttachment attachment = attachmentRepository
                .findById(attachmentId)
                .orElseThrow(PaymentRequestAttachmentNotFoundException::new);

        deletePolicy.validate(authenticatedUserId, paymentRequest, attachment);

        deleteAttachmentMetadata(paymentRequestId, attachmentId, attachment);
    }

    private void deleteAttachmentMetadata(
            Long paymentRequestId,
            Long attachmentId,
            PaymentRequestAttachment attachment
    ) {
        PreparedAttachmentDeletion prepared = attachmentStorageService
                .prepareDelete(attachment.getStoragePath());

        DeletionLifecycle lifecycle = new DeletionLifecycle(
                prepared,
                paymentRequestId,
                attachmentId
        );
        lifecycle.register();

        try {
            attachmentRepository.delete(attachment);
            attachmentRepository.flush();
        } catch (RuntimeException exception) {
            lifecycle.restoreForFailure(exception);
            throw exception;
        }

        lifecycle.finalizeWithoutTransactionSynchronization();
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new PaymentRequestAttachmentNotFoundException();
        }
    }

    private final class DeletionLifecycle {

        private final PreparedAttachmentDeletion prepared;
        private final Long paymentRequestId;
        private final Long attachmentId;
        private final AtomicBoolean completed = new AtomicBoolean();

        private DeletionLifecycle(
                PreparedAttachmentDeletion prepared,
                Long paymentRequestId,
                Long attachmentId
        ) {
            this.prepared = prepared;
            this.paymentRequestId = paymentRequestId;
            this.attachmentId = attachmentId;
        }

        private void register() {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                return;
            }
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (!completed.compareAndSet(false, true)) {
                                return;
                            }
                            if (status == STATUS_COMMITTED) {
                                finalizeAfterCommit();
                            } else {
                                restoreAfterRollback();
                            }
                        }
                    }
            );
        }

        private void restoreForFailure(RuntimeException primaryException) {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            try {
                attachmentStorageService.restore(prepared);
            } catch (RuntimeException compensationException) {
                log.error(
                        "Attachment delete compensation failed: paymentRequestId={}, attachmentId={}, originalPath={}, preparedPath={}",
                        paymentRequestId,
                        attachmentId,
                        prepared.originalRelativePath(),
                        prepared.preparedRelativePath(),
                        compensationException
                );
                // Preserve the database/business exception that caused rollback.
                if (primaryException != compensationException) {
                    primaryException.addSuppressed(compensationException);
                }
            }
        }

        private void finalizeWithoutTransactionSynchronization() {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                return;
            }
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            finalizeAfterCommit();
        }

        private void finalizeAfterCommit() {
            try {
                attachmentStorageService.commitDelete(prepared);
            } catch (RuntimeException exception) {
                // The database transaction has already committed. Do not turn
                // a successful API call into a false rollback signal.
                log.error(
                        "Attachment delete finalization failed: paymentRequestId={}, attachmentId={}, preparedPath={}",
                        paymentRequestId,
                        attachmentId,
                        prepared.preparedRelativePath(),
                        exception
                );
            }
        }

        private void restoreAfterRollback() {
            try {
                attachmentStorageService.restore(prepared);
            } catch (RuntimeException exception) {
                log.error(
                        "Attachment delete rollback compensation failed: paymentRequestId={}, attachmentId={}, originalPath={}, preparedPath={}",
                        paymentRequestId,
                        attachmentId,
                        prepared.originalRelativePath(),
                        prepared.preparedRelativePath(),
                        exception
                );
            }
        }
    }
}
