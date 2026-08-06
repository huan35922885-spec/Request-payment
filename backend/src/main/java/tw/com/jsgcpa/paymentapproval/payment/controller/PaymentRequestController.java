package tw.com.jsgcpa.paymentapproval.payment.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CreatePaymentDraftRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CashierReviewPaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.ManagerReviewPaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.PaymentRequestListQuery;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.RecordPaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.SubmitPaymentDraftRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.CreatePaymentDraftResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.CashierReviewPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.ManagerReviewPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestPageResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.RecordPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.SubmitPaymentDraftResponse;
import tw.com.jsgcpa.paymentapproval.payment.service.CashierReviewPaymentService;
import tw.com.jsgcpa.paymentapproval.payment.service.CreatePaymentDraftService;
import tw.com.jsgcpa.paymentapproval.payment.service.ManagerReviewPaymentService;
import tw.com.jsgcpa.paymentapproval.payment.service.RecordPaymentService;
import tw.com.jsgcpa.paymentapproval.payment.service.GetPaymentRequestDetailService;
import tw.com.jsgcpa.paymentapproval.payment.service.ListPaymentRequestsService;
import tw.com.jsgcpa.paymentapproval.payment.service.SubmitPaymentDraftService;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentRequestListScope;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;
import tw.com.jsgcpa.paymentapproval.security.enums.SecurityRole;

@RestController
@RequestMapping("/api/payment-requests")
public class PaymentRequestController {

    private final CreatePaymentDraftService createPaymentDraftService;
    private final SubmitPaymentDraftService submitPaymentDraftService;
    private final ManagerReviewPaymentService managerReviewPaymentService;
    private final CashierReviewPaymentService cashierReviewPaymentService;
    private final RecordPaymentService recordPaymentService;
    private final GetPaymentRequestDetailService getPaymentRequestDetailService;
    private final ListPaymentRequestsService listPaymentRequestsService;

    public PaymentRequestController(
            CreatePaymentDraftService createPaymentDraftService,
            SubmitPaymentDraftService submitPaymentDraftService,
            ManagerReviewPaymentService managerReviewPaymentService,
            CashierReviewPaymentService cashierReviewPaymentService,
            RecordPaymentService recordPaymentService,
            GetPaymentRequestDetailService getPaymentRequestDetailService,
            ListPaymentRequestsService listPaymentRequestsService
    ) {
        this.createPaymentDraftService = createPaymentDraftService;
        this.submitPaymentDraftService = submitPaymentDraftService;
        this.managerReviewPaymentService = managerReviewPaymentService;
        this.cashierReviewPaymentService = cashierReviewPaymentService;
        this.recordPaymentService = recordPaymentService;
        this.getPaymentRequestDetailService = getPaymentRequestDetailService;
        this.listPaymentRequestsService = listPaymentRequestsService;
    }

    @PostMapping("/drafts")
    public ResponseEntity<CreatePaymentDraftResponse> createDraft(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            @Valid
            @RequestBody
            CreatePaymentDraftRequest request
    ) {
        CreatePaymentDraftResponse response =
                createPaymentDraftService.createDraft(
                        principal.getUserId(),
                        request
                );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<SubmitPaymentDraftResponse> submitDraft(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            @PathVariable("id") Long id,
            @Valid
            @RequestBody
            SubmitPaymentDraftRequest request
    ) {
        SubmitPaymentDraftResponse response = submitPaymentDraftService.submit(
                id,
                principal.getUserId(),
                request.version()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/manager-approve")
    public ResponseEntity<ManagerReviewPaymentResponse> managerApprove(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            @PathVariable("id") Long id,
            @Valid
            @RequestBody
            ManagerReviewPaymentRequest request
    ) {
        ManagerReviewPaymentResponse response = managerReviewPaymentService.approve(
                id,
                principal.getUserId(),
                request.version(),
                request.comment()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/manager-reject")
    public ResponseEntity<ManagerReviewPaymentResponse> managerReject(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            @PathVariable("id") Long id,
            @Valid
            @RequestBody
            ManagerReviewPaymentRequest request
    ) {
        ManagerReviewPaymentResponse response = managerReviewPaymentService.reject(
                id,
                principal.getUserId(),
                request.version(),
                request.comment()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cashier-approve")
    public ResponseEntity<CashierReviewPaymentResponse> cashierApprove(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            @PathVariable("id") Long id,
            @Valid
            @RequestBody
            CashierReviewPaymentRequest request
    ) {
        CashierReviewPaymentResponse response = cashierReviewPaymentService.approve(
                id,
                principal.getUserId(),
                request
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cashier-reject")
    public ResponseEntity<CashierReviewPaymentResponse> cashierReject(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            @PathVariable("id") Long id,
            @Valid
            @RequestBody
            CashierReviewPaymentRequest request
    ) {
        CashierReviewPaymentResponse response = cashierReviewPaymentService.reject(
                id,
                principal.getUserId(),
                request
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping(
            path = "/{id}/record-payment",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<RecordPaymentResponse> recordPayment(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            @PathVariable("id") Long id,
            @Valid
            @RequestPart("request")
            RecordPaymentRequest request,
            @RequestPart(value = "files", required = false)
            List<MultipartFile> paymentProofFiles,
            @RequestPart(value = "file", required = false)
            MultipartFile paymentProofFile
    ) {
        List<MultipartFile> files = mergeProofFiles(paymentProofFiles, paymentProofFile);
        RecordPaymentResponse response = recordPaymentService.recordPayment(
                id,
                request,
                files,
                principal.getUserId()
        );
        return ResponseEntity.ok(response);
    }

    private static List<MultipartFile> mergeProofFiles(
            List<MultipartFile> paymentProofFiles,
            MultipartFile paymentProofFile
    ) {
        List<MultipartFile> files = new ArrayList<>();
        if (paymentProofFiles != null) {
            files.addAll(paymentProofFiles);
        }
        if (paymentProofFile != null && !paymentProofFile.isEmpty()) {
            files.add(paymentProofFile);
        }
        return files;
    }

    /** JSON clients must migrate to multipart so a payment proof is always supplied. */
    @PostMapping(
            path = "/{id}/record-payment",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<RecordPaymentResponse> recordPaymentJson(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            @PathVariable("id") Long id,
            @Valid
            @RequestBody
            RecordPaymentRequest request
    ) {
        return ResponseEntity.ok(recordPaymentService.recordPayment(
                id,
                principal.getUserId(),
                request
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentRequestDetailResponse> getDetail(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(
                getPaymentRequestDetailService.getDetail(
                        id,
                        principal.getUserId(),
                        hasAuthority(principal, SecurityRole.CASHIER),
                        false
                )
        );
    }

    private boolean hasAuthority(
            AuthenticatedUserPrincipal principal,
            SecurityRole role
    ) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role.name()::equals);
    }

    @GetMapping
    public ResponseEntity<PaymentRequestPageResponse> list(
            @AuthenticationPrincipal
            AuthenticatedUserPrincipal principal,
            @RequestParam(required = false) PaymentRequestListScope scope,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String requestNo,
            @RequestParam(required = false) ApprovalStatus approvalStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) RequestCategory requestCategory,
            @RequestParam(required = false) Long applicantId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long supervisorId,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate createdTo
    ) {
        if (scope == null) {
            throw new PaymentDraftBusinessException(
                    "PAYMENT_REQUEST_LIST_SCOPE_REQUIRED",
                    "請指定請款列表查詢範圍"
            );
        }

        PaymentRequestListQuery query = new PaymentRequestListQuery(
                page,
                size,
                requestNo,
                approvalStatus,
                paymentStatus,
                requestCategory,
                applicantId,
                departmentId,
                supervisorId,
                companyId,
                customerId,
                createdFrom,
                createdTo
        );

        boolean hasCashierAuthority = hasAuthority(
                principal,
                SecurityRole.CASHIER
        );
        return ResponseEntity.ok(listPaymentRequestsService.list(
                query,
                scope,
                principal.getUserId(),
                hasCashierAuthority,
                false
        ));
    }
}
