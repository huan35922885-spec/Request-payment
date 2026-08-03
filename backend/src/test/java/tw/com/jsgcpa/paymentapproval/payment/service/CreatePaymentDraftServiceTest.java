package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.master.entity.Company;
import tw.com.jsgcpa.paymentapproval.master.entity.Customer;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.repository.CompanyRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.CustomerRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.entity.Department;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CreatePaymentDraftItemRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CreatePaymentDraftRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.CreatePaymentDraftResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestItem;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestItemRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@ExtendWith(MockitoExtension.class)
class CreatePaymentDraftServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ExpenseTypeRepository expenseTypeRepository;

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private PaymentRequestItemRepository paymentRequestItemRepository;

    @Mock
    private PaymentRequestNumberGenerator paymentRequestNumberGenerator;

    @Mock
    private PaymentDraftItemCalculator paymentDraftItemCalculator;

    private CreatePaymentDraftService service;

    @BeforeEach
    void setUp() {
        service = new CreatePaymentDraftService(
                appUserRepository,
                companyRepository,
                customerRepository,
                expenseTypeRepository,
                paymentRequestRepository,
                paymentRequestItemRepository,
                paymentRequestNumberGenerator,
                paymentDraftItemCalculator
        );
    }

    @Test
    void createsDraftWithCalculatedItemsAndResponseData() {
        References references = validReferences();
        Map<String, Object> firstExtraData = new LinkedHashMap<>();
        firstExtraData.put("source", 1);
        CreatePaymentDraftItemRequest firstItem = item(1L, firstExtraData, null);
        CreatePaymentDraftItemRequest secondItem = item(1L, null, 5);
        ExpensePriceSetting priceSetting = priceSetting();
        when(paymentDraftItemCalculator.calculate(any(), any()))
                .thenReturn(new CalculatedPaymentDraftItem(
                        priceSetting,
                        new BigDecimal("80.00"),
                        BigDecimal.ONE,
                        new BigDecimal("100.00")
                ))
                .thenReturn(new CalculatedPaymentDraftItem(
                        null,
                        null,
                        BigDecimal.ONE,
                        new BigDecimal("25.00")
                ));
        stubSaves();

        CreatePaymentDraftResponse response = service.createDraft(1L,
                request(firstItem, secondItem)
        );

        assertEquals("PAY-20260730-000001", response.requestNo());
        assertEquals(ApprovalStatus.DRAFT, response.approvalStatus());
        assertEquals(PaymentStatus.UNPAID, response.paymentStatus());
        assertEquals(new BigDecimal("125.00"), response.totalAmount());
        assertEquals(2, response.items().size());
        assertEquals(1, response.items().get(0).sortOrder());
        assertEquals(5, response.items().get(1).sortOrder());
        assertEquals(99L, response.items().get(0).priceSettingId());
        assertEquals("NORMAL", response.items().get(0).priceCode());
        assertEquals("Normal", response.items().get(0).priceName());
        assertEquals(1, response.items().get(0).extraData().get("source"));
        verify(expenseTypeRepository, times(1)).findById(1L);
        verify(paymentRequestRepository, times(1)).save(any(PaymentRequest.class));
        verify(paymentRequestItemRepository, times(1)).saveAll(anyList());
    }

    @Test
    void assignsSequentialSortOrderAndCopiesExtraData() {
        validReferences();
        Map<String, Object> originalExtraData = new LinkedHashMap<>();
        originalExtraData.put("source", "request");
        CreatePaymentDraftItemRequest firstItem = item(1L, originalExtraData, null);
        CreatePaymentDraftItemRequest secondItem = item(1L, null, null);
        when(paymentDraftItemCalculator.calculate(any(), any()))
                .thenReturn(calculated("10.00"));
        stubSaves();

        CreatePaymentDraftResponse response = service.createDraft(1L,
                request(firstItem, secondItem)
        );
        originalExtraData.put("source", "mutated");

        ArgumentCaptor<List<PaymentRequestItem>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(paymentRequestItemRepository).saveAll(captor.capture());
        List<PaymentRequestItem> savedItems = captor.getValue();
        assertEquals(1, savedItems.get(0).getSortOrder());
        assertEquals(2, savedItems.get(1).getSortOrder());
        assertEquals("request", savedItems.get(0).getExtraData().get("source"));
        assertEquals("request", response.items().get(0).extraData().get("source"));

        response.items().get(0).extraData().put("source", "response");
        assertEquals("request", savedItems.get(0).getExtraData().get("source"));
    }

    @Test
    void acceptsCustomerWithoutDefaultRequestCategory() {
        References references = validReferences();
        references.customer.setDefaultRequestCategory(null);
        when(paymentDraftItemCalculator.calculate(any(), any()))
                .thenReturn(calculated("10.00"));
        stubSaves();

        assertEquals(
                RequestCategory.EXPENSE,
                service.createDraft(1L, request(item(1L, null, null))).requestCategory()
        );
    }

    @Test
    void rejectsInvalidApplicantId() {
        assertCode(
                "INVALID_APPLICANT_ID",
                () -> service.createDraft(0L, request(item(1L, null, null)))
        );
        verify(appUserRepository, never()).findById(any());
        verify(paymentRequestRepository, never()).save(any());
    }

    @Test
    void rejectsMissingApplicant() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.empty());

        assertCode("APPLICANT_NOT_FOUND", () -> service.createDraft(1L, request(item(1L, null, null))));
        verify(paymentRequestRepository, never()).save(any());
        verify(paymentRequestItemRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsInactiveApplicant() {
        AppUser applicant = new AppUser();
        setId(applicant, 1L);
        applicant.setActive(false);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(applicant));

        assertCode("APPLICANT_INACTIVE", () -> service.createDraft(1L, request(item(1L, null, null))));
    }

    @Test
    void rejectsApplicantWithoutDepartment() {
        AppUser applicant = new AppUser();
        setId(applicant, 1L);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(applicant));

        assertCode("APPLICANT_DEPARTMENT_MISSING", () -> service.createDraft(1L, request(item(1L, null, null))));
    }

    @Test
    void rejectsInactiveDepartment() {
        AppUser applicant = new AppUser();
        setId(applicant, 1L);
        Department department = new Department();
        setId(department, 2L);
        department.setActive(false);
        applicant.setDepartment(department);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(applicant));

        assertCode("DEPARTMENT_INACTIVE", () -> service.createDraft(1L, request(item(1L, null, null))));
    }

    @Test
    void rejectsMissingCompany() {
        validApplicant();
        when(companyRepository.findById(10L)).thenReturn(Optional.empty());

        assertCode("COMPANY_NOT_FOUND", () -> service.createDraft(1L, request(item(1L, null, null))));
    }

    @Test
    void rejectsInactiveCustomer() {
        validApplicant();
        validCompany();
        Customer customer = new Customer();
        setId(customer, 20L);
        customer.setActive(false);
        when(customerRepository.findById(20L)).thenReturn(Optional.of(customer));

        assertCode("CUSTOMER_INACTIVE", () -> service.createDraft(1L, request(item(1L, null, null))));
    }

    @Test
    void rejectsCustomerCategoryMismatch() {
        References references = validReferences();
        references.customer.setDefaultRequestCategory(RequestCategory.ADVANCE);

        assertCode("CUSTOMER_CATEGORY_MISMATCH", () -> service.createDraft(1L, request(item(1L, null, null))));
    }

    @Test
    void rejectsNullRequestCategory() {
        validReferences();
        CreatePaymentDraftRequest request = new CreatePaymentDraftRequest(
                10L,
                20L,
                null,
                "reason",
                List.of(item(1L, null, null))
        );

        assertCode("INVALID_REQUEST_CATEGORY", () -> service.createDraft(1L, request));
    }

    @Test
    void rejectsMissingExpenseType() {
        validApplicant();
        validCompany();
        validCustomer();
        when(expenseTypeRepository.findById(1L)).thenReturn(Optional.empty());

        assertCode("EXPENSE_TYPE_NOT_FOUND", () -> service.createDraft(1L, itemRequest()));
        verify(paymentRequestRepository, never()).save(any());
    }

    @Test
    void rejectsInactiveExpenseType() {
        validApplicant();
        validCompany();
        validCustomer();
        ExpenseType expenseType = new ExpenseType();
        setId(expenseType, 1L);
        expenseType.setActive(false);
        when(expenseTypeRepository.findById(1L)).thenReturn(Optional.of(expenseType));

        assertCode("EXPENSE_TYPE_INACTIVE", () -> service.createDraft(1L, itemRequest()));
        verify(paymentRequestRepository, never()).save(any());
    }

    @Test
    void propagatesCalculatorFailureWithoutSaving() {
        validReferences();
        when(paymentDraftItemCalculator.calculate(any(), any()))
                .thenThrow(new PaymentDraftBusinessException(
                        "INVALID_CALCULATION_INPUT",
                        "invalid"
                ));

        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.createDraft(1L, itemRequest())
        );

        assertEquals("INVALID_CALCULATION_INPUT", exception.getCode());
        verify(paymentRequestRepository, never()).save(any());
        verify(paymentRequestItemRepository, never()).saveAll(anyList());
    }

    private References validReferences() {
        AppUser applicant = validApplicant();
        Department department = applicant.getDepartment();
        Company company = validCompany();
        Customer customer = validCustomer();
        ExpenseType expenseType = new ExpenseType();
        setId(expenseType, 1L);
        expenseType.setCode("MANUAL");
        expenseType.setName("Manual");
        expenseType.setCalculationType(CalculationType.MANUAL);
        expenseType.setActive(true);
        lenient().when(expenseTypeRepository.findById(1L))
                .thenReturn(Optional.of(expenseType));
        lenient().when(paymentRequestNumberGenerator.generate())
                .thenReturn("PAY-20260730-000001");
        return new References(applicant, department, company, customer, expenseType);
    }

    private AppUser validApplicant() {
        AppUser applicant = new AppUser();
        setId(applicant, 1L);
        applicant.setActive(true);
        Department department = new Department();
        setId(department, 2L);
        department.setActive(true);
        applicant.setDepartment(department);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(applicant));
        return applicant;
    }

    private Company validCompany() {
        Company company = new Company();
        setId(company, 10L);
        company.setActive(true);
        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        return company;
    }

    private Customer validCustomer() {
        Customer customer = new Customer();
        setId(customer, 20L);
        customer.setActive(true);
        customer.setDefaultRequestCategory(RequestCategory.EXPENSE);
        when(customerRepository.findById(20L)).thenReturn(Optional.of(customer));
        return customer;
    }

    private CreatePaymentDraftRequest itemRequest() {
        return request(item(1L, null, null));
    }

    private CreatePaymentDraftRequest request(CreatePaymentDraftItemRequest... items) {
        return new CreatePaymentDraftRequest(
                10L,
                20L,
                RequestCategory.EXPENSE,
                "reason",
                List.of(items)
        );
    }

    private CreatePaymentDraftItemRequest item(
            Long expenseTypeId,
            Map<String, Object> extraData,
            Integer sortOrder
    ) {
        return new CreatePaymentDraftItemRequest(
                expenseTypeId,
                null,
                "description",
                null,
                null,
                null,
                null,
                new BigDecimal("10.00"),
                extraData,
                sortOrder
        );
    }

    private CalculatedPaymentDraftItem calculated(String amount) {
        return new CalculatedPaymentDraftItem(
                null,
                null,
                BigDecimal.ONE,
                new BigDecimal(amount)
        );
    }

    private ExpensePriceSetting priceSetting() {
        ExpensePriceSetting priceSetting = new ExpensePriceSetting();
        setId(priceSetting, 99L);
        priceSetting.setPriceCode("NORMAL");
        priceSetting.setPriceName("Normal");
        priceSetting.setUnitPrice(new BigDecimal("80.00"));
        return priceSetting;
    }

    private void stubSaves() {
        when(paymentRequestRepository.save(any(PaymentRequest.class)))
                .thenAnswer(invocation -> {
                    PaymentRequest paymentRequest = invocation.getArgument(0);
                    setId(paymentRequest, 100L);
                    ReflectionTestUtils.setField(
                            paymentRequest,
                            "version",
                            0L
                    );
                    ReflectionTestUtils.setField(
                            paymentRequest,
                            "createdAt",
                            OffsetDateTime.parse("2026-07-30T10:00:00+08:00")
                    );
                    return paymentRequest;
                });
        when(paymentRequestItemRepository.saveAll(anyList()))
                .thenAnswer(invocation -> {
                    List<PaymentRequestItem> items = invocation.getArgument(0);
                    for (int index = 0; index < items.size(); index++) {
                        setId(items.get(index), 200L + index);
                    }
                    return items;
                });
    }

    private void assertCode(String code, Runnable action) {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                action::run
        );
        assertEquals(code, exception.getCode());
    }

    private void setId(Object entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }

    private record References(
            AppUser applicant,
            Department department,
            Company company,
            Customer customer,
            ExpenseType expenseType
    ) {
    }
}
