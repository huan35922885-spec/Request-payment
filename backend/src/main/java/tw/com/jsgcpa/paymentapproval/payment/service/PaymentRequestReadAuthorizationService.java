package tw.com.jsgcpa.paymentapproval.payment.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentRequestListScope;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;

/**
 * Minimum read policy for a single payment request detail.
 *
 * <p>This policy intentionally receives only application data and primitive
 * authority flags. It must not depend on Spring Security's context types.</p>
 */
@Service
public class PaymentRequestReadAuthorizationService {

    public Long resolveApplicantIdForList(
            PaymentRequestListScope scope,
            Long authenticatedUserId
    ) {
        if (scope != PaymentRequestListScope.MY_REQUESTS) {
            return null;
        }
        if (authenticatedUserId == null || authenticatedUserId <= 0) {
            throw new PaymentDraftBusinessException(
                    "PAYMENT_REQUEST_LIST_AUTHENTICATION_REQUIRED",
                    "目前登入者無效"
            );
        }
        return authenticatedUserId;
    }

    public Long resolveSupervisorIdForList(
            PaymentRequestListScope scope,
            Long authenticatedUserId
    ) {
        if (scope != PaymentRequestListScope.MANAGER_PENDING) {
            return null;
        }
        requireAuthenticatedUser(authenticatedUserId);
        return authenticatedUserId;
    }

    public ApprovalStatus resolveApprovalStatusForList(
            PaymentRequestListScope scope,
            ApprovalStatus requestedApprovalStatus
    ) {
        if (scope != PaymentRequestListScope.MANAGER_PENDING
                && scope != PaymentRequestListScope.CASHIER_PENDING
                && scope != PaymentRequestListScope.PAYMENT_PENDING) {
            return requestedApprovalStatus;
        }
        ApprovalStatus requiredStatus = switch (scope) {
            case MANAGER_PENDING -> ApprovalStatus.PENDING_MANAGER;
            case CASHIER_PENDING -> ApprovalStatus.PENDING_CASHIER;
            case PAYMENT_PENDING -> ApprovalStatus.APPROVED;
            default -> requestedApprovalStatus;
        };
        if (requestedApprovalStatus != null
                && requestedApprovalStatus != requiredStatus) {
            throw scopeConflict(
                    scope + " 不接受此 approvalStatus 篩選"
            );
        }
        return requiredStatus;
    }

    public void requireCashierAuthority(
            PaymentRequestListScope scope,
            boolean hasCashierAuthority
    ) {
        if (scope == PaymentRequestListScope.CASHIER_PENDING
                && !hasCashierAuthority) {
            throw new PaymentDraftBusinessException(
                    "PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN",
                    "目前登入者沒有出納待辦查看權限"
            );
        }
    }

    public void requirePaymentOperatorAuthority(
            PaymentRequestListScope scope,
            boolean hasPaymentOperatorAuthority
    ) {
        if (scope == PaymentRequestListScope.PAYMENT_PENDING
                && !hasPaymentOperatorAuthority) {
            throw new PaymentDraftBusinessException(
                    "PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN",
                    "目前登入者沒有付款待辦查看權限"
            );
        }
    }

    public PaymentStatus resolvePaymentStatusForList(
            PaymentRequestListScope scope,
            PaymentStatus requestedPaymentStatus
    ) {
        if (scope != PaymentRequestListScope.PAYMENT_PENDING) {
            return requestedPaymentStatus;
        }
        if (requestedPaymentStatus != null
                && requestedPaymentStatus != PaymentStatus.UNPAID) {
            throw scopeConflict("PAYMENT_PENDING 不接受此 paymentStatus 篩選");
        }
        return PaymentStatus.UNPAID;
    }

    public void validateSupervisorFilter(
            PaymentRequestListScope scope,
            Long requestedSupervisorId
    ) {
        if (scope == PaymentRequestListScope.MANAGER_PENDING
                && requestedSupervisorId != null) {
            throw scopeConflict("MANAGER_PENDING 不接受 supervisorId 篩選");
        }
    }

    public boolean canReadDetail(
            PaymentRequest paymentRequest,
            Long authenticatedUserId,
            boolean hasCashierAuthority,
            boolean hasPaymentOperatorAuthority
    ) {
        if (paymentRequest == null || authenticatedUserId == null) {
            return false;
        }

        if (paymentRequest.getApplicant() != null
                && Objects.equals(
                        paymentRequest.getApplicant().getId(),
                        authenticatedUserId
                )) {
            return true;
        }

        if (paymentRequest.getApprovalStatus() == ApprovalStatus.PENDING_MANAGER
                && paymentRequest.getSupervisorSnapshot() != null
                && Objects.equals(
                        paymentRequest.getSupervisorSnapshot().getId(),
                        authenticatedUserId
                )) {
            return true;
        }

        if (hasCashierAuthority
                && paymentRequest.getApprovalStatus() == ApprovalStatus.PENDING_CASHIER) {
            return true;
        }

        return hasPaymentOperatorAuthority
                && paymentRequest.getApprovalStatus() == ApprovalStatus.APPROVED
                && paymentRequest.getPaymentStatus() == PaymentStatus.UNPAID;
    }

    private void requireAuthenticatedUser(Long authenticatedUserId) {
        if (authenticatedUserId == null || authenticatedUserId <= 0) {
            throw new PaymentDraftBusinessException(
                    "PAYMENT_REQUEST_LIST_AUTHENTICATION_REQUIRED",
                    "目前登入者無效"
            );
        }
    }

    private PaymentDraftBusinessException scopeConflict(String message) {
        return new PaymentDraftBusinessException(
                "PAYMENT_REQUEST_LIST_SCOPE_FILTER_CONFLICT",
                message
        );
    }
}
