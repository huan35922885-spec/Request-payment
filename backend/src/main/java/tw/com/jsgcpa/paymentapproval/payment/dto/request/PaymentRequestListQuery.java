package tw.com.jsgcpa.paymentapproval.payment.dto.request;

import java.time.LocalDate;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;

public record PaymentRequestListQuery(
        Integer page,
        Integer size,
        String requestNo,
        ApprovalStatus approvalStatus,
        PaymentStatus paymentStatus,
        RequestCategory requestCategory,
        Long applicantId,
        Long departmentId,
        Long supervisorId,
        Long companyId,
        Long customerId,
        LocalDate createdFrom,
        LocalDate createdTo
) {
}
