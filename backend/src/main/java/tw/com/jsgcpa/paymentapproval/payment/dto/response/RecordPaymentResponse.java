package tw.com.jsgcpa.paymentapproval.payment.dto.response;

import java.time.OffsetDateTime;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalAction;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;

public record RecordPaymentResponse(
        Long id,
        String requestNo,
        ApprovalAction action,
        ApprovalStatus approvalStatus,
        PaymentStatus paymentStatus,
        Long paidById,
        String paidByName,
        OffsetDateTime paidAt,
        PaymentMethod paymentMethod,
        String paymentReference,
        String paymentNote,
        OffsetDateTime recordedAt,
        Long version
) {
}
