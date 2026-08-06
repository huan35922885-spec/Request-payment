package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.test.util.ReflectionTestUtils;

import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentRequestListScope;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;

class PaymentRequestReadAuthorizationServiceTest {

    private static final Long APPLICANT_ID = 1L;
    private static final Long SUPERVISOR_ID = 2L;
    private static final Long OTHER_USER_ID = 9L;

    private final PaymentRequestReadAuthorizationService service =
            new PaymentRequestReadAuthorizationService();

    @ParameterizedTest
    @MethodSource("allRequestStates")
    void applicantCanReadOwnRequestInEveryState(
            ApprovalStatus approvalStatus,
            PaymentStatus paymentStatus
    ) {
        PaymentRequest request = request(
                APPLICANT_ID,
                SUPERVISOR_ID,
                approvalStatus,
                paymentStatus
        );

        assertTrue(service.canReadDetail(
                request,
                APPLICANT_ID,
                false,
                false
        ));
    }

    @ParameterizedTest
    @MethodSource("allRequestStates")
    void otherUserDoesNotReadApplicantRequestWithoutAnotherRule(
            ApprovalStatus approvalStatus,
            PaymentStatus paymentStatus
    ) {
        PaymentRequest request = request(
                APPLICANT_ID,
                SUPERVISOR_ID,
                approvalStatus,
                paymentStatus
        );

        assertFalse(service.canReadDetail(
                request,
                OTHER_USER_ID,
                false,
                false
        ));
    }

    @Test
    void supervisorSnapshotOwnerCanReadPendingManager() {
        assertTrue(service.canReadDetail(
                request(
                        APPLICANT_ID,
                        SUPERVISOR_ID,
                        ApprovalStatus.PENDING_MANAGER,
                        PaymentStatus.UNPAID
                ),
                SUPERVISOR_ID,
                false,
                false
        ));
    }

    @ParameterizedTest
    @MethodSource("supervisorCannotReadOtherStates")
    void supervisorSnapshotOwnerCannotReadOtherStates(ApprovalStatus status) {
        assertFalse(service.canReadDetail(
                request(
                        APPLICANT_ID,
                        SUPERVISOR_ID,
                        status,
                        PaymentStatus.UNPAID
                ),
                SUPERVISOR_ID,
                false,
                false
        ));
    }

    @Test
    void missingSupervisorSnapshotDoesNotGrantAccess() {
        PaymentRequest request = request(
                APPLICANT_ID,
                null,
                ApprovalStatus.PENDING_MANAGER,
                PaymentStatus.UNPAID
        );

        assertFalse(service.canReadDetail(
                request,
                SUPERVISOR_ID,
                false,
                false
        ));
    }

    @Test
    void currentSupervisorWithoutSnapshotRelationDoesNotGrantAccess() {
        PaymentRequest request = request(
                APPLICANT_ID,
                OTHER_USER_ID,
                ApprovalStatus.PENDING_MANAGER,
                PaymentStatus.UNPAID
        );

        assertFalse(service.canReadDetail(
                request,
                SUPERVISOR_ID,
                false,
                false
        ));
    }

    @Test
    void cashierCanReadPendingCashier() {
        assertTrue(service.canReadDetail(
                request(
                        APPLICANT_ID,
                        SUPERVISOR_ID,
                        ApprovalStatus.PENDING_CASHIER,
                        PaymentStatus.UNPAID
                ),
                OTHER_USER_ID,
                true,
                false
        ));
    }

    @ParameterizedTest
    @MethodSource("cashierCannotReadOtherStates")
    void cashierCannotReadOtherStates(ApprovalStatus status) {
        assertFalse(service.canReadDetail(
                request(
                        APPLICANT_ID,
                        SUPERVISOR_ID,
                        status,
                        PaymentStatus.UNPAID
                ),
                OTHER_USER_ID,
                true,
                false
        ));
    }

    @Test
    void cashierCanReadApprovedUnpaid() {
        assertTrue(service.canReadDetail(
                request(
                        APPLICANT_ID,
                        SUPERVISOR_ID,
                        ApprovalStatus.APPROVED,
                        PaymentStatus.UNPAID
                ),
                OTHER_USER_ID,
                true,
                false
        ));
    }

    @Test
    void cashierCanReadApprovedPaid() {
        assertTrue(service.canReadDetail(
                request(
                        APPLICANT_ID,
                        SUPERVISOR_ID,
                        ApprovalStatus.APPROVED,
                        PaymentStatus.PAID
                ),
                OTHER_USER_ID,
                true,
                false
        ));
    }

    @ParameterizedTest
    @MethodSource("cashierCannotReadNonApprovedPaymentStates")
    void cashierCannotReadNonApprovedPaymentStates(
            ApprovalStatus approvalStatus,
            PaymentStatus paymentStatus
    ) {
        assertFalse(service.canReadDetail(
                request(
                        APPLICANT_ID,
                        SUPERVISOR_ID,
                        approvalStatus,
                        paymentStatus
                ),
                OTHER_USER_ID,
                true,
                false
        ));
    }

    @Test
    void multipleAuthoritiesUseOrRules() {
        assertTrue(service.canReadDetail(
                request(
                        APPLICANT_ID,
                        SUPERVISOR_ID,
                        ApprovalStatus.PENDING_CASHIER,
                        PaymentStatus.UNPAID
                ),
                OTHER_USER_ID,
                true,
                true
        ));
        assertTrue(service.canReadDetail(
                request(
                        APPLICANT_ID,
                        SUPERVISOR_ID,
                        ApprovalStatus.APPROVED,
                        PaymentStatus.UNPAID
                ),
                OTHER_USER_ID,
                true,
                true
        ));
        assertTrue(service.canReadDetail(
                request(
                        APPLICANT_ID,
                        SUPERVISOR_ID,
                        ApprovalStatus.APPROVED,
                        PaymentStatus.PAID
                ),
                OTHER_USER_ID,
                true,
                true
        ));
        assertTrue(service.canReadDetail(
                request(
                        APPLICANT_ID,
                        SUPERVISOR_ID,
                        ApprovalStatus.APPROVED,
                        PaymentStatus.PAID
                ),
                APPLICANT_ID,
                true,
                true
        ));
    }

    @Test
    void nullRequestOrUserDoesNotGrantAccess() {
        assertFalse(service.canReadDetail(null, APPLICANT_ID, false, false));
        assertFalse(service.canReadDetail(
                request(
                        APPLICANT_ID,
                        SUPERVISOR_ID,
                        ApprovalStatus.DRAFT,
                        PaymentStatus.UNPAID
                ),
                null,
                false,
                false
        ));
    }

    @Test
    void myRequestsResolvesApplicantIdFromAuthenticatedUser() {
        assertEquals(
                42L,
                service.resolveApplicantIdForList(
                        PaymentRequestListScope.MY_REQUESTS,
                        42L
                )
        );
    }

    @Test
    void legacyListDoesNotResolveApplicantId() {
        assertNull(service.resolveApplicantIdForList(null, 42L));
    }

    @Test
    void myRequestsRejectsMissingAuthenticatedUser() {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.resolveApplicantIdForList(
                        PaymentRequestListScope.MY_REQUESTS,
                        null
                )
        );

        assertEquals(
                "PAYMENT_REQUEST_LIST_AUTHENTICATION_REQUIRED",
                exception.getCode()
        );
    }

    @Test
    void managerPendingResolvesSupervisorIdFromAuthenticatedUser() {
        assertEquals(
                42L,
                service.resolveSupervisorIdForList(
                        PaymentRequestListScope.MANAGER_PENDING,
                        42L
                )
        );
    }

    @Test
    void managerPendingForcesPendingManagerStatus() {
        assertEquals(
                ApprovalStatus.PENDING_MANAGER,
                service.resolveApprovalStatusForList(
                        PaymentRequestListScope.MANAGER_PENDING,
                        null
                )
        );
        assertEquals(
                ApprovalStatus.PENDING_MANAGER,
                service.resolveApprovalStatusForList(
                        PaymentRequestListScope.MANAGER_PENDING,
                        ApprovalStatus.PENDING_MANAGER
                )
        );
    }

    @Test
    void managerPendingRejectsAnySupervisorFilter() {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.validateSupervisorFilter(
                        PaymentRequestListScope.MANAGER_PENDING,
                        42L
                )
        );

        assertEquals(
                "PAYMENT_REQUEST_LIST_SCOPE_FILTER_CONFLICT",
                exception.getCode()
        );
    }

    @Test
    void managerPendingRejectsApprovalStatusOutsideFixedScope() {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.resolveApprovalStatusForList(
                        PaymentRequestListScope.MANAGER_PENDING,
                        ApprovalStatus.APPROVED
                )
        );

        assertEquals(
                "PAYMENT_REQUEST_LIST_SCOPE_FILTER_CONFLICT",
                exception.getCode()
        );
    }

    @Test
    void cashierPendingRequiresCashierAuthority() {
        assertDoesNotThrow(() -> service.requireCashierAuthority(
                PaymentRequestListScope.CASHIER_PENDING,
                true
        ));
    }

    @Test
    void cashierPendingWithoutCashierAuthorityIsForbidden() {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.requireCashierAuthority(
                        PaymentRequestListScope.CASHIER_PENDING,
                        false
                )
        );

        assertEquals(
                "PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN",
                exception.getCode()
        );
        assertEquals(
                "目前登入者沒有出納待辦查看權限",
                exception.getMessage()
        );
    }

    @Test
    void cashierPendingForcesPendingCashierStatus() {
        assertEquals(
                ApprovalStatus.PENDING_CASHIER,
                service.resolveApprovalStatusForList(
                        PaymentRequestListScope.CASHIER_PENDING,
                        null
                )
        );
        assertEquals(
                ApprovalStatus.PENDING_CASHIER,
                service.resolveApprovalStatusForList(
                        PaymentRequestListScope.CASHIER_PENDING,
                        ApprovalStatus.PENDING_CASHIER
                )
        );
    }

    @Test
    void cashierPendingRejectsApprovalStatusOutsideFixedScope() {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.resolveApprovalStatusForList(
                        PaymentRequestListScope.CASHIER_PENDING,
                        ApprovalStatus.APPROVED
                )
        );

        assertEquals(
                "PAYMENT_REQUEST_LIST_SCOPE_FILTER_CONFLICT",
                exception.getCode()
        );
    }

    @Test
    void paymentOperatorAuthorityDoesNotSatisfyCashierScope() {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.requireCashierAuthority(
                        PaymentRequestListScope.CASHIER_PENDING,
                        false
                )
        );

        assertEquals(
                "PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN",
                exception.getCode()
        );
    }

    @Test
    void paymentPendingRequiresCashierAuthority() {
        assertDoesNotThrow(() -> service.requireCashierAuthority(
                PaymentRequestListScope.PAYMENT_PENDING,
                true
        ));
    }

    @Test
    void paymentPendingWithoutCashierIsForbidden() {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.requireCashierAuthority(
                        PaymentRequestListScope.PAYMENT_PENDING,
                        false
                )
        );

        assertEquals(
                "PAYMENT_REQUEST_LIST_SCOPE_FORBIDDEN",
                exception.getCode()
        );
        assertEquals(
                "目前登入者沒有付款待辦查看權限",
                exception.getMessage()
        );
    }

    @Test
    void paymentPendingForcesApprovedAndUnpaid() {
        assertEquals(
                ApprovalStatus.APPROVED,
                service.resolveApprovalStatusForList(
                        PaymentRequestListScope.PAYMENT_PENDING,
                        null
                )
        );
        assertEquals(
                PaymentStatus.UNPAID,
                service.resolvePaymentStatusForList(
                        PaymentRequestListScope.PAYMENT_PENDING,
                        null
                )
        );
        assertEquals(
                PaymentStatus.UNPAID,
                service.resolvePaymentStatusForList(
                        PaymentRequestListScope.PAYMENT_PENDING,
                        PaymentStatus.UNPAID
                )
        );
    }

    @Test
    void paymentPendingRejectsApprovalStatusConflict() {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.resolveApprovalStatusForList(
                        PaymentRequestListScope.PAYMENT_PENDING,
                        ApprovalStatus.PENDING_CASHIER
                )
        );

        assertEquals(
                "PAYMENT_REQUEST_LIST_SCOPE_FILTER_CONFLICT",
                exception.getCode()
        );
    }

    @Test
    void paymentPendingRejectsPaymentStatusConflict() {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.resolvePaymentStatusForList(
                        PaymentRequestListScope.PAYMENT_PENDING,
                        PaymentStatus.PAID
                )
        );

        assertEquals(
                "PAYMENT_REQUEST_LIST_SCOPE_FILTER_CONFLICT",
                exception.getCode()
        );
    }

    @Test
    void managerPendingDoesNotUseCurrentDepartmentSupervisorConfiguration() {
        assertNull(service.resolveSupervisorIdForList(null, 42L));
        assertEquals(
                ApprovalStatus.APPROVED,
                service.resolveApprovalStatusForList(null, ApprovalStatus.APPROVED)
        );
    }

    private static Stream<Arguments> allRequestStates() {
        return Stream.of(
                Arguments.of(ApprovalStatus.DRAFT, PaymentStatus.UNPAID),
                Arguments.of(ApprovalStatus.PENDING_MANAGER, PaymentStatus.UNPAID),
                Arguments.of(ApprovalStatus.PENDING_CASHIER, PaymentStatus.UNPAID),
                Arguments.of(ApprovalStatus.APPROVED, PaymentStatus.UNPAID),
                Arguments.of(ApprovalStatus.APPROVED, PaymentStatus.PAID),
                Arguments.of(ApprovalStatus.REJECTED_CLOSED, PaymentStatus.UNPAID)
        );
    }

    private static Stream<ApprovalStatus> supervisorCannotReadOtherStates() {
        return Stream.of(
                ApprovalStatus.DRAFT,
                ApprovalStatus.PENDING_CASHIER,
                ApprovalStatus.APPROVED,
                ApprovalStatus.REJECTED_CLOSED
        );
    }

    private static Stream<ApprovalStatus> cashierCannotReadOtherStates() {
        return Stream.of(
                ApprovalStatus.DRAFT,
                ApprovalStatus.PENDING_MANAGER,
                ApprovalStatus.REJECTED_CLOSED
        );
    }

    private static Stream<Arguments> cashierCannotReadNonApprovedPaymentStates() {
        return Stream.of(
                Arguments.of(ApprovalStatus.DRAFT, PaymentStatus.UNPAID),
                Arguments.of(ApprovalStatus.PENDING_MANAGER, PaymentStatus.UNPAID),
                Arguments.of(ApprovalStatus.REJECTED_CLOSED, PaymentStatus.UNPAID)
        );
    }

    private PaymentRequest request(
            Long applicantId,
            Long supervisorId,
            ApprovalStatus approvalStatus,
            PaymentStatus paymentStatus
    ) {
        PaymentRequest request = new PaymentRequest();
        request.setApplicant(user(applicantId));
        request.setSupervisorSnapshot(
                supervisorId == null ? null : user(supervisorId)
        );
        request.setApprovalStatus(approvalStatus);
        request.setPaymentStatus(paymentStatus);
        return request;
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
