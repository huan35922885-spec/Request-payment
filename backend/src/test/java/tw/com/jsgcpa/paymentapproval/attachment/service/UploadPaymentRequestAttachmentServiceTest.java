package tw.com.jsgcpa.paymentapproval.attachment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.attachment.dto.response.PaymentRequestAttachmentResponse;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentBusinessException;
import tw.com.jsgcpa.paymentapproval.attachment.policy.PaymentRequestAttachmentUploadPolicy;
import tw.com.jsgcpa.paymentapproval.attachment.storage.AttachmentStorageService;
import tw.com.jsgcpa.paymentapproval.attachment.storage.StoredAttachmentFile;
import tw.com.jsgcpa.paymentapproval.attachment.validation.AttachmentFileValidator;
import tw.com.jsgcpa.paymentapproval.attachment.validation.ValidatedAttachmentFile;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestAttachmentRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploadPaymentRequestAttachmentServiceTest {

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private PaymentRequestAttachmentRepository attachmentRepository;

    @Mock
    private AttachmentFileValidator attachmentFileValidator;

    @Mock
    private AttachmentStorageService attachmentStorageService;

    @Mock
    private PaymentRequestAttachmentUploadPolicy uploadPolicy;

    @Mock
    private PaymentRequest paymentRequest;

    @Mock
    private AppUser applicant;

    @Mock
    private PaymentRequestAttachment savedAttachment;

    private UploadPaymentRequestAttachmentService service;
    private MockMultipartFile multipartFile;
    private ValidatedAttachmentFile validatedFile;
    private StoredAttachmentFile storedFile;

    @BeforeEach
    void setUp() throws Exception {
        service = new UploadPaymentRequestAttachmentService(
                paymentRequestRepository,
                attachmentRepository,
                attachmentFileValidator,
                attachmentStorageService,
                uploadPolicy
        );
        multipartFile = new MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}
        );
        validatedFile = new ValidatedAttachmentFile(
                "invoice.pdf",
                "application/pdf",
                "pdf",
                5L,
                multipartFile.getBytes()
        );
        storedFile = new StoredAttachmentFile(
                "550e8400-e29b-41d4-a716-446655440000.pdf",
                "payment-requests/14/550e8400-e29b-41d4-a716-446655440000.pdf",
                5L,
                "application/pdf"
        );
        when(paymentRequestRepository.findById(14L))
                .thenReturn(Optional.of(paymentRequest));
        when(paymentRequest.getApplicant()).thenReturn(applicant);
        when(applicant.getId()).thenReturn(1L);
        when(paymentRequest.getApprovalStatus()).thenReturn(ApprovalStatus.DRAFT);
        when(attachmentFileValidator.validate(multipartFile))
                .thenReturn(validatedFile);
        when(attachmentStorageService.store(14L, validatedFile))
                .thenReturn(storedFile);
        when(savedAttachment.getId()).thenReturn(90L);
        when(savedAttachment.getAttachmentType()).thenReturn(AttachmentType.INVOICE);
        when(savedAttachment.getOriginalFilename()).thenReturn("invoice.pdf");
        when(savedAttachment.getContentType()).thenReturn("application/pdf");
        when(savedAttachment.getFileSize()).thenReturn(5L);
        when(savedAttachment.getUploadedBy()).thenReturn(applicant);
        when(applicant.getDisplayName()).thenReturn("Applicant");
        when(attachmentRepository.saveAndFlush(any(PaymentRequestAttachment.class)))
                .thenReturn(savedAttachment);
    }

    @Test
    void uploadsMetadataWithPrincipalApplicantAndReturnsResponse() {
        PaymentRequestAttachmentResponse response = service.upload(
                14L,
                1L,
                AttachmentType.INVOICE,
                multipartFile
        );

        assertEquals(90L, response.id());
        assertEquals(AttachmentType.INVOICE, response.attachmentType());
        assertEquals(1L, response.uploadedById());
        assertEquals("Applicant", response.uploadedByDisplayName());
        verify(uploadPolicy).validate(1L, paymentRequest, AttachmentType.INVOICE);
        verify(attachmentStorageService).store(14L, validatedFile);

        ArgumentCaptor<PaymentRequestAttachment> captor =
                ArgumentCaptor.forClass(PaymentRequestAttachment.class);
        verify(attachmentRepository).saveAndFlush(captor.capture());
        PaymentRequestAttachment saved = captor.getValue();
        assertEquals(paymentRequest, saved.getPaymentRequest());
        assertEquals(applicant, saved.getUploadedBy());
        assertEquals(AttachmentType.INVOICE, saved.getAttachmentType());
        assertEquals("invoice.pdf", saved.getOriginalFilename());
        assertEquals(storedFile.storedFilename(), saved.getStoredFilename());
        assertEquals(storedFile.relativeStoragePath(), saved.getStoragePath());
        assertEquals("application/pdf", saved.getContentType());
        assertEquals(5L, saved.getFileSize());
    }

    @Test
    void missingPaymentRequestDoesNotValidateOrStore() {
        when(paymentRequestRepository.findById(14L)).thenReturn(Optional.empty());

        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.upload(
                        14L,
                        1L,
                        AttachmentType.INVOICE,
                        multipartFile
                )
        );

        assertEquals("PAYMENT_REQUEST_NOT_FOUND", exception.getCode());
        verify(uploadPolicy, never()).validate(any(), any(), any());
        verifyNoStorageOrSave();
    }

    @Test
    void policyFailureDoesNotValidateOrStore() {
        doThrow(new PaymentRequestAttachmentBusinessException(
                "PAYMENT_REQUEST_ATTACHMENT_UPLOAD_FORBIDDEN",
                "forbidden"
        )).when(uploadPolicy).validate(1L, paymentRequest, AttachmentType.INVOICE);

        assertThrows(
                PaymentRequestAttachmentBusinessException.class,
                () -> service.upload(
                        14L,
                        1L,
                        AttachmentType.INVOICE,
                        multipartFile
                )
        );

        verifyNoStorageOrSave();
    }

    @Test
    void validatorFailureDoesNotWriteStorageOrMetadata() {
        doThrow(new RuntimeException("validation failed"))
                .when(attachmentFileValidator).validate(multipartFile);

        assertThrows(
                RuntimeException.class,
                () -> service.upload(
                        14L,
                        1L,
                        AttachmentType.INVOICE,
                        multipartFile
                )
        );

        verify(attachmentStorageService, never()).store(any(), any());
        verify(attachmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void storageFailureDoesNotSaveMetadata() {
        doThrow(new RuntimeException("storage failed"))
                .when(attachmentStorageService).store(14L, validatedFile);

        assertThrows(
                RuntimeException.class,
                () -> service.upload(
                        14L,
                        1L,
                        AttachmentType.INVOICE,
                        multipartFile
                )
        );

        verify(attachmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void metadataFailureDeletesStoredFile() {
        when(attachmentRepository.saveAndFlush(any(PaymentRequestAttachment.class)))
                .thenThrow(new RuntimeException("database failed"));

        assertThrows(
                RuntimeException.class,
                () -> service.upload(
                        14L,
                        1L,
                        AttachmentType.INVOICE,
                        multipartFile
                )
        );

        verify(attachmentStorageService).delete(
                eq(storedFile.relativeStoragePath())
        );
    }

    private void verifyNoStorageOrSave() {
        verify(attachmentStorageService, never()).store(any(), any());
        verify(attachmentRepository, never()).saveAndFlush(any());
    }
}
