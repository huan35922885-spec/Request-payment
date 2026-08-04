package tw.com.jsgcpa.paymentapproval.attachment.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import tw.com.jsgcpa.paymentapproval.attachment.dto.response.DownloadPaymentRequestAttachmentResult;
import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentStorageException;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentNotFoundException;
import tw.com.jsgcpa.paymentapproval.attachment.storage.AttachmentStorageService;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestAttachmentRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;
import tw.com.jsgcpa.paymentapproval.payment.service.PaymentRequestReadAuthorizationService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DownloadPaymentRequestAttachmentServiceTest {

    private static final Long REQUEST_ID = 14L;
    private static final Long ATTACHMENT_ID = 90L;
    private static final Long APPLICANT_ID = 1L;
    private static final String STORAGE_PATH =
            "payment-requests/14/550e8400-e29b-41d4-a716-446655440000.pdf";
    private static final byte[] CONTENT = new byte[]{
            0x25, 0x50, 0x44, 0x46, 0x2D
    };

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private PaymentRequestAttachmentRepository attachmentRepository;

    @Mock
    private PaymentRequestReadAuthorizationService readAuthorizationService;

    @Mock
    private AttachmentStorageService attachmentStorageService;

    @Mock
    private PaymentRequest paymentRequest;

    @Mock
    private PaymentRequestAttachment attachment;

    private DownloadPaymentRequestAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new DownloadPaymentRequestAttachmentService(
                paymentRequestRepository,
                attachmentRepository,
                readAuthorizationService,
                attachmentStorageService
        );
        when(paymentRequestRepository.findById(REQUEST_ID))
                .thenReturn(Optional.of(paymentRequest));
        when(readAuthorizationService.canReadDetail(
                eq(paymentRequest),
                eq(APPLICANT_ID),
                anyBoolean(),
                anyBoolean()
        )).thenReturn(true);
        when(attachmentRepository.findById(ATTACHMENT_ID))
                .thenReturn(Optional.of(attachment));
        when(attachment.getPaymentRequest()).thenReturn(paymentRequest);
        when(paymentRequest.getId()).thenReturn(REQUEST_ID);
        when(attachment.getFileSize()).thenReturn((long) CONTENT.length);
        when(attachment.getStoragePath()).thenReturn(STORAGE_PATH);
        when(attachment.getOriginalFilename()).thenReturn("下載測試發票.pdf");
        when(attachment.getContentType()).thenReturn("application/pdf");
        when(attachmentStorageService.size(STORAGE_PATH))
                .thenReturn((long) CONTENT.length);
        when(attachmentStorageService.load(STORAGE_PATH))
                .thenReturn(new ByteArrayInputStream(CONTENT));
    }

    @Test
    void applicantDownloadUsesAuthorizationThenAttachmentThenStorage() throws Exception {
        DownloadPaymentRequestAttachmentResult result = service.download(
                REQUEST_ID,
                ATTACHMENT_ID,
                APPLICANT_ID,
                false,
                false
        );

        assertEquals("下載測試發票.pdf", result.safeOriginalFilename());
        assertEquals(MediaType.APPLICATION_PDF, result.contentType());
        assertEquals(CONTENT.length, result.fileSize());
        assertArrayEquals(CONTENT, result.resource().getInputStream().readAllBytes());

        InOrder order = inOrder(
                paymentRequestRepository,
                readAuthorizationService,
                attachmentRepository,
                attachmentStorageService
        );
        order.verify(paymentRequestRepository).findById(REQUEST_ID);
        order.verify(readAuthorizationService).canReadDetail(
                paymentRequest,
                APPLICANT_ID,
                false,
                false
        );
        order.verify(attachmentRepository).findById(ATTACHMENT_ID);
        order.verify(attachmentStorageService).size(STORAGE_PATH);
        order.verify(attachmentStorageService).load(STORAGE_PATH);
    }

    @Test
    void unauthorizedUserDoesNotQueryAttachmentOrStorage() {
        when(readAuthorizationService.canReadDetail(
                paymentRequest,
                9L,
                false,
                false
        )).thenReturn(false);

        assertThrows(
                PaymentRequestAttachmentNotFoundException.class,
                () -> service.download(
                        REQUEST_ID,
                        ATTACHMENT_ID,
                        9L,
                        false,
                        false
                )
        );

        verify(attachmentRepository, never()).findById(ATTACHMENT_ID);
        verify(attachmentStorageService, never()).size(eq(STORAGE_PATH));
        verify(attachmentStorageService, never()).load(eq(STORAGE_PATH));
    }

    @Test
    void missingAttachmentReturnsNotFound() {
        when(attachmentRepository.findById(ATTACHMENT_ID))
                .thenReturn(Optional.empty());

        assertThrows(
                PaymentRequestAttachmentNotFoundException.class,
                () -> service.download(
                        REQUEST_ID,
                        ATTACHMENT_ID,
                        APPLICANT_ID,
                        false,
                        false
                )
        );

        verify(attachmentStorageService, never()).size(eq(STORAGE_PATH));
        verify(attachmentStorageService, never()).load(eq(STORAGE_PATH));
    }

    @Test
    void attachmentFromAnotherPaymentRequestReturnsNotFound() {
        PaymentRequest otherRequest = org.mockito.Mockito.mock(PaymentRequest.class);
        when(otherRequest.getId()).thenReturn(99L);
        when(attachment.getPaymentRequest()).thenReturn(otherRequest);

        assertThrows(
                PaymentRequestAttachmentNotFoundException.class,
                () -> service.download(
                        REQUEST_ID,
                        ATTACHMENT_ID,
                        APPLICANT_ID,
                        false,
                        false
                )
        );

        verify(attachmentStorageService, never()).size(eq(STORAGE_PATH));
        verify(attachmentStorageService, never()).load(eq(STORAGE_PATH));
    }

    @Test
    void storageReadFailureIsPreservedAsSafeReadError() {
        when(attachmentStorageService.size(STORAGE_PATH))
                .thenThrow(new AttachmentStorageException(
                        "ATTACHMENT_STORAGE_READ_FAILED",
                        "internal path detail"
                ));

        AttachmentStorageException exception = assertThrows(
                AttachmentStorageException.class,
                () -> service.download(
                        REQUEST_ID,
                        ATTACHMENT_ID,
                        APPLICANT_ID,
                        false,
                        false
                )
        );

        assertEquals("ATTACHMENT_STORAGE_READ_FAILED", exception.getCode());
        verify(attachmentStorageService, never()).load(eq(STORAGE_PATH));
    }

    @Test
    void fileSizeMismatchFailsBeforeLoadingBinary() {
        when(attachmentStorageService.size(STORAGE_PATH)).thenReturn(99L);

        AttachmentStorageException exception = assertThrows(
                AttachmentStorageException.class,
                () -> service.download(
                        REQUEST_ID,
                        ATTACHMENT_ID,
                        APPLICANT_ID,
                        false,
                        false
                )
        );

        assertEquals("ATTACHMENT_STORAGE_READ_FAILED", exception.getCode());
        verify(attachmentStorageService, never()).load(eq(STORAGE_PATH));
    }

    @Test
    void blankOrUnsafeFilenameUsesSafeFallback() {
        when(attachment.getOriginalFilename()).thenReturn("../unsafe\r\nname.pdf");

        DownloadPaymentRequestAttachmentResult result = service.download(
                REQUEST_ID,
                ATTACHMENT_ID,
                APPLICANT_ID,
                false,
                false
        );

        assertEquals("attachment-90.pdf", result.safeOriginalFilename());
    }
}
