package tw.com.jsgcpa.paymentapproval.payment.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CreatePaymentDraftRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CashierReviewPaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.ManagerReviewPaymentRequest;
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
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;

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
            @Valid
            @RequestBody
            CreatePaymentDraftRequest request
    ) {
        CreatePaymentDraftResponse response =
                createPaymentDraftService.createDraft(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<SubmitPaymentDraftResponse> submitDraft(
            @PathVariable("id") Long id,
            @Valid
            @RequestBody
            SubmitPaymentDraftRequest request
    ) {
        SubmitPaymentDraftResponse response = submitPaymentDraftService.submit(
                id,
                request.version()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/manager-approve")
    public ResponseEntity<ManagerReviewPaymentResponse> managerApprove(
            @PathVariable("id") Long id,
            @Valid
            @RequestBody
            ManagerReviewPaymentRequest request
    ) {
        ManagerReviewPaymentResponse response = managerReviewPaymentService.approve(
                id,
                request.managerId(),
                request.version(),
                request.comment()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/manager-reject")
    public ResponseEntity<ManagerReviewPaymentResponse> managerReject(
            @PathVariable("id") Long id,
            @Valid
            @RequestBody
            ManagerReviewPaymentRequest request
    ) {
        ManagerReviewPaymentResponse response = managerReviewPaymentService.reject(
                id,
                request.managerId(),
                request.version(),
                request.comment()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cashier-approve")
    public ResponseEntity<CashierReviewPaymentResponse> cashierApprove(
            @PathVariable("id") Long id,
            @Valid
            @RequestBody
            CashierReviewPaymentRequest request
    ) {
        CashierReviewPaymentResponse response = cashierReviewPaymentService.approve(
                id,
                request.cashierId(),
                request.version(),
                request.comment()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cashier-reject")
    public ResponseEntity<CashierReviewPaymentResponse> cashierReject(
            @PathVariable("id") Long id,
            @Valid
            @RequestBody
            CashierReviewPaymentRequest request
    ) {
        CashierReviewPaymentResponse response = cashierReviewPaymentService.reject(
                id,
                request.cashierId(),
                request.version(),
                request.comment()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/record-payment")
    public ResponseEntity<RecordPaymentResponse> recordPayment(
            @PathVariable("id") Long id,
            @Valid
            @RequestBody
            RecordPaymentRequest request
    ) {
        RecordPaymentResponse response = recordPaymentService.record(
                id,
                request.paidById(),
                request.version(),
                request.paidAt(),
                request.paymentMethod(),
                request.paymentReference(),
                request.paymentNote()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentRequestDetailResponse> getDetail(
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(
                getPaymentRequestDetailService.getDetail(id)
        );
    }

    @GetMapping
    public ResponseEntity<PaymentRequestPageResponse> list(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String requestNo,
            @RequestParam(required = false) ApprovalStatus approvalStatus,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) RequestCategory requestCategory,
            @RequestParam(required = false) Long applicantId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate createdTo
    ) {
        return ResponseEntity.ok(listPaymentRequestsService.list(
                page,
                size,
                requestNo,
                approvalStatus,
                paymentStatus,
                requestCategory,
                applicantId,
                departmentId,
                companyId,
                customerId,
                createdFrom,
                createdTo
        ));
    }
}
