package tw.com.jsgcpa.paymentapproval.attachment.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.com.jsgcpa.paymentapproval.attachment.dto.response.DownloadPaymentRequestAttachmentResult;
import tw.com.jsgcpa.paymentapproval.attachment.exception.AttachmentStorageException;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentNotFoundException;
import tw.com.jsgcpa.paymentapproval.attachment.storage.AttachmentStorageService;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestAttachmentRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;
import tw.com.jsgcpa.paymentapproval.payment.service.PaymentRequestReadAuthorizationService;

@Service
@Transactional(readOnly = true)
public class DownloadPaymentRequestAttachmentService {

    private static final String PDF = "application/pdf";
    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentRequestAttachmentRepository attachmentRepository;
    private final PaymentRequestReadAuthorizationService readAuthorizationService;
    private final AttachmentStorageService attachmentStorageService;

    public DownloadPaymentRequestAttachmentService(
            PaymentRequestRepository paymentRequestRepository,
            PaymentRequestAttachmentRepository attachmentRepository,
            PaymentRequestReadAuthorizationService readAuthorizationService,
            AttachmentStorageService attachmentStorageService
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.attachmentRepository = attachmentRepository;
        this.readAuthorizationService = readAuthorizationService;
        this.attachmentStorageService = attachmentStorageService;
    }

    public DownloadPaymentRequestAttachmentResult download(
            Long paymentRequestId,
            Long attachmentId,
            Long authenticatedUserId,
            boolean hasCashierAuthority,
            boolean hasPaymentOperatorAuthority
    ) {
        validateId(paymentRequestId);
        validateId(attachmentId);

        PaymentRequest paymentRequest = paymentRequestRepository
                .findById(paymentRequestId)
                .orElseThrow(PaymentRequestAttachmentNotFoundException::new);

        if (!readAuthorizationService.canReadDetail(
                paymentRequest,
                authenticatedUserId,
                hasCashierAuthority,
                hasPaymentOperatorAuthority
        )) {
            throw new PaymentRequestAttachmentNotFoundException();
        }

        PaymentRequestAttachment attachment = attachmentRepository
                .findById(attachmentId)
                .orElseThrow(PaymentRequestAttachmentNotFoundException::new);

        if (attachment.getPaymentRequest() == null
                || !Objects.equals(
                        attachment.getPaymentRequest().getId(),
                        paymentRequestId
                )) {
            throw new PaymentRequestAttachmentNotFoundException();
        }

        Long metadataFileSize = attachment.getFileSize();
        if (metadataFileSize == null || metadataFileSize <= 0) {
            throw storageReadFailure();
        }

        long actualFileSize = attachmentStorageService.size(
                attachment.getStoragePath()
        );
        if (actualFileSize != metadataFileSize) {
            throw storageReadFailure();
        }

        InputStream inputStream = attachmentStorageService.load(
                attachment.getStoragePath()
        );
        if (inputStream == null) {
            throw storageReadFailure();
        }

        return new DownloadPaymentRequestAttachmentResult(
                new InputStreamResource(inputStream),
                safeFilename(
                        attachment.getOriginalFilename(),
                        attachmentId,
                        attachment.getContentType()
                ),
                safeContentType(attachment.getContentType()),
                metadataFileSize
        );
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new PaymentRequestAttachmentNotFoundException();
        }
    }

    private AttachmentStorageException storageReadFailure() {
        return new AttachmentStorageException(
                "ATTACHMENT_STORAGE_READ_FAILED",
                "附件檔案暫時無法讀取"
        );
    }

    private MediaType safeContentType(String contentType) {
        if (PDF.equalsIgnoreCase(contentType)) {
            return MediaType.APPLICATION_PDF;
        }
        if (JPEG.equalsIgnoreCase(contentType)) {
            return MediaType.IMAGE_JPEG;
        }
        if (PNG.equalsIgnoreCase(contentType)) {
            return MediaType.IMAGE_PNG;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String safeFilename(
            String originalFilename,
            Long attachmentId,
            String contentType
    ) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return fallbackFilename(attachmentId, contentType);
        }

        String candidate = originalFilename.trim();
        if (candidate.isEmpty()
                || candidate.indexOf('\r') >= 0
                || candidate.indexOf('\n') >= 0
                || candidate.indexOf('\0') >= 0
                || candidate.indexOf('/') >= 0
                || candidate.indexOf('\\') >= 0
                || candidate.contains("..")
                || containsControlCharacter(candidate)) {
            return fallbackFilename(attachmentId, contentType);
        }
        return truncateUtf8(candidate, 255);
    }

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private String fallbackFilename(Long attachmentId, String contentType) {
        String extension = switch (contentType == null
                ? ""
                : contentType.toLowerCase()) {
            case PDF -> ".pdf";
            case JPEG -> ".jpg";
            case PNG -> ".png";
            default -> ".bin";
        };
        return "attachment-" + attachmentId + extension;
    }

    private String truncateUtf8(String value, int maxBytes) {
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String next = new String(Character.toChars(codePoint));
            if ((result + next).getBytes(StandardCharsets.UTF_8).length
                    > maxBytes) {
                break;
            }
            result.append(next);
            offset += Character.charCount(codePoint);
        }
        return result.toString().isBlank()
                ? "attachment.bin"
                : result.toString();
    }
}
