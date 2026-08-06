package tw.com.jsgcpa.paymentapproval.payment.controller;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.PatchPaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse;
import tw.com.jsgcpa.paymentapproval.payment.service.PaymentMaintenanceService;
import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;

@RestController
@RequestMapping("/api/payment-requests")
public class PaymentRequestMaintenanceController {

    private final PaymentMaintenanceService paymentMaintenanceService;

    public PaymentRequestMaintenanceController(
            PaymentMaintenanceService paymentMaintenanceService
    ) {
        this.paymentMaintenanceService = paymentMaintenanceService;
    }

    @PatchMapping("/{id}/payment")
    public ResponseEntity<PaymentRequestDetailResponse> patchPayment(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable("id") Long id,
            @Valid @RequestBody PatchPaymentRequest request
    ) {
        return ResponseEntity.ok(paymentMaintenanceService.patchPayment(
                id,
                request,
                principal.getUserId()
        ));
    }

    @PostMapping(
            path = "/{id}/payment-proofs",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<PaymentRequestDetailResponse> uploadPaymentProofs(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable("id") Long id,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                paymentMaintenanceService.uploadPaymentProofs(
                        id,
                        files,
                        principal.getUserId()
                )
        );
    }

    @DeleteMapping("/{id}/payment-proofs/{attachmentId}")
    public ResponseEntity<Void> deletePaymentProof(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable("id") Long id,
            @PathVariable Long attachmentId
    ) {
        paymentMaintenanceService.deletePaymentProof(
                id,
                attachmentId,
                principal.getUserId()
        );
        return ResponseEntity.noContent().build();
    }
}
