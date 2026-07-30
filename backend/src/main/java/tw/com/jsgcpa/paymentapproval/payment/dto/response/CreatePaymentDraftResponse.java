package tw.com.jsgcpa.paymentapproval.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;

public record CreatePaymentDraftResponse(
        Long id,
        String requestNo,
        Long applicantId,
        Long departmentId,
        Long companyId,
        Long customerId,
        RequestCategory requestCategory,
        String reason,
        ApprovalStatus approvalStatus,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        List<CreatePaymentDraftItemResponse> items,
        OffsetDateTime createdAt,
        Long version
) {
}
