package tw.com.jsgcpa.paymentapproval.attachment.dto.response;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record DownloadPaymentRequestAttachmentResult(
        Resource resource,
        String safeOriginalFilename,
        MediaType contentType,
        long fileSize
) {
}
