package tw.com.jsgcpa.paymentapproval.attachment.policy;

import java.util.Objects;

import org.springframework.stereotype.Component;

import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentDeleteException;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentNotFoundException;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;

/** Pure business policy for deleting a general attachment from a draft. */
@Component
public class PaymentRequestAttachmentDeletePolicy {

    public void validate(
            Long authenticatedUserId,
            PaymentRequest paymentRequest,
            PaymentRequestAttachment attachment
    ) {
        if (paymentRequest == null
                || !isApplicant(authenticatedUserId, paymentRequest)) {
            throw new PaymentRequestAttachmentDeleteException(
                    "PAYMENT_REQUEST_ATTACHMENT_DELETE_FORBIDDEN",
                    "目前登入者不可刪除此請款單附件"
            );
        }

        if (paymentRequest.getApprovalStatus() != ApprovalStatus.DRAFT) {
            throw new PaymentRequestAttachmentDeleteException(
                    "PAYMENT_REQUEST_ATTACHMENT_DELETE_STATUS_INVALID",
                    "只有草稿狀態可以刪除一般請款附件"
            );
        }

        // The service uses a null attachment for the first authorization check,
        // before it is allowed to query the attachment repository.
        if (attachment == null) {
            return;
        }

        if (attachment.getPaymentRequest() == null
                || !Objects.equals(
                        paymentRequest.getId(),
                        attachment.getPaymentRequest().getId()
                )) {
            throw new PaymentRequestAttachmentNotFoundException();
        }

        if (attachment.getAttachmentType() == AttachmentType.PAYMENT_PROOF) {
            throw new PaymentRequestAttachmentDeleteException(
                    "PAYMENT_REQUEST_ATTACHMENT_TYPE_INVALID",
                    "付款證明不允許透過一般附件刪除功能移除"
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
