package tw.com.jsgcpa.paymentapproval.master.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tw.com.jsgcpa.paymentapproval.master.dto.response.CompanyOptionResponse;
import tw.com.jsgcpa.paymentapproval.master.dto.response.CustomerOptionResponse;
import tw.com.jsgcpa.paymentapproval.master.dto.response.ExpensePriceOptionResponse;
import tw.com.jsgcpa.paymentapproval.master.dto.response.ExpenseTypeOptionResponse;
import tw.com.jsgcpa.paymentapproval.master.entity.Company;
import tw.com.jsgcpa.paymentapproval.master.entity.Customer;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.repository.CompanyRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.CustomerRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpensePriceSettingRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;

@ExtendWith(MockitoExtension.class)
class MasterDataQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 31);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ExpenseTypeRepository expenseTypeRepository;

    @Mock
    private ExpensePriceSettingRepository expensePriceSettingRepository;

    private MasterDataQueryService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                ZonedDateTime.of(2026, 7, 31, 10, 0, 0, 0, BUSINESS_ZONE)
                        .toInstant(),
                BUSINESS_ZONE
        );
        service = new MasterDataQueryService(
                companyRepository,
                customerRepository,
                expenseTypeRepository,
                expensePriceSettingRepository,
                fixedClock
        );
    }

    @Test
    void mapsActiveCompanies() {
        when(companyRepository.findByActiveTrueOrderByCodeAscIdAsc())
                .thenReturn(List.of(company(1L, "COMPANY", "Company")));

        List<CompanyOptionResponse> result = service.getCompanies();

        assertEquals(List.of(new CompanyOptionResponse(1L, "COMPANY", "Company")), result);
    }

    @Test
    void returnsEmptyCompanies() {
        when(companyRepository.findByActiveTrueOrderByCodeAscIdAsc())
                .thenReturn(List.of());

        assertTrue(service.getCompanies().isEmpty());
    }

    @Test
    void mapsCustomersIncludingDefaultCategory() {
        Customer customer = customer(2L, "CUSTOMER", "Customer");
        customer.setDefaultRequestCategory(RequestCategory.EXPENSE);
        when(customerRepository.findByActiveTrueOrderByCodeAscIdAsc())
                .thenReturn(List.of(customer));

        assertEquals(
                List.of(new CustomerOptionResponse(
                        2L, "CUSTOMER", "Customer", RequestCategory.EXPENSE
                )),
                service.getCustomers()
        );
    }

    @Test
    void mapsCustomerWithNullDefaultCategory() {
        when(customerRepository.findByActiveTrueOrderByCodeAscIdAsc())
                .thenReturn(List.of(customer(2L, "CUSTOMER", "Customer")));

        assertEquals(null, service.getCustomers().get(0).defaultRequestCategory());
    }

    @Test
    void returnsEmptyCustomers() {
        when(customerRepository.findByActiveTrueOrderByCodeAscIdAsc())
                .thenReturn(List.of());

        assertTrue(service.getCustomers().isEmpty());
    }

    @Test
    void mapsExpenseTypesIncludingCalculationType() {
        when(expenseTypeRepository.findByActiveTrueOrderByCodeAscIdAsc())
                .thenReturn(List.of(
                        expenseType(1L, "MANUAL", CalculationType.MANUAL),
                        expenseType(2L, "MEAL", CalculationType.MEAL)
                ));

        assertEquals(
                List.of(
                        new ExpenseTypeOptionResponse(
                                1L, "MANUAL", "MANUAL", CalculationType.MANUAL
                        ),
                        new ExpenseTypeOptionResponse(
                                2L, "MEAL", "MEAL", CalculationType.MEAL
                        )
                ),
                service.getExpenseTypes()
        );
    }

    @Test
    void returnsEmptyExpenseTypes() {
        when(expenseTypeRepository.findByActiveTrueOrderByCodeAscIdAsc())
                .thenReturn(List.of());

        assertTrue(service.getExpenseTypes().isEmpty());
    }

    @Test
    void mapsEffectivePricesUsingTaipeiToday() {
        ExpenseType expenseType = expenseType(3L, "MAIL", CalculationType.QUANTITY_PRICE);
        when(expenseTypeRepository.findById(3L)).thenReturn(java.util.Optional.of(expenseType));
        ExpensePriceSetting price = price(8L, "DEFAULT", "Default", "28.00");
        when(expensePriceSettingRepository.findEffectivePrices(3L, TODAY))
                .thenReturn(List.of(price));

        List<ExpensePriceOptionResponse> result = service.getExpensePrices(3L);

        assertEquals(
                List.of(new ExpensePriceOptionResponse(
                        8L,
                        "DEFAULT",
                        "Default",
                        new BigDecimal("28.00"),
                        TODAY,
                        null
                )),
                result
        );
        verify(expensePriceSettingRepository).findEffectivePrices(3L, TODAY);
    }

    @Test
    void returnsEmptyWhenNoEffectivePrices() {
        when(expenseTypeRepository.findById(3L))
                .thenReturn(java.util.Optional.of(
                        expenseType(3L, "MAIL", CalculationType.QUANTITY_PRICE)
                ));
        when(expensePriceSettingRepository.findEffectivePrices(3L, TODAY))
                .thenReturn(List.of());

        assertTrue(service.getExpensePrices(3L).isEmpty());
    }

    @Test
    void manualExpenseTypeHasNoPrices() {
        when(expenseTypeRepository.findById(1L))
                .thenReturn(java.util.Optional.of(
                        expenseType(1L, "MANUAL", CalculationType.MANUAL)
                ));

        assertTrue(service.getExpensePrices(1L).isEmpty());
        verify(expensePriceSettingRepository, never()).findEffectivePrices(1L, TODAY);
    }

    @Test
    void travelExpenseTypeHasNoPrices() {
        when(expenseTypeRepository.findById(4L))
                .thenReturn(java.util.Optional.of(
                        expenseType(4L, "TRAVEL", CalculationType.TRAVEL)
                ));

        assertTrue(service.getExpensePrices(4L).isEmpty());
        verify(expensePriceSettingRepository, never()).findEffectivePrices(4L, TODAY);
    }

    @Test
    void rejectsInvalidExpenseTypeId() {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.getExpensePrices(0L)
        );

        assertEquals("INVALID_EXPENSE_TYPE_ID", exception.getCode());
        verify(expenseTypeRepository, never()).findById(0L);
    }

    @Test
    void rejectsNullExpenseTypeId() {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.getExpensePrices(null)
        );

        assertEquals("INVALID_EXPENSE_TYPE_ID", exception.getCode());
    }

    @Test
    void rejectsMissingExpenseType() {
        when(expenseTypeRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.getExpensePrices(99L)
        );

        assertEquals("EXPENSE_TYPE_NOT_FOUND", exception.getCode());
    }

    @Test
    void rejectsInactiveExpenseType() {
        ExpenseType inactive = expenseType(9L, "INACTIVE", CalculationType.MEAL);
        inactive.setActive(false);
        when(expenseTypeRepository.findById(9L)).thenReturn(java.util.Optional.of(inactive));

        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.getExpensePrices(9L)
        );

        assertEquals("EXPENSE_TYPE_NOT_FOUND", exception.getCode());
        verify(expensePriceSettingRepository, never()).findEffectivePrices(9L, TODAY);
    }

    private Company company(Long id, String code, String name) {
        Company company = new Company();
        ReflectionTestUtils.setField(company, "id", id);
        company.setCode(code);
        company.setName(name);
        return company;
    }

    private Customer customer(Long id, String code, String name) {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", id);
        customer.setCode(code);
        customer.setName(name);
        return customer;
    }

    private ExpenseType expenseType(Long id, String code, CalculationType calculationType) {
        ExpenseType expenseType = new ExpenseType();
        ReflectionTestUtils.setField(expenseType, "id", id);
        expenseType.setCode(code);
        expenseType.setName(code);
        expenseType.setCalculationType(calculationType);
        return expenseType;
    }

    private ExpensePriceSetting price(
            Long id,
            String code,
            String name,
            String unitPrice
    ) {
        ExpensePriceSetting price = new ExpensePriceSetting();
        ReflectionTestUtils.setField(price, "id", id);
        price.setPriceCode(code);
        price.setPriceName(name);
        price.setUnitPrice(new BigDecimal(unitPrice));
        price.setEffectiveFrom(TODAY);
        return price;
    }
}
