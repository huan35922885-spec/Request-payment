package tw.com.jsgcpa.paymentapproval.payment.dto.response;

import java.time.OffsetDateTime;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;

public record SubmitPaymentDraftResponse(
        Long id,
        String requestNo,
        ApprovalStatus approvalStatus,
        PaymentStatus paymentStatus,
        Long supervisorId,
        String supervisorName,
        OffsetDateTime submittedAt,
        Long version
) {
}
