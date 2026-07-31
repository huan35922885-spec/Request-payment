package tw.com.jsgcpa.paymentapproval.payment.dto.response;

import java.time.OffsetDateTime;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalAction;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;

public record ManagerReviewPaymentResponse(
        Long id,
        String requestNo,
        ApprovalAction action,
        ApprovalStatus approvalStatus,
        PaymentStatus paymentStatus,
        Long managerId,
        String managerName,
        String comment,
        OffsetDateTime actedAt,
        Long version
) {
}
