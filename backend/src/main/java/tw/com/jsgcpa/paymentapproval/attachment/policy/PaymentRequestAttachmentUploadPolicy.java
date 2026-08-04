package tw.com.jsgcpa.paymentapproval.attachment.policy;

import java.util.Objects;

import org.springframework.stereotype.Component;

import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentBusinessException;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;

@Component
public class PaymentRequestAttachmentUploadPolicy {

    public void validate(
            Long authenticatedUserId,
            PaymentRequest paymentRequest,
            AttachmentType attachmentType
    ) {
        if (paymentRequest == null
                || !isApplicant(authenticatedUserId, paymentRequest)) {
            throw new PaymentRequestAttachmentBusinessException(
                    "PAYMENT_REQUEST_ATTACHMENT_UPLOAD_FORBIDDEN",
                    "Only the payment request applicant may upload attachments"
            );
        }

        if (paymentRequest.getApprovalStatus() != ApprovalStatus.DRAFT) {
            throw new PaymentRequestAttachmentBusinessException(
                    "PAYMENT_REQUEST_ATTACHMENT_UPLOAD_STATUS_INVALID",
                    "Attachments may only be uploaded while the request is DRAFT"
            );
        }

        if (attachmentType == null || attachmentType == AttachmentType.PAYMENT_PROOF) {
            throw new PaymentRequestAttachmentBusinessException(
                    "PAYMENT_REQUEST_ATTACHMENT_TYPE_INVALID",
                    "PAYMENT_PROOF is not allowed in the general attachment upload"
            );
        }
    }

    private boolean isApplicant(
            Long authenticatedUserId,
            PaymentRequest paymentRequest
    ) {
        AppUser applicant = paymentRequest.getApplicant();
        return authenticatedUserId != null
                && applicant != null
                && Objects.equals(applicant.getId(), authenticatedUserId);
    }
}
