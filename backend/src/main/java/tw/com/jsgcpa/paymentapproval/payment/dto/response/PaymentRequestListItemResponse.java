package tw.com.jsgcpa.paymentapproval.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;

public record PaymentRequestListItemResponse(
        Long id,
        String requestNo,
        Long applicantId,
        String applicantName,
        Long departmentId,
        String departmentName,
        Long supervisorId,
        String supervisorName,
        Long companyId,
        String companyName,
        Long customerId,
        String customerName,
        RequestCategory requestCategory,
        ApprovalStatus approvalStatus,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        OffsetDateTime submittedAt,
        OffsetDateTime approvedAt,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version
) {
}
