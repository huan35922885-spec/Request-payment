package tw.com.jsgcpa.paymentapproval.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalAction;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;

public record PaymentRequestDetailResponse(
        Long id,
        String requestNo,
        UserSummary applicant,
        DepartmentSummary department,
        UserSummary supervisor,
        CompanySummary company,
        CustomerSummary customer,
        RequestCategory requestCategory,
        String reason,
        ApprovalStatus approvalStatus,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        OffsetDateTime submittedAt,
        OffsetDateTime approvedAt,
        UserSummary approvedBy,
        OffsetDateTime rejectedAt,
        OffsetDateTime closedAt,
        OffsetDateTime paidAt,
        UserSummary paidBy,
        PaymentMethod paymentMethod,
        String paymentReference,
        String paymentNote,
        List<ItemDetail> items,
        List<ApprovalHistoryDetail> approvalHistories,
        List<AttachmentDetail> attachments,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version
) {

    public record UserSummary(
            Long id,
            String username,
            String displayName
    ) {
    }

    public record DepartmentSummary(
            Long id,
            String code,
            String name
    ) {
    }

    public record CompanySummary(
            Long id,
            String code,
            String name
    ) {
    }

    public record CustomerSummary(
            Long id,
            String code,
            String name
    ) {
    }

    public record ItemDetail(
            Long id,
            Long expenseTypeId,
            String expenseTypeCode,
            String expenseTypeName,
            CalculationType calculationType,
            Long priceSettingId,
            String priceCode,
            String priceName,
            String description,
            Integer peopleCount,
            Integer days,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal multiplier,
            BigDecimal amount,
            Map<String, Object> extraData,
            Integer sortOrder
    ) {
    }

    public record ApprovalHistoryDetail(
            Long id,
            UserSummary actor,
            ApprovalAction action,
            ApprovalStatus fromApprovalStatus,
            ApprovalStatus toApprovalStatus,
            PaymentStatus fromPaymentStatus,
            PaymentStatus toPaymentStatus,
            String comment,
            OffsetDateTime actedAt
    ) {
    }

    public record AttachmentDetail(
            Long id,
            AttachmentType attachmentType,
            String originalFilename,
            String contentType,
            Long fileSize,
            OffsetDateTime createdAt
    ) {
    }
}
