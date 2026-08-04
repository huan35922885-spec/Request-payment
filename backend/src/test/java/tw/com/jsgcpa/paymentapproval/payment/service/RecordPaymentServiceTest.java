package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockMultipartFile;
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
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestAttachmentRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordPaymentServiceTest {

    private static final OffsetDateTime PAID_AT =
            OffsetDateTime.parse("2026-08-04T10:00:00+08:00");
    private static final OffsetDateTime RECORDED_AT =
            OffsetDateTime.parse("2026-08-04T14:00:00+08:00");

    @Mock private PaymentRequestRepository paymentRequestRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private ApprovalHistoryRepository approvalHistoryRepository;
    @Mock private PaymentRequestAttachmentRepository attachmentRepository;
    @Mock private AttachmentFileValidator validator;
    @Mock private AttachmentStorageService storage;

    private RecordPaymentService service;
    private PaymentRequest request;
    private AppUser operator;
    private MultipartFile file;
    private ValidatedAttachmentFile validatedFile;
    private StoredAttachmentFile storedFile;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-04T06:00:00Z"),
                ZoneId.of("Asia/Taipei")
        );
        service = new RecordPaymentService(
                paymentRequestRepository,
                appUserRepository,
                approvalHistoryRepository,
                attachmentRepository,
                validator,
                storage,
                clock
        );
        request = approvedUnpaidRequest();
        operator = user(9L, "Payment Operator");
        file = new MockMultipartFile(
                "file", "payment-proof.pdf", "application/pdf", "%PDF".getBytes()
        );
        validatedFile = new ValidatedAttachmentFile(
                "payment-proof.pdf", "application/pdf", "pdf", 4L, "%PDF".getBytes()
        );
        storedFile = new StoredAttachmentFile(
                "stored-proof.pdf", "9/payment-proof.pdf", 4L, "application/pdf"
        );
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(request));
        when(attachmentRepository.existsByPaymentRequest_IdAndAttachmentType(
                1L, AttachmentType.PAYMENT_PROOF)).thenReturn(false);
        when(appUserRepository.findById(9L)).thenReturn(Optional.of(operator));
        when(validator.validate(file)).thenReturn(validatedFile);
        when(storage.store(1L, validatedFile)).thenReturn(storedFile);
        when(attachmentRepository.saveAndFlush(any(PaymentRequestAttachment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRequestRepository.saveAndFlush(request))
                .thenAnswer(invocation -> {
                    setField(request, "version", 4L);
                    return request;
                });
    }

    @Test
    void recordsPaidPaymentWithPaymentProofAndAuthenticatedOperator() {
        RecordPaymentResponse response = service.recordPayment(
                1L, paymentRequest(), file, 9L
        );

        assertEquals(ApprovalStatus.APPROVED, request.getApprovalStatus());
        assertEquals(PaymentStatus.PAID, request.getPaymentStatus());
        assertEquals(PAID_AT, request.getPaidAt());
        assertSame(operator, request.getPaidBy());
        assertEquals(PaymentMethod.BANK_TRANSFER, request.getPaymentMethod());
        assertEquals(4L, response.version());
        assertEquals(ApprovalAction.PAYMENT_RECORDED, response.action());

        ApprovalHistory history = history();
        assertSame(operator, history.getActor());
        assertEquals(ApprovalAction.PAYMENT_RECORDED, history.getAction());
        assertEquals(ApprovalStatus.APPROVED, history.getFromApprovalStatus());
        assertEquals(ApprovalStatus.APPROVED, history.getToApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, history.getFromPaymentStatus());
        assertEquals(PaymentStatus.PAID, history.getToPaymentStatus());
        assertEquals("E2E payment", history.getComment());
        assertEquals(RECORDED_AT, history.getActedAt());

        PaymentRequestAttachment attachment = attachment();
        assertSame(operator, attachment.getUploadedBy());
        assertEquals(AttachmentType.PAYMENT_PROOF, attachment.getAttachmentType());
        assertEquals("payment-proof.pdf", attachment.getOriginalFilename());
    }

    @Test
    void flushesAttachmentAndPaymentBeforeHistory() {
        service.recordPayment(1L, paymentRequest(), file, 9L);
        InOrder order = inOrder(
                attachmentRepository,
                paymentRequestRepository,
                approvalHistoryRepository
        );
        order.verify(attachmentRepository).saveAndFlush(any(PaymentRequestAttachment.class));
        order.verify(paymentRequestRepository).saveAndFlush(request);
        order.verify(approvalHistoryRepository).save(any(ApprovalHistory.class));
    }

    @Test
    void requiresProofBeforeChangingPayment() {
        assertCode("PAYMENT_PROOF_REQUIRED", () -> service.recordPayment(
                1L, paymentRequest(), null, 9L
        ));
        verify(paymentRequestRepository).findById(1L);
        verify(storage, never()).store(any(), any());
    }

    @Test
    void rejectsDuplicateProofBeforeWritingBinary() {
        when(attachmentRepository.existsByPaymentRequest_IdAndAttachmentType(
                1L, AttachmentType.PAYMENT_PROOF)).thenReturn(true);
        assertCode("PAYMENT_PROOF_ALREADY_EXISTS", () -> service.recordPayment(
                1L, paymentRequest(), file, 9L
        ));
        verify(storage, never()).store(any(), any());
    }

    @Test
    void rejectsNonApprovedAndAlreadyPaidRequests() {
        request.setApprovalStatus(ApprovalStatus.PENDING_CASHIER);
        assertCode("PAYMENT_REQUEST_NOT_APPROVED", () -> service.recordPayment(
                1L, paymentRequest(), file, 9L
        ));
        request.setApprovalStatus(ApprovalStatus.APPROVED);
        request.setPaymentStatus(PaymentStatus.PAID);
        assertCode("PAYMENT_REQUEST_ALREADY_PAID", () -> service.recordPayment(
                1L, paymentRequest(), file, 9L
        ));
    }

    @Test
    void convertsOptimisticLockAndCleansStoredProof() {
        when(paymentRequestRepository.saveAndFlush(any(PaymentRequest.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"));
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.recordPayment(1L, paymentRequest(), file, 9L)
        );
        assertEquals("PAYMENT_REQUEST_VERSION_CONFLICT", exception.getCode());
        verify(storage).delete(storedFile.relativeStoragePath());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void cleansStoredProofWhenMetadataSaveFails() {
        when(attachmentRepository.saveAndFlush(any(PaymentRequestAttachment.class)))
                .thenThrow(new RuntimeException("metadata failure"));
        assertThrows(RuntimeException.class, () -> service.recordPayment(
                1L, paymentRequest(), file, 9L
        ));
        verify(storage).delete(storedFile.relativeStoragePath());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void propagatesHistorySaveFailureAndCleansStoredProof() {
        when(approvalHistoryRepository.save(any(ApprovalHistory.class)))
                .thenThrow(new RuntimeException("history failure"));
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));
        assertEquals("history failure", exception.getMessage());
        verify(storage).delete(storedFile.relativeStoragePath());
    }

    @Test
    void rejectsInvalidIdentifiersAndVersion() {
        assertCode("INVALID_PAYMENT_REQUEST_ID", () -> service.recordPayment(
                0L, paymentRequest(), file, 9L));
        assertCode("INVALID_PAID_BY_ID", () -> service.recordPayment(
                1L, paymentRequest(), file, 0L));
        assertCode("INVALID_PAYMENT_REQUEST_VERSION", () -> service.recordPayment(
                1L, new RecordPaymentRequest(-1L, PAID_AT, null, null, null), file, 9L));
    }

    @Test
    void legacyJsonContractCannotRecordWithoutProof() {
        assertCode("PAYMENT_PROOF_REQUIRED", () -> service.recordPayment(
                1L, 9L, paymentRequest()
        ));
    }

    private RecordPaymentRequest paymentRequest() {
        return new RecordPaymentRequest(
                3L, PAID_AT, PaymentMethod.BANK_TRANSFER,
                "BANK-001", "E2E payment"
        );
    }

    private PaymentRequest approvedUnpaidRequest() {
        PaymentRequest value = new PaymentRequest();
        setField(value, "id", 1L);
        value.setRequestNo("PAY-001");
        value.setApprovalStatus(ApprovalStatus.APPROVED);
        value.setPaymentStatus(PaymentStatus.UNPAID);
        setField(value, "version", 3L);
        return value;
    }

    private AppUser user(Long id, String displayName) {
        AppUser value = new AppUser();
        setField(value, "id", id);
        value.setDisplayName(displayName);
        value.setActive(true);
        return value;
    }

    private ApprovalHistory history() {
        var captor = org.mockito.ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(approvalHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private PaymentRequestAttachment attachment() {
        var captor = org.mockito.ArgumentCaptor.forClass(PaymentRequestAttachment.class);
        verify(attachmentRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private void setField(Object target, String field, Object value) {
        org.springframework.test.util.ReflectionTestUtils.setField(target, field, value);
    }

    private void assertCode(String code, org.junit.jupiter.api.function.Executable executable) {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class, executable
        );
        assertEquals(code, exception.getCode());
    }
}
