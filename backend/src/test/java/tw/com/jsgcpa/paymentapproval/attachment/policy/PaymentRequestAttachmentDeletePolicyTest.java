package tw.com.jsgcpa.paymentapproval.attachment.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentDeleteException;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentNotFoundException;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;

class PaymentRequestAttachmentDeletePolicyTest {

    private final PaymentRequestAttachmentDeletePolicy policy =
            new PaymentRequestAttachmentDeletePolicy();
    private PaymentRequest paymentRequest;
    private PaymentRequestAttachment attachment;
    private AppUser applicant;

    @BeforeEach
    void setUp() {
        applicant = mock(AppUser.class);
        when(applicant.getId()).thenReturn(1L);
        paymentRequest = mock(PaymentRequest.class);
        when(paymentRequest.getId()).thenReturn(14L);
        when(paymentRequest.getApplicant()).thenReturn(applicant);
        when(paymentRequest.getApprovalStatus()).thenReturn(ApprovalStatus.DRAFT);

        attachment = mock(PaymentRequestAttachment.class);
        when(attachment.getPaymentRequest()).thenReturn(paymentRequest);
        when(attachment.getAttachmentType()).thenReturn(AttachmentType.INVOICE);
    }

    @Test
    void allowsAllGeneralAttachmentTypes() {
        for (AttachmentType type : new AttachmentType[]{
                AttachmentType.INVOICE,
                AttachmentType.RECEIPT,
                AttachmentType.REQUEST_PROOF,
                AttachmentType.OTHER
        }) {
            when(attachment.getAttachmentType()).thenReturn(type);
            assertDoesNotThrow(() -> policy.validate(1L, paymentRequest, attachment));
        }
    }

    @Test
    void rejectsPaymentProof() {
        when(attachment.getAttachmentType()).thenReturn(AttachmentType.PAYMENT_PROOF);

        PaymentRequestAttachmentDeleteException exception = assertThrows(
                PaymentRequestAttachmentDeleteException.class,
                () -> policy.validate(1L, paymentRequest, attachment)
        );

        assertEquals("PAYMENT_REQUEST_ATTACHMENT_TYPE_INVALID", exception.getCode());
    }

    @Test
    void rejectsNonApplicantBeforeAttachmentLookup() {
        PaymentRequestAttachmentDeleteException exception = assertThrows(
                PaymentRequestAttachmentDeleteException.class,
                () -> policy.validate(6L, paymentRequest, null)
        );

        assertEquals("PAYMENT_REQUEST_ATTACHMENT_DELETE_FORBIDDEN", exception.getCode());
    }

    @Test
    void rejectsNonDraftBeforeAttachmentLookup() {
        when(paymentRequest.getApprovalStatus()).thenReturn(ApprovalStatus.PENDING_MANAGER);

        PaymentRequestAttachmentDeleteException exception = assertThrows(
                PaymentRequestAttachmentDeleteException.class,
                () -> policy.validate(1L, paymentRequest, null)
        );

        assertEquals("PAYMENT_REQUEST_ATTACHMENT_DELETE_STATUS_INVALID", exception.getCode());
    }

    @Test
    void rejectsAttachmentFromAnotherRequestAsNotFound() {
        PaymentRequest otherRequest = mock(PaymentRequest.class);
        when(otherRequest.getId()).thenReturn(11L);
        when(attachment.getPaymentRequest()).thenReturn(otherRequest);

        assertThrows(
                PaymentRequestAttachmentNotFoundException.class,
                () -> policy.validate(1L, paymentRequest, attachment)
        );
    }
}
