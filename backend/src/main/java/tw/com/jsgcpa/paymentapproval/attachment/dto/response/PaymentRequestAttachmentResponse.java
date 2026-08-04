package tw.com.jsgcpa.paymentapproval.attachment.dto.response;

import java.time.OffsetDateTime;

import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;

public record PaymentRequestAttachmentResponse(
        Long id,
        AttachmentType attachmentType,
        String originalFilename,
        String contentType,
        Long fileSize,
        Long uploadedById,
        String uploadedByDisplayName,
        OffsetDateTime createdAt
) {
}
