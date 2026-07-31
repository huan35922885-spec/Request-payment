package tw.com.jsgcpa.paymentapproval.payment.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestListItemResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestPageResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@Service
@Transactional(readOnly = true)
public class ListPaymentRequestsService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final PaymentRequestRepository paymentRequestRepository;

    public ListPaymentRequestsService(PaymentRequestRepository paymentRequestRepository) {
        this.paymentRequestRepository = paymentRequestRepository;
    }

    public PaymentRequestPageResponse list(
            Integer page,
            Integer size,
            String requestNo,
            ApprovalStatus approvalStatus,
            PaymentStatus paymentStatus,
            RequestCategory requestCategory,
            Long applicantId,
            Long departmentId,
            Long companyId,
            Long customerId,
            LocalDate createdFrom,
            LocalDate createdTo
    ) {
        int effectivePage = page == null ? DEFAULT_PAGE : page;
        int effectiveSize = size == null ? DEFAULT_SIZE : size;

        validatePage(effectivePage);
        validateSize(effectiveSize);
        validateFilterId(applicantId);
        validateFilterId(departmentId);
        validateFilterId(companyId);
        validateFilterId(customerId);

        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw businessError(
                    "INVALID_DATE_RANGE",
                    "createdFrom must not be after createdTo"
            );
        }

        OffsetDateTime createdFromStart = toStartOfDay(createdFrom);
        OffsetDateTime createdToExclusive = toStartOfDay(
                createdTo == null ? null : createdTo.plusDays(1)
        );
        String normalizedRequestNo = normalizeRequestNo(requestNo);

        Page<PaymentRequest> result = paymentRequestRepository.search(
                normalizedRequestNo,
                approvalStatus,
                paymentStatus,
                requestCategory,
                applicantId,
                departmentId,
                companyId,
                customerId,
                createdFromStart,
                createdToExclusive,
                PageRequest.of(
                        effectivePage,
                        effectiveSize,
                        Sort.by(
                                Sort.Order.desc("createdAt"),
                                Sort.Order.desc("id")
                        )
                )
        );

        List<PaymentRequestListItemResponse> content = result.getContent()
                .stream()
                .map(this::toListItem)
                .toList();

        return new PaymentRequestPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    private PaymentRequestListItemResponse toListItem(PaymentRequest paymentRequest) {
        return new PaymentRequestListItemResponse(
                paymentRequest.getId(),
                paymentRequest.getRequestNo(),
                paymentRequest.getApplicant().getId(),
                paymentRequest.getApplicant().getDisplayName(),
                paymentRequest.getDepartment().getId(),
                paymentRequest.getDepartment().getName(),
                paymentRequest.getSupervisorSnapshot() == null
                        ? null : paymentRequest.getSupervisorSnapshot().getId(),
                paymentRequest.getSupervisorSnapshot() == null
                        ? null : paymentRequest.getSupervisorSnapshot().getDisplayName(),
                paymentRequest.getCompany().getId(),
                paymentRequest.getCompany().getName(),
                paymentRequest.getCustomer().getId(),
                paymentRequest.getCustomer().getName(),
                paymentRequest.getRequestCategory(),
                paymentRequest.getApprovalStatus(),
                paymentRequest.getPaymentStatus(),
                paymentRequest.getTotalAmount(),
                paymentRequest.getSubmittedAt(),
                paymentRequest.getApprovedAt(),
                paymentRequest.getPaidAt(),
                paymentRequest.getCreatedAt(),
                paymentRequest.getUpdatedAt(),
                paymentRequest.getVersion()
        );
    }

    private OffsetDateTime toStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
    }

    private String normalizeRequestNo(String requestNo) {
        return requestNo == null || requestNo.isBlank() ? null : requestNo.trim();
    }

    private void validatePage(int page) {
        if (page < 0) {
            throw businessError("INVALID_PAGE", "page must be greater than or equal to zero");
        }
    }

    private void validateSize(int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw businessError("INVALID_PAGE_SIZE", "size must be between 1 and 100");
        }
    }

    private void validateFilterId(Long id) {
        if (id != null && id <= 0) {
            throw businessError("INVALID_FILTER_ID", "filter id must be greater than zero");
        }
    }

    private PaymentDraftBusinessException businessError(String code, String message) {
        return new PaymentDraftBusinessException(code, message);
    }
}
