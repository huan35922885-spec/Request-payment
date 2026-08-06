package tw.com.jsgcpa.paymentapproval.attachment.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import tw.com.jsgcpa.paymentapproval.attachment.dto.response.PaymentRequestAttachmentResponse;
import tw.com.jsgcpa.paymentapproval.attachment.dto.response.DownloadPaymentRequestAttachmentResult;
import tw.com.jsgcpa.paymentapproval.attachment.service.DownloadPaymentRequestAttachmentService;
import tw.com.jsgcpa.paymentapproval.attachment.service.DeletePaymentRequestAttachmentService;
import tw.com.jsgcpa.paymentapproval.attachment.service.UploadPaymentRequestAttachmentService;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;
import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;
import tw.com.jsgcpa.paymentapproval.security.enums.SecurityRole;

@RestController
@RequestMapping("/api/payment-requests")
public class PaymentRequestAttachmentController {

    private final UploadPaymentRequestAttachmentService uploadService;
    private final DownloadPaymentRequestAttachmentService downloadService;
    private final DeletePaymentRequestAttachmentService deleteService;

    public PaymentRequestAttachmentController(
            UploadPaymentRequestAttachmentService uploadService,
            DownloadPaymentRequestAttachmentService downloadService,
            DeletePaymentRequestAttachmentService deleteService
    ) {
        this.uploadService = uploadService;
        this.downloadService = downloadService;
        this.deleteService = deleteService;
    }

    @PostMapping(
            path = "/{paymentRequestId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<PaymentRequestAttachmentResponse> upload(
            @PathVariable Long paymentRequestId,
            @RequestParam("attachmentType") AttachmentType attachmentType,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        PaymentRequestAttachmentResponse response = uploadService.upload(
                paymentRequestId,
                principal.getUserId(),
                attachmentType,
                file
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{paymentRequestId}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long paymentRequestId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        DownloadPaymentRequestAttachmentResult result = downloadService.download(
                paymentRequestId,
                attachmentId,
                principal.getUserId(),
                hasAuthority(principal, SecurityRole.CASHIER),
                false
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(result.contentType());
        headers.setContentLength(result.fileSize());
        headers.setContentDisposition(
                org.springframework.http.ContentDisposition.attachment()
                        .filename(
                                result.safeOriginalFilename(),
                                StandardCharsets.UTF_8
                        )
                        .build()
        );
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(result.resource());
    }

    @DeleteMapping("/{paymentRequestId}/attachments/{attachmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long paymentRequestId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        deleteService.delete(
                paymentRequestId,
                attachmentId,
                principal.getUserId()
        );
        return ResponseEntity.noContent().build();
    }

    private boolean hasAuthority(
            AuthenticatedUserPrincipal principal,
            SecurityRole role
    ) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role.name()::equals);
    }
}
