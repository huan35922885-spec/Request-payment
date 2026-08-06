package tw.com.jsgcpa.paymentapproval.attachment.policy;

import org.springframework.stereotype.Component;

import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;

@Component
public class PaymentProofMaintenancePolicy {

    public void requireApproved(PaymentRequest paymentRequest) {
        if (paymentRequest == null
                || paymentRequest.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new PaymentDraftBusinessException(
                    "PAYMENT_REQUEST_NOT_APPROVED",
                    "僅核准案件可維護付款資料或付款證明"
            );
        }
    }

    public void requirePaymentProof(PaymentRequestAttachment attachment, Long paymentRequestId) {
        if (attachment == null
                || attachment.getPaymentRequest() == null
                || !paymentRequestId.equals(attachment.getPaymentRequest().getId())) {
            throw new PaymentDraftBusinessException(
                    "PAYMENT_PROOF_NOT_FOUND",
                    "找不到付款證明"
            );
        }
        if (attachment.getAttachmentType() != AttachmentType.PAYMENT_PROOF) {
            throw new PaymentDraftBusinessException(
                    "PAYMENT_PROOF_NOT_FOUND",
                    "找不到付款證明"
            );
        }
    }
}
