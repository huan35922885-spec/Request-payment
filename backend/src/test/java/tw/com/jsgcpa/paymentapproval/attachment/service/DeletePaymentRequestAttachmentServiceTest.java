package tw.com.jsgcpa.paymentapproval.attachment.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentStorageException;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentDeleteException;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentNotFoundException;
import tw.com.jsgcpa.paymentapproval.attachment.policy.PaymentRequestAttachmentDeletePolicy;
import tw.com.jsgcpa.paymentapproval.attachment.storage.AttachmentStorageService;
import tw.com.jsgcpa.paymentapproval.attachment.storage.PreparedAttachmentDeletion;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestAttachmentRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeletePaymentRequestAttachmentServiceTest {

    private static final PreparedAttachmentDeletion PREPARED =
            new PreparedAttachmentDeletion(
                    "payment-requests/14/file.pdf",
                    ".attachment-delete-abc.deleting",
                    true
            );

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private PaymentRequestAttachmentRepository attachmentRepository;

    @Mock
    private PaymentRequestAttachmentDeletePolicy deletePolicy;

    @Mock
    private AttachmentStorageService attachmentStorageService;

    @Mock
    private PaymentRequest paymentRequest;

    @Mock
    private PaymentRequestAttachment attachment;

    private DeletePaymentRequestAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new DeletePaymentRequestAttachmentService(
                paymentRequestRepository,
                attachmentRepository,
                deletePolicy,
                attachmentStorageService
        );
        when(paymentRequestRepository.findById(14L))
                .thenReturn(Optional.of(paymentRequest));
        when(attachmentRepository.findById(90L))
                .thenReturn(Optional.of(attachment));
        when(attachment.getStoragePath())
                .thenReturn("payment-requests/14/file.pdf");
        when(attachmentStorageService.prepareDelete(
                "payment-requests/14/file.pdf"
        )).thenReturn(PREPARED);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletesMetadataAfterPreparingBinaryAndFinalizesDirectCall() {
        service.delete(14L, 90L, 1L);

        verify(deletePolicy).validate(1L, paymentRequest, null);
        verify(deletePolicy, org.mockito.Mockito.times(3))
                .validate(1L, paymentRequest, attachment);
        verify(attachmentStorageService).prepareDelete(
                "payment-requests/14/file.pdf"
        );
        verify(attachmentRepository).delete(attachment);
        verify(attachmentRepository).flush();
        verify(attachmentStorageService).commitDelete(PREPARED);
        verify(attachmentStorageService, never()).restore(PREPARED);
    }

    @Test
    void invalidIdsAreRejectedBeforeDatabaseLookup() {
        assertThrows(
                PaymentRequestAttachmentNotFoundException.class,
                () -> service.delete(0L, 90L, 1L)
        );
        assertThrows(
                PaymentRequestAttachmentNotFoundException.class,
                () -> service.delete(14L, null, 1L)
        );

        verifyNoInteractions(paymentRequestRepository, attachmentRepository);
    }

    @Test
    void missingRequestIsHiddenAsAttachmentNotFound() {
        when(paymentRequestRepository.findById(14L)).thenReturn(Optional.empty());

        assertThrows(
                PaymentRequestAttachmentNotFoundException.class,
                () -> service.delete(14L, 90L, 1L)
        );

        verifyNoInteractions(attachmentRepository, attachmentStorageService);
    }

    @Test
    void forbiddenApplicantDoesNotQueryAttachmentOrStorage() {
        doThrow(new PaymentRequestAttachmentDeleteException(
                "PAYMENT_REQUEST_ATTACHMENT_DELETE_FORBIDDEN", "forbidden"
        )).when(deletePolicy).validate(6L, paymentRequest, null);

        assertThrows(
                PaymentRequestAttachmentDeleteException.class,
                () -> service.delete(14L, 90L, 6L)
        );

        verifyNoInteractions(attachmentRepository, attachmentStorageService);
    }

    @Test
    void nonDraftDoesNotQueryAttachmentOrStorage() {
        doThrow(new PaymentRequestAttachmentDeleteException(
                "PAYMENT_REQUEST_ATTACHMENT_DELETE_STATUS_INVALID", "status"
        )).when(deletePolicy).validate(1L, paymentRequest, null);

        assertThrows(
                PaymentRequestAttachmentDeleteException.class,
                () -> service.delete(14L, 90L, 1L)
        );

        verifyNoInteractions(attachmentRepository, attachmentStorageService);
    }

    @Test
    void missingAttachmentDoesNotTouchStorage() {
        when(attachmentRepository.findById(90L)).thenReturn(Optional.empty());

        assertThrows(
                PaymentRequestAttachmentNotFoundException.class,
                () -> service.delete(14L, 90L, 1L)
        );

        verifyNoInteractions(attachmentStorageService);
    }

    @Test
    void mismatchAndPaymentProofAreRejectedBeforePrepare() {
        doThrow(new PaymentRequestAttachmentNotFoundException())
                .when(deletePolicy).validate(1L, paymentRequest, attachment);

        assertThrows(
                PaymentRequestAttachmentNotFoundException.class,
                () -> service.delete(14L, 90L, 1L)
        );
        verifyNoInteractions(attachmentStorageService);

        org.mockito.Mockito.reset(deletePolicy);
        doThrow(new PaymentRequestAttachmentDeleteException(
                "PAYMENT_REQUEST_ATTACHMENT_TYPE_INVALID", "type"
        )).when(deletePolicy).validate(1L, paymentRequest, attachment);

        assertThrows(
                PaymentRequestAttachmentDeleteException.class,
                () -> service.delete(14L, 90L, 1L)
        );
        verifyNoInteractions(attachmentStorageService);
    }

    @Test
    void missingBinaryDoesNotDeleteMetadata() {
        AttachmentStorageException exception = new AttachmentStorageException(
                "ATTACHMENT_STORAGE_DELETE_FAILED", "missing"
        );
        when(attachmentStorageService.prepareDelete(any()))
                .thenThrow(exception);

        assertSame(exception, assertThrows(
                AttachmentStorageException.class,
                () -> service.delete(14L, 90L, 1L)
        ));

        verify(attachmentRepository, never()).delete(any());
        verify(attachmentRepository, never()).flush();
    }

    @Test
    void databaseFailureRestoresPreparedBinaryAndPreservesOriginalException() {
        RuntimeException databaseException = new RuntimeException("db");
        doThrow(databaseException).when(attachmentRepository).flush();

        assertSame(databaseException, assertThrows(
                RuntimeException.class,
                () -> service.delete(14L, 90L, 1L)
        ));

        verify(attachmentStorageService).restore(PREPARED);
        verify(attachmentStorageService, never()).commitDelete(PREPARED);
    }

    @Test
    void commitFinalizesOnlyAfterTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        service.delete(14L, 90L, 1L);

        verify(attachmentStorageService, never()).commitDelete(PREPARED);
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(attachmentStorageService).commitDelete(PREPARED);
        verify(attachmentStorageService, never()).restore(PREPARED);
    }

    @Test
    void rollbackRestoresPreparedBinary() {
        TransactionSynchronizationManager.initSynchronization();
        service.delete(14L, 90L, 1L);

        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(attachmentStorageService).restore(PREPARED);
        verify(attachmentStorageService, never()).commitDelete(PREPARED);
    }

    @Test
    void commitFinalizationFailureDoesNotThrowAfterDatabaseCommit() {
        doThrow(new AttachmentStorageException(
                "ATTACHMENT_STORAGE_DELETE_FAILED", "cleanup"
        )).when(attachmentStorageService).commitDelete(PREPARED);
        TransactionSynchronizationManager.initSynchronization();

        service.delete(14L, 90L, 1L);
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED
                )
        );
    }
}
