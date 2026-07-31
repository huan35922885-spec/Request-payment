package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.master.entity.Company;
import tw.com.jsgcpa.paymentapproval.master.entity.Customer;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.entity.Department;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@ExtendWith(MockitoExtension.class)
class ListPaymentRequestsServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    private ListPaymentRequestsService service;

    @BeforeEach
    void setUp() {
        service = new ListPaymentRequestsService(paymentRequestRepository);
    }

    @Test
    void usesDefaultPaginationAndFixedSort() {
        whenSearchReturns(Page.empty());

        service.list(
                null, null, null, null, null, null,
                null, null, null, null, null, null
        );

        Pageable pageable = capturePageable();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC,
                pageable.getSort().getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.DESC,
                pageable.getSort().getOrderFor("id").getDirection());
        assertEquals(2, pageable.getSort().toList().size());
    }

    @Test
    void mapsDraftAndApprovedPaidRequestsUsingSupervisorSnapshot() {
        PaymentRequest draft = request(1L, "PAY-1", ApprovalStatus.DRAFT, PaymentStatus.UNPAID);
        draft.setSupervisorSnapshot(null);
        PaymentRequest paid = request(2L, "PAY-2", ApprovalStatus.APPROVED, PaymentStatus.PAID);
        paid.setSupervisorSnapshot(user(20L, "主管快照"));
        Page<PaymentRequest> page = new PageImpl<>(List.of(draft, paid), PageRequest.of(0, 20), 2);
        whenSearchReturns(page);

        var response = service.list(
                0, 20, null, null, null, null,
                null, null, null, null, null, null
        );

        assertEquals(2, response.content().size());
        assertNull(response.content().get(0).supervisorId());
        assertEquals(20L, response.content().get(1).supervisorId());
        assertEquals("主管快照", response.content().get(1).supervisorName());
        assertEquals(ApprovalStatus.APPROVED, response.content().get(1).approvalStatus());
        assertEquals(PaymentStatus.PAID, response.content().get(1).paymentStatus());
        assertEquals(new BigDecimal("1620.50"), response.content().get(1).totalAmount());
    }

    @Test
    void mapsPaginationMetadata() {
        Page<PaymentRequest> page = new PageImpl<>(
                List.of(request(2L, "PAY-2", ApprovalStatus.APPROVED, PaymentStatus.PAID)),
                PageRequest.of(1, 20),
                45
        );
        whenSearchReturns(page);

        var response = service.list(
                1, 20, null, null, null, null,
                null, null, null, null, null, null
        );

        assertEquals(1, response.page());
        assertEquals(20, response.size());
        assertEquals(45, response.totalElements());
        assertEquals(3, response.totalPages());
        assertFalse(response.first());
        assertFalse(response.last());
    }

    @Test
    void returnsEmptyPageMetadata() {
        whenSearchReturns(Page.empty(PageRequest.of(0, 20)));

        var response = service.list(
                0, 20, null, null, null, null,
                null, null, null, null, null, null
        );

        assertEquals(List.of(), response.content());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
        assertTrue(response.first());
        assertTrue(response.last());
    }

    @Test
    void passesEnumAndIdFiltersToRepository() {
        whenSearchReturns(Page.empty());

        service.list(
                0,
                20,
                null,
                ApprovalStatus.PENDING_MANAGER,
                PaymentStatus.UNPAID,
                RequestCategory.EXPENSE,
                1L,
                2L,
                3L,
                4L,
                null,
                null
        );

        verify(paymentRequestRepository).search(
                eq(null),
                eq(ApprovalStatus.PENDING_MANAGER),
                eq(PaymentStatus.UNPAID),
                eq(RequestCategory.EXPENSE),
                eq(1L),
                eq(2L),
                eq(3L),
                eq(4L),
                eq(null),
                eq(null),
                any(Pageable.class)
        );
    }

    @Test
    void convertsBlankRequestNoToNull() {
        whenSearchReturns(Page.empty());

        service.list(
                0, 20, "   ", null, null, null,
                null, null, null, null, null, null
        );

        ArgumentCaptor<String> requestNoCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentRequestRepository).search(
                requestNoCaptor.capture(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class)
        );
        assertNull(requestNoCaptor.getValue());
    }

    @Test
    void keepsNullRequestNoForNoFilterRegression() {
        whenSearchReturns(Page.empty());

        service.list(
                0, 20, null, null, null, null,
                null, null, null, null, null, null
        );

        verify(paymentRequestRepository).search(
                eq(null),
                any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class)
        );
    }

    @Test
    void convertsDateRangeToTaipeiStartAndExclusiveNextDay() {
        whenSearchReturns(Page.empty());

        service.list(
                0,
                20,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
        );

        ArgumentCaptor<OffsetDateTime> fromCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(paymentRequestRepository).search(
                any(), any(), any(), any(), any(), any(), any(), any(),
                fromCaptor.capture(),
                toCaptor.capture(),
                any(Pageable.class)
        );
        assertEquals(
                LocalDate.of(2026, 7, 1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime(),
                fromCaptor.getValue()
        );
        assertEquals(
                LocalDate.of(2026, 8, 1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime(),
                toCaptor.getValue()
        );
    }

    @Test
    void acceptsOneSidedDateFilters() {
        whenSearchReturns(Page.empty());

        service.list(
                0,
                20,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 7, 1),
                null
        );

        verify(paymentRequestRepository).search(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(OffsetDateTime.class),
                any(),
                any(Pageable.class)
        );
    }

    @Test
    void rejectsNegativePage() {
        assertCode("INVALID_PAGE", () -> service.list(
                -1, 20, null, null, null, null,
                null, null, null, null, null, null
        ));
        verifySearchNeverCalled();
    }

    @Test
    void rejectsZeroPageSize() {
        assertCode("INVALID_PAGE_SIZE", () -> service.list(
                0, 0, null, null, null, null,
                null, null, null, null, null, null
        ));
        verifySearchNeverCalled();
    }

    @Test
    void rejectsPageSizeAboveMaximum() {
        assertCode("INVALID_PAGE_SIZE", () -> service.list(
                0, 101, null, null, null, null,
                null, null, null, null, null, null
        ));
        verifySearchNeverCalled();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void rejectsInvalidFilterIds(Long invalidId) {
        assertCode("INVALID_FILTER_ID", () -> service.list(
                0, 20, null, null, null, null,
                invalidId, null, null, null, null, null
        ));
        verifySearchNeverCalled();
    }

    @Test
    void rejectsReversedDateRange() {
        assertCode("INVALID_DATE_RANGE", () -> service.list(
                0,
                20,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 7, 31)
        ));
        verifySearchNeverCalled();
    }

    @Test
    void doesNotWriteThroughRepository() {
        whenSearchReturns(Page.empty());

        service.list(
                0, 20, null, null, null, null,
                null, null, null, null, null, null
        );

        verify(paymentRequestRepository, never()).save(any());
        verify(paymentRequestRepository, never()).delete(any());
        verify(paymentRequestRepository, never()).flush();
    }

    private PaymentRequest request(
            Long id,
            String requestNo,
            ApprovalStatus approvalStatus,
            PaymentStatus paymentStatus
    ) {
        PaymentRequest request = new PaymentRequest();
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "version", 4L);
        ReflectionTestUtils.setField(request, "createdAt", OffsetDateTime.parse(
                "2026-07-31T09:00:00+08:00"
        ));
        ReflectionTestUtils.setField(request, "updatedAt", OffsetDateTime.parse(
                "2026-07-31T10:00:00+08:00"
        ));
        request.setRequestNo(requestNo);
        request.setApplicant(user(1L, "申請人"));
        request.setDepartment(department(2L, "部門"));
        request.setCompany(company(3L, "公司"));
        request.setCustomer(customer(4L, "客戶"));
        request.setRequestCategory(RequestCategory.EXPENSE);
        request.setApprovalStatus(approvalStatus);
        request.setPaymentStatus(paymentStatus);
        request.setTotalAmount(new BigDecimal("1620.50"));
        request.setSubmittedAt(OffsetDateTime.parse("2026-07-31T09:30:00+08:00"));
        request.setApprovedAt(paymentStatus == PaymentStatus.PAID
                ? OffsetDateTime.parse("2026-07-31T10:30:00+08:00") : null);
        request.setPaidAt(paymentStatus == PaymentStatus.PAID
                ? OffsetDateTime.parse("2026-07-31T11:00:00+08:00") : null);
        return request;
    }

    private AppUser user(Long id, String displayName) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername("user-" + id);
        user.setDisplayName(displayName);
        return user;
    }

    private Department department(Long id, String name) {
        Department department = new Department();
        ReflectionTestUtils.setField(department, "id", id);
        department.setCode("DEP-" + id);
        department.setName(name);
        return department;
    }

    private Company company(Long id, String name) {
        Company company = new Company();
        ReflectionTestUtils.setField(company, "id", id);
        company.setCode("COMP-" + id);
        company.setName(name);
        return company;
    }

    private Customer customer(Long id, String name) {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", id);
        customer.setCode("CUS-" + id);
        customer.setName(name);
        return customer;
    }

    private void whenSearchReturns(Page<PaymentRequest> page) {
        when(paymentRequestRepository.search(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class)
        )).thenReturn(page);
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRequestRepository).search(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                captor.capture()
        );
        return captor.getValue();
    }

    private void verifySearchNeverCalled() {
        verify(paymentRequestRepository, never()).search(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(Pageable.class)
        );
    }

    private void assertCode(String expectedCode, Executable invocation) {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                invocation
        );
        assertEquals(expectedCode, exception.getCode());
    }
}
