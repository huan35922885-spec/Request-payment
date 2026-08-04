package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.junit.jupiter.api.extension.ExtendWith;

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
    @Mock private TransactionRollbackCleanupRegistrar cleanupRegistrar;

    private RecordPaymentService service;
    private MultipartFile file;
    private ValidatedAttachmentFile validatedFile;
    private StoredAttachmentFile storedFile;
    private AppUser operator;

    @BeforeEach
    void setUp() {
        service = new RecordPaymentService(
                paymentRequestRepository,
                appUserRepository,
                approvalHistoryRepository,
                attachmentRepository,
                validator,
                storage,
                cleanupRegistrar,
                Clock.fixed(
                        Instant.parse("2026-08-04T06:00:00Z"),
                        ZoneId.of("Asia/Taipei")
                )
        );
        file = new MockMultipartFile(
                "file", "payment-proof.pdf", "application/pdf", "%PDF".getBytes()
        );
        validatedFile = new ValidatedAttachmentFile(
                "payment-proof.pdf", "application/pdf", "pdf", 4L, "%PDF".getBytes()
        );
        storedFile = new StoredAttachmentFile(
                "stored-proof.pdf", "1/payment-proof.pdf", 4L, "application/pdf"
        );
        operator = user(9L, "Payment Operator");
    }

    @Test
    void recordsPaidPaymentWithPaymentProofAndAuthenticatedOperator() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubValidOperation(paymentRequest);

        RecordPaymentResponse response = service.recordPayment(
                1L, paymentRequest("  BANK-001  ", "  E2E payment  "), file, 9L
        );

        assertEquals(ApprovalStatus.APPROVED, paymentRequest.getApprovalStatus());
        assertEquals(PaymentStatus.PAID, paymentRequest.getPaymentStatus());
        assertEquals(PAID_AT, paymentRequest.getPaidAt());
        assertSame(operator, paymentRequest.getPaidBy());
        assertEquals(PaymentMethod.BANK_TRANSFER, paymentRequest.getPaymentMethod());
        assertEquals("BANK-001", paymentRequest.getPaymentReference());
        assertEquals("E2E payment", paymentRequest.getPaymentNote());
        assertEquals(ApprovalAction.PAYMENT_RECORDED, response.action());
        assertEquals(4L, response.version());
        assertEquals(RECORDED_AT, response.recordedAt());
        verify(storage, never()).delete(any());
    }

    @Test
    void usesAuthenticatedUserForPaidByAndProofUploader() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubValidOperation(paymentRequest);

        RecordPaymentResponse response = service.recordPayment(
                1L, paymentRequest(), file, 9L
        );

        assertSame(operator, paymentRequest.getPaidBy());
        assertEquals(9L, response.paidById());
        assertSame(operator, capturedAttachment().getUploadedBy());
        verify(appUserRepository).findById(9L);
    }

    @Test
    void createsCompletePaymentRecordedHistory() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubValidOperation(paymentRequest);

        service.recordPayment(1L, paymentRequest(), file, 9L);

        ApprovalHistory history = capturedHistory();
        assertSame(paymentRequest, history.getPaymentRequest());
        assertSame(operator, history.getActor());
        assertEquals(ApprovalAction.PAYMENT_RECORDED, history.getAction());
        assertEquals(ApprovalStatus.APPROVED, history.getFromApprovalStatus());
        assertEquals(ApprovalStatus.APPROVED, history.getToApprovalStatus());
        assertEquals(PaymentStatus.UNPAID, history.getFromPaymentStatus());
        assertEquals(PaymentStatus.PAID, history.getToPaymentStatus());
        assertEquals("E2E payment", history.getComment());
        assertEquals(RECORDED_AT, history.getActedAt());
    }

    @Test
    void storesCompletePaymentProofMetadata() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubValidOperation(paymentRequest);

        service.recordPayment(1L, paymentRequest(), file, 9L);

        PaymentRequestAttachment attachment = capturedAttachment();
        assertSame(paymentRequest, attachment.getPaymentRequest());
        assertSame(operator, attachment.getUploadedBy());
        assertEquals(AttachmentType.PAYMENT_PROOF, attachment.getAttachmentType());
        assertEquals("payment-proof.pdf", attachment.getOriginalFilename());
        assertEquals("stored-proof.pdf", attachment.getStoredFilename());
        assertEquals("1/payment-proof.pdf", attachment.getStoragePath());
        assertEquals("application/pdf", attachment.getContentType());
        assertEquals(4L, attachment.getFileSize());
    }

    @Test
    void keepsPaidAtSeparateFromRecordedAt() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubValidOperation(paymentRequest);

        RecordPaymentResponse response = service.recordPayment(
                1L, paymentRequest(), file, 9L
        );

        assertEquals(PAID_AT, paymentRequest.getPaidAt());
        assertEquals(PAID_AT, response.paidAt());
        assertEquals(RECORDED_AT, capturedHistory().getActedAt());
        assertEquals(RECORDED_AT, response.recordedAt());
    }

    @Test
    void allowsOptionalPaymentFieldsToBeNull() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubValidOperation(paymentRequest);

        service.recordPayment(
                1L, new RecordPaymentRequest(3L, PAID_AT, null, null, null), file, 9L
        );

        assertEquals(PaymentStatus.PAID, paymentRequest.getPaymentStatus());
        assertEquals(PAID_AT, paymentRequest.getPaidAt());
        assertSame(operator, paymentRequest.getPaidBy());
        assertEquals(null, paymentRequest.getPaymentMethod());
        assertEquals(null, paymentRequest.getPaymentReference());
        assertEquals(null, paymentRequest.getPaymentNote());
        assertEquals(null, capturedHistory().getComment());
    }

    @Test
    void preservesApprovalDataAmountAndSubmittedAt() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        OffsetDateTime approvedAt = paymentRequest.getApprovedAt();
        AppUser approvedBy = paymentRequest.getApprovedBy();
        AppUser supervisorSnapshot = paymentRequest.getSupervisorSnapshot();
        OffsetDateTime submittedAt = paymentRequest.getSubmittedAt();
        BigDecimal totalAmount = paymentRequest.getTotalAmount();
        stubValidOperation(paymentRequest);

        service.recordPayment(1L, paymentRequest(), file, 9L);

        assertEquals(approvedAt, paymentRequest.getApprovedAt());
        assertSame(approvedBy, paymentRequest.getApprovedBy());
        assertSame(supervisorSnapshot, paymentRequest.getSupervisorSnapshot());
        assertEquals(submittedAt, paymentRequest.getSubmittedAt());
        assertEquals(totalAmount, paymentRequest.getTotalAmount());
    }

    @Test
    void flushesAttachmentAndPaymentBeforeHistory() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubValidOperation(paymentRequest);

        service.recordPayment(1L, paymentRequest(), file, 9L);

        InOrder order = inOrder(
                attachmentRepository,
                paymentRequestRepository,
                approvalHistoryRepository
        );
        order.verify(attachmentRepository).saveAndFlush(any(PaymentRequestAttachment.class));
        order.verify(paymentRequestRepository).saveAndFlush(paymentRequest);
        order.verify(approvalHistoryRepository).save(any(ApprovalHistory.class));
    }

    @Test
    void rejectsMissingPaymentRequestBeforeLookingUpPaidBy() {
        when(paymentRequestRepository.findById(1L)).thenReturn(Optional.empty());

        assertCode("PAYMENT_REQUEST_NOT_FOUND", () -> service.recordPayment(
                1L, paymentRequest(), file, 9L
        ));

        verify(appUserRepository, never()).findById(any());
        verify(attachmentRepository, never()).saveAndFlush(any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsVersionConflictBeforeWritingBinary() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubPaymentLookup(paymentRequest);

        assertCode("PAYMENT_REQUEST_VERSION_CONFLICT", () -> service.recordPayment(
                1L, new RecordPaymentRequest(2L, PAID_AT, null, null, null), file, 9L
        ));

        verify(storage, never()).store(any(), any());
        verify(appUserRepository, never()).findById(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ApprovalStatus.class, names = {
            "DRAFT", "PENDING_MANAGER", "PENDING_CASHIER", "REJECTED_CLOSED"
    })
    void rejectsStatusesOtherThanApproved(ApprovalStatus status) {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        paymentRequest.setApprovalStatus(status);
        stubPaymentLookup(paymentRequest);

        assertCode("PAYMENT_REQUEST_NOT_APPROVED", () -> service.recordPayment(
                1L, paymentRequest(), file, 9L
        ));

        verify(storage, never()).store(any(), any());
        verify(appUserRepository, never()).findById(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsAlreadyPaidWithoutOverwritingExistingPaymentData() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        AppUser existingPaidBy = user(8L, "Original Payer");
        OffsetDateTime originalPaidAt =
                OffsetDateTime.parse("2026-07-29T10:00:00+08:00");
        paymentRequest.setPaymentStatus(PaymentStatus.PAID);
        paymentRequest.setPaidAt(originalPaidAt);
        paymentRequest.setPaidBy(existingPaidBy);
        paymentRequest.setPaymentMethod(PaymentMethod.CASH);
        paymentRequest.setPaymentReference("ORIGINAL-001");
        paymentRequest.setPaymentNote("Original payment");
        stubPaymentLookup(paymentRequest);

        assertCode("PAYMENT_REQUEST_ALREADY_PAID", () -> service.recordPayment(
                1L, paymentRequest("NEW", "NEW"), file, 9L
        ));

        assertSame(existingPaidBy, paymentRequest.getPaidBy());
        assertEquals(originalPaidAt, paymentRequest.getPaidAt());
        assertEquals(PaymentMethod.CASH, paymentRequest.getPaymentMethod());
        assertEquals("ORIGINAL-001", paymentRequest.getPaymentReference());
        assertEquals("Original payment", paymentRequest.getPaymentNote());
        verify(storage, never()).store(any(), any());
        verify(appUserRepository, never()).findById(any());
    }

    @Test
    void rejectsMissingPaymentProofBeforeWritingBinary() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubPaymentLookup(paymentRequest);

        assertCode("PAYMENT_PROOF_REQUIRED", () -> service.recordPayment(
                1L, paymentRequest(), null, 9L
        ));

        verify(attachmentRepository, never()).existsByPaymentRequest_IdAndAttachmentType(
                any(), any()
        );
        verify(storage, never()).store(any(), any());
        verify(appUserRepository, never()).findById(any());
    }

    @Test
    void rejectsDuplicateProofBeforeWritingBinary() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubPaymentLookup(paymentRequest);
        when(attachmentRepository.existsByPaymentRequest_IdAndAttachmentType(
                1L, AttachmentType.PAYMENT_PROOF
        )).thenReturn(true);

        assertCode("PAYMENT_PROOF_ALREADY_EXISTS", () -> service.recordPayment(
                1L, paymentRequest(), file, 9L
        ));

        verify(storage, never()).store(any(), any());
        verify(appUserRepository, never()).findById(any());
    }

    @Test
    void rejectsMissingPaidBy() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubPaymentAndDuplicateCheck(paymentRequest);
        when(appUserRepository.findById(9L)).thenReturn(Optional.empty());

        assertCode("PAID_BY_NOT_FOUND", () -> service.recordPayment(
                1L, paymentRequest(), file, 9L
        ));

        verify(storage, never()).store(any(), any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void rejectsInactivePaidBy() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubPaymentAndDuplicateCheck(paymentRequest);
        operator.setActive(false);
        when(appUserRepository.findById(9L)).thenReturn(Optional.of(operator));

        assertCode("PAID_BY_INACTIVE", () -> service.recordPayment(
                1L, paymentRequest(), file, 9L
        ));

        verify(storage, never()).store(any(), any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsFileValidationFailureWithoutDatabaseWrites() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubPaymentAndDuplicateCheck(paymentRequest);
        stubActiveOperator();
        when(validator.validate(file)).thenThrow(new RuntimeException("invalid file"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));

        assertEquals("invalid file", exception.getMessage());
        verify(storage, never()).store(any(), any());
        verify(attachmentRepository, never()).saveAndFlush(any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void propagatesStorageFailureWithoutDatabaseWrites() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubPaymentAndDuplicateCheck(paymentRequest);
        stubActiveOperator();
        when(validator.validate(file)).thenReturn(validatedFile);
        when(storage.store(1L, validatedFile))
                .thenThrow(new RuntimeException("storage failure"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));

        assertEquals("storage failure", exception.getMessage());
        verify(attachmentRepository, never()).saveAndFlush(any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void cleansStoredProofWhenMetadataSaveFails() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubBeforeMetadataSave(paymentRequest);
        when(attachmentRepository.saveAndFlush(any(PaymentRequestAttachment.class)))
                .thenThrow(new RuntimeException("metadata failure"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));

        assertEquals("metadata failure", exception.getMessage());
        verify(storage).delete(storedFile.relativeStoragePath());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void cleansStoredProofWhenPaymentSaveFails() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubBeforePaymentSave(paymentRequest);
        when(paymentRequestRepository.saveAndFlush(any(PaymentRequest.class)))
                .thenThrow(new RuntimeException("payment save failure"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));

        assertEquals("payment save failure", exception.getMessage());
        verify(storage).delete(storedFile.relativeStoragePath());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void propagatesHistorySaveFailureAndCleansStoredProof() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubBeforeHistorySave(paymentRequest);
        when(approvalHistoryRepository.save(any(ApprovalHistory.class)))
                .thenThrow(new RuntimeException("history failure"));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));

        assertEquals("history failure", exception.getMessage());
        verify(storage).delete(storedFile.relativeStoragePath());
        verify(attachmentRepository).saveAndFlush(any(PaymentRequestAttachment.class));
        verify(paymentRequestRepository).saveAndFlush(paymentRequest);
    }

    @Test
    void convertsOptimisticLockingFailureAndCleansStoredProof() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubBeforePaymentSave(paymentRequest);
        when(paymentRequestRepository.saveAndFlush(any(PaymentRequest.class)))
                .thenThrow(new OptimisticLockingFailureException("conflict"));

        PaymentDraftBusinessException exception = assertCode(
                "PAYMENT_REQUEST_VERSION_CONFLICT",
                () -> service.recordPayment(1L, paymentRequest(), file, 9L)
        );

        assertEquals(0, exception.getSuppressed().length);
        verify(storage).delete(storedFile.relativeStoragePath());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void cleansStoredProofOnlyOnceWhenRollbackCallbackRunsAfterFailure() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubBeforeHistorySave(paymentRequest);
        when(approvalHistoryRepository.save(any(ApprovalHistory.class)))
                .thenThrow(new RuntimeException("history failure"));
        AtomicReference<Runnable> callback = new AtomicReference<>();
        doAnswer(invocation -> {
            callback.set(invocation.getArgument(0));
            return null;
        }).when(cleanupRegistrar).register(any(Runnable.class));

        assertThrows(RuntimeException.class, () -> service.recordPayment(
                1L, paymentRequest(), file, 9L
        ));
        callback.get().run();
        callback.get().run();

        verify(storage, times(1)).delete(storedFile.relativeStoragePath());
    }

    @Test
    void registrationFailureCleansStoredProofAndWritesNoDatabaseRows() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubBeforeStore(paymentRequest);
        RuntimeException registrationFailure = new RuntimeException("registration failure");
        doThrow(registrationFailure).when(cleanupRegistrar).register(any(Runnable.class));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));

        assertSame(registrationFailure, exception);
        verify(storage).delete(storedFile.relativeStoragePath());
        verify(attachmentRepository, never()).saveAndFlush(any());
        verify(paymentRequestRepository, never()).saveAndFlush(any());
        verify(approvalHistoryRepository, never()).save(any());
    }

    @Test
    void cleanupFailureDoesNotReplaceRegistrationFailure() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubBeforeStore(paymentRequest);
        RuntimeException registrationFailure = new RuntimeException("registration failure");
        RuntimeException cleanupFailure = new RuntimeException("cleanup failure");
        doThrow(registrationFailure).when(cleanupRegistrar).register(any(Runnable.class));
        doThrow(cleanupFailure).when(storage).delete(storedFile.relativeStoragePath());

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));

        assertSame(registrationFailure, exception);
        assertEquals(1, exception.getSuppressed().length);
        assertSame(cleanupFailure, exception.getSuppressed()[0]);
    }

    @Test
    void cleanupFailureDoesNotReplaceMetadataFailure() {
        PaymentRequest paymentRequest = approvedUnpaidRequest();
        stubBeforeMetadataSave(paymentRequest);
        RuntimeException metadataFailure = new RuntimeException("metadata failure");
        RuntimeException cleanupFailure = new RuntimeException("cleanup failure");
        when(attachmentRepository.saveAndFlush(any(PaymentRequestAttachment.class)))
                .thenThrow(metadataFailure);
        doThrow(cleanupFailure).when(storage).delete(storedFile.relativeStoragePath());

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                service.recordPayment(1L, paymentRequest(), file, 9L));

        assertSame(metadataFailure, exception);
        assertEquals(1, exception.getSuppressed().length);
        assertSame(cleanupFailure, exception.getSuppressed()[0]);
    }

    @Test
    void rejectsInvalidPaymentRequestIds() {
        for (Long id : new Long[] {null, 0L, -1L}) {
            assertCode("INVALID_PAYMENT_REQUEST_ID", () -> service.recordPayment(
                    id, paymentRequest(), file, 9L
            ));
        }
        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void rejectsInvalidAuthenticatedUserIds() {
        for (Long userId : new Long[] {null, 0L, -1L}) {
            assertCode("INVALID_PAID_BY_ID", () -> service.recordPayment(
                    1L, paymentRequest(), file, userId
            ));
        }
        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void rejectsInvalidVersions() {
        assertCode("INVALID_PAYMENT_REQUEST_VERSION", () -> service.recordPayment(
                1L, new RecordPaymentRequest(null, PAID_AT, null, null, null), file, 9L
        ));
        assertCode("INVALID_PAYMENT_REQUEST_VERSION", () -> service.recordPayment(
                1L, new RecordPaymentRequest(-1L, PAID_AT, null, null, null), file, 9L
        ));
        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void rejectsNullRequestAndPaidAt() {
        assertCode("INVALID_PAYMENT_REQUEST", () -> service.recordPayment(
                1L, null, file, 9L
        ));
        assertCode("INVALID_PAYMENT_DATE", () -> service.recordPayment(
                1L, new RecordPaymentRequest(3L, null, null, null, null), file, 9L
        ));
        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void legacyJsonContractCannotRecordWithoutProof() {
        assertCode("PAYMENT_PROOF_REQUIRED", () -> service.recordPayment(
                1L, 9L, paymentRequest()
        ));
    }

    private void stubValidOperation(PaymentRequest paymentRequest) {
        stubBeforePaymentSave(paymentRequest);
        when(paymentRequestRepository.saveAndFlush(paymentRequest))
                .thenAnswer(invocation -> {
                    setField(paymentRequest, "version", 4L);
                    return paymentRequest;
                });
    }

    private void stubBeforeHistorySave(PaymentRequest paymentRequest) {
        stubBeforePaymentSave(paymentRequest);
        when(paymentRequestRepository.saveAndFlush(paymentRequest))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubBeforePaymentSave(PaymentRequest paymentRequest) {
        stubBeforeMetadataSave(paymentRequest);
        when(attachmentRepository.saveAndFlush(any(PaymentRequestAttachment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubBeforeMetadataSave(PaymentRequest paymentRequest) {
        stubBeforeStore(paymentRequest);
    }

    private void stubBeforeStore(PaymentRequest paymentRequest) {
        stubPaymentAndDuplicateCheck(paymentRequest);
        stubActiveOperator();
        when(validator.validate(file)).thenReturn(validatedFile);
        when(storage.store(1L, validatedFile)).thenReturn(storedFile);
    }

    private void stubPaymentAndDuplicateCheck(PaymentRequest paymentRequest) {
        stubPaymentLookup(paymentRequest);
        when(attachmentRepository.existsByPaymentRequest_IdAndAttachmentType(
                1L, AttachmentType.PAYMENT_PROOF
        )).thenReturn(false);
    }

    private void stubPaymentLookup(PaymentRequest paymentRequest) {
        when(paymentRequestRepository.findById(1L))
                .thenReturn(Optional.of(paymentRequest));
    }

    private void stubActiveOperator() {
        when(appUserRepository.findById(9L)).thenReturn(Optional.of(operator));
    }

    private RecordPaymentRequest paymentRequest() {
        return paymentRequest("BANK-001", "E2E payment");
    }

    private RecordPaymentRequest paymentRequest(String reference, String note) {
        return new RecordPaymentRequest(
                3L, PAID_AT, PaymentMethod.BANK_TRANSFER, reference, note
        );
    }

    private PaymentRequest approvedUnpaidRequest() {
        PaymentRequest value = new PaymentRequest();
        setField(value, "id", 1L);
        setField(value, "version", 3L);
        value.setRequestNo("PAY-001");
        value.setApprovalStatus(ApprovalStatus.APPROVED);
        value.setPaymentStatus(PaymentStatus.UNPAID);
        value.setTotalAmount(new BigDecimal("1620.50"));
        value.setSubmittedAt(OffsetDateTime.parse("2026-08-03T09:00:00+08:00"));
        value.setApprovedAt(OffsetDateTime.parse("2026-08-03T10:00:00+08:00"));
        value.setApprovedBy(user(2L, "Approver"));
        value.setSupervisorSnapshot(user(3L, "Supervisor Snapshot"));
        return value;
    }

    private AppUser user(Long id, String displayName) {
        AppUser value = new AppUser();
        setField(value, "id", id);
        value.setDisplayName(displayName);
        value.setActive(true);
        return value;
    }

    private ApprovalHistory capturedHistory() {
        ArgumentCaptor<ApprovalHistory> captor =
                ArgumentCaptor.forClass(ApprovalHistory.class);
        verify(approvalHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private PaymentRequestAttachment capturedAttachment() {
        ArgumentCaptor<PaymentRequestAttachment> captor =
                ArgumentCaptor.forClass(PaymentRequestAttachment.class);
        verify(attachmentRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private PaymentDraftBusinessException assertCode(
            String expectedCode,
            org.junit.jupiter.api.function.Executable executable
    ) {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class, executable
        );
        assertEquals(expectedCode, exception.getCode());
        return exception;
    }

    private void setField(Object target, String fieldName, Object value) {
        ReflectionTestUtils.setField(target, fieldName, value);
    }
}
