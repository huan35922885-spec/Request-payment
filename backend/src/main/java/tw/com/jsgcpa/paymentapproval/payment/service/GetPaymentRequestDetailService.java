package tw.com.jsgcpa.paymentapproval.payment.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.com.jsgcpa.paymentapproval.approval.entity.ApprovalHistory;
import tw.com.jsgcpa.paymentapproval.approval.repository.ApprovalHistoryRepository;
import tw.com.jsgcpa.paymentapproval.master.entity.Company;
import tw.com.jsgcpa.paymentapproval.master.entity.Customer;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.entity.Department;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse.ApprovalHistoryDetail;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse.AttachmentDetail;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse.CompanySummary;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse.CustomerSummary;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse.DepartmentSummary;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse.ItemDetail;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse.UserSummary;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestItem;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestAttachmentRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestItemRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@Service
@Transactional(readOnly = true)
public class GetPaymentRequestDetailService {

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentRequestItemRepository paymentRequestItemRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final PaymentRequestAttachmentRepository paymentRequestAttachmentRepository;
    private final PaymentRequestReadAuthorizationService readAuthorizationService;

    public GetPaymentRequestDetailService(
            PaymentRequestRepository paymentRequestRepository,
            PaymentRequestItemRepository paymentRequestItemRepository,
            ApprovalHistoryRepository approvalHistoryRepository,
            PaymentRequestAttachmentRepository paymentRequestAttachmentRepository,
            PaymentRequestReadAuthorizationService readAuthorizationService
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.paymentRequestItemRepository = paymentRequestItemRepository;
        this.approvalHistoryRepository = approvalHistoryRepository;
        this.paymentRequestAttachmentRepository = paymentRequestAttachmentRepository;
        this.readAuthorizationService = readAuthorizationService;
    }

    public PaymentRequestDetailResponse getDetail(
            Long paymentRequestId,
            Long authenticatedUserId,
            boolean hasCashierAuthority,
            boolean hasPaymentOperatorAuthority
    ) {
        validatePaymentRequestId(paymentRequestId);

        PaymentRequest paymentRequest = paymentRequestRepository
                .findById(paymentRequestId)
                .orElseThrow(() -> businessError(
                        "PAYMENT_REQUEST_NOT_FOUND",
                        "找不到請款單"
                ));

        if (!readAuthorizationService.canReadDetail(
                paymentRequest,
                authenticatedUserId,
                hasCashierAuthority,
                hasPaymentOperatorAuthority
        )) {
            throw businessError(
                    "PAYMENT_REQUEST_NOT_FOUND",
                    "找不到請款單"
            );
        }

        List<PaymentRequestItem> items = paymentRequestItemRepository
                .findByPaymentRequest_IdOrderBySortOrderAscIdAsc(paymentRequestId);
        List<ApprovalHistory> approvalHistories = approvalHistoryRepository
                .findByPaymentRequest_IdOrderByActedAtAscIdAsc(paymentRequestId);
        List<PaymentRequestAttachment> attachments =
                paymentRequestAttachmentRepository
                        .findByPaymentRequest_IdOrderByCreatedAtAscIdAsc(
                                paymentRequestId
                        );

        return toResponse(
                paymentRequest,
                items,
                approvalHistories,
                attachments
        );
    }

    private PaymentRequestDetailResponse toResponse(
            PaymentRequest paymentRequest,
            List<PaymentRequestItem> items,
            List<ApprovalHistory> approvalHistories,
            List<PaymentRequestAttachment> attachments
    ) {
        return new PaymentRequestDetailResponse(
                paymentRequest.getId(),
                paymentRequest.getRequestNo(),
                toUserSummary(paymentRequest.getApplicant()),
                toDepartmentSummary(paymentRequest.getDepartment()),
                toUserSummary(paymentRequest.getSupervisorSnapshot()),
                toCompanySummary(paymentRequest.getCompany()),
                toCustomerSummary(paymentRequest.getCustomer()),
                paymentRequest.getRequestCategory(),
                paymentRequest.getReason(),
                paymentRequest.getApprovalStatus(),
                paymentRequest.getPaymentStatus(),
                paymentRequest.getTotalAmount(),
                paymentRequest.getSubmittedAt(),
                paymentRequest.getApprovedAt(),
                toUserSummary(paymentRequest.getApprovedBy()),
                paymentRequest.getRejectedAt(),
                paymentRequest.getClosedAt(),
                paymentRequest.getPaidAt(),
                toUserSummary(paymentRequest.getPaidBy()),
                paymentRequest.getPaymentMethod(),
                paymentRequest.getPaymentReference(),
                paymentRequest.getPaymentNote(),
                mapItems(items),
                mapApprovalHistories(approvalHistories),
                mapAttachments(attachments),
                paymentRequest.getCreatedAt(),
                paymentRequest.getUpdatedAt(),
                paymentRequest.getVersion()
        );
    }

    private List<ItemDetail> mapItems(List<PaymentRequestItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(this::toItemDetail)
                .toList();
    }

    private ItemDetail toItemDetail(PaymentRequestItem item) {
        ExpenseType expenseType = item.getExpenseType();
        ExpensePriceSetting priceSetting = item.getPriceSetting();
        return new ItemDetail(
                item.getId(),
                expenseType == null ? null : expenseType.getId(),
                expenseType == null ? null : expenseType.getCode(),
                expenseType == null ? null : expenseType.getName(),
                expenseType == null ? null : expenseType.getCalculationType(),
                priceSetting == null ? null : priceSetting.getId(),
                priceSetting == null ? null : priceSetting.getPriceCode(),
                priceSetting == null ? null : priceSetting.getPriceName(),
                item.getDescription(),
                item.getPeopleCount(),
                item.getDays(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getMultiplier(),
                item.getAmount(),
                item.getExtraData(),
                item.getSortOrder()
        );
    }

    private List<ApprovalHistoryDetail> mapApprovalHistories(
            List<ApprovalHistory> approvalHistories
    ) {
        if (approvalHistories == null) {
            return List.of();
        }
        return approvalHistories.stream()
                .map(history -> new ApprovalHistoryDetail(
                        history.getId(),
                        toUserSummary(history.getActor()),
                        history.getAction(),
                        history.getFromApprovalStatus(),
                        history.getToApprovalStatus(),
                        history.getFromPaymentStatus(),
                        history.getToPaymentStatus(),
                        history.getComment(),
                        history.getActedAt()
                ))
                .toList();
    }

    private List<AttachmentDetail> mapAttachments(
            List<PaymentRequestAttachment> attachments
    ) {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream()
                .map(this::toAttachmentDetail)
                .toList();
    }

    private AttachmentDetail toAttachmentDetail(
            PaymentRequestAttachment attachment
    ) {
        AppUser uploadedBy = attachment.getUploadedBy();
        if (uploadedBy == null) {
            throw new IllegalStateException(
                    "Payment request attachment is missing uploadedBy"
            );
        }
        return new AttachmentDetail(
                attachment.getId(),
                attachment.getAttachmentType(),
                attachment.getOriginalFilename(),
                attachment.getContentType(),
                attachment.getFileSize(),
                uploadedBy.getId(),
                uploadedBy.getDisplayName(),
                attachment.getCreatedAt()
        );
    }

    private UserSummary toUserSummary(AppUser user) {
        if (user == null) {
            return null;
        }
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName());
    }

    private DepartmentSummary toDepartmentSummary(Department department) {
        if (department == null) {
            return null;
        }
        return new DepartmentSummary(
                department.getId(),
                department.getCode(),
                department.getName()
        );
    }

    private CompanySummary toCompanySummary(Company company) {
        if (company == null) {
            return null;
        }
        return new CompanySummary(company.getId(), company.getCode(), company.getName());
    }

    private CustomerSummary toCustomerSummary(Customer customer) {
        if (customer == null) {
            return null;
        }
        return new CustomerSummary(customer.getId(), customer.getCode(), customer.getName());
    }

    private void validatePaymentRequestId(Long paymentRequestId) {
        if (paymentRequestId == null || paymentRequestId <= 0) {
            throw businessError(
                    "INVALID_PAYMENT_REQUEST_ID",
                    "Payment request id must be greater than zero"
            );
        }
    }

    private PaymentDraftBusinessException businessError(
            String code,
            String message
    ) {
        return new PaymentDraftBusinessException(code, message);
    }
}
