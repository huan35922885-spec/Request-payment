package tw.com.jsgcpa.paymentapproval.attachment.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentBusinessException;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;

class PaymentRequestAttachmentUploadPolicyTest {

    private final PaymentRequestAttachmentUploadPolicy policy =
            new PaymentRequestAttachmentUploadPolicy();

    private PaymentRequest paymentRequest;
    private AppUser applicant;

    @BeforeEach
    void setUp() {
        paymentRequest = mock(PaymentRequest.class);
        applicant = mock(AppUser.class);
        when(paymentRequest.getApplicant()).thenReturn(applicant);
        when(applicant.getId()).thenReturn(1L);
        when(paymentRequest.getApprovalStatus()).thenReturn(ApprovalStatus.DRAFT);
    }

    @Test
    void allowsApplicantToUploadGeneralAttachmentInDraft() {
        assertDoesNotThrow(() -> policy.validate(
                1L,
                paymentRequest,
                AttachmentType.INVOICE
        ));
        assertDoesNotThrow(() -> policy.validate(
                1L,
                paymentRequest,
                AttachmentType.RECEIPT
        ));
        assertDoesNotThrow(() -> policy.validate(
                1L,
                paymentRequest,
                AttachmentType.REQUEST_PROOF
        ));
        assertDoesNotThrow(() -> policy.validate(
                1L,
                paymentRequest,
                AttachmentType.OTHER
        ));
    }

    @Test
    void rejectsPaymentProofFromGeneralUploadEndpoint() {
        PaymentRequestAttachmentBusinessException exception = assertThrows(
                PaymentRequestAttachmentBusinessException.class,
                () -> policy.validate(
                        1L,
                        paymentRequest,
                        AttachmentType.PAYMENT_PROOF
                )
        );

        assertEquals(
                "PAYMENT_REQUEST_ATTACHMENT_TYPE_INVALID",
                exception.getCode()
        );
    }

    @Test
    void rejectsNonApplicant() {
        PaymentRequestAttachmentBusinessException exception = assertThrows(
                PaymentRequestAttachmentBusinessException.class,
                () -> policy.validate(
                        2L,
                        paymentRequest,
                        AttachmentType.INVOICE
                )
        );

        assertEquals(
                "PAYMENT_REQUEST_ATTACHMENT_UPLOAD_FORBIDDEN",
                exception.getCode()
        );
    }

    @Test
    void rejectsNonDraftStatus() {
        when(paymentRequest.getApprovalStatus())
                .thenReturn(ApprovalStatus.PENDING_MANAGER);

        PaymentRequestAttachmentBusinessException exception = assertThrows(
                PaymentRequestAttachmentBusinessException.class,
                () -> policy.validate(
                        1L,
                        paymentRequest,
                        AttachmentType.INVOICE
                )
        );

        assertEquals(
                "PAYMENT_REQUEST_ATTACHMENT_UPLOAD_STATUS_INVALID",
                exception.getCode()
        );
    }
}
