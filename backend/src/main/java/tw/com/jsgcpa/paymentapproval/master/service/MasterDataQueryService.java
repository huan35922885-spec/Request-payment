package tw.com.jsgcpa.paymentapproval.master.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;

@Service
@Transactional(readOnly = true)
public class MasterDataQueryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final CompanyRepository companyRepository;
    private final CustomerRepository customerRepository;
    private final ExpenseTypeRepository expenseTypeRepository;
    private final ExpensePriceSettingRepository expensePriceSettingRepository;
    private final Clock clock;

    @Autowired
    public MasterDataQueryService(
            CompanyRepository companyRepository,
            CustomerRepository customerRepository,
            ExpenseTypeRepository expenseTypeRepository,
            ExpensePriceSettingRepository expensePriceSettingRepository
    ) {
        this(
                companyRepository,
                customerRepository,
                expenseTypeRepository,
                expensePriceSettingRepository,
                Clock.system(BUSINESS_ZONE)
        );
    }

    MasterDataQueryService(
            CompanyRepository companyRepository,
            CustomerRepository customerRepository,
            ExpenseTypeRepository expenseTypeRepository,
            ExpensePriceSettingRepository expensePriceSettingRepository,
            Clock clock
    ) {
        this.companyRepository = companyRepository;
        this.customerRepository = customerRepository;
        this.expenseTypeRepository = expenseTypeRepository;
        this.expensePriceSettingRepository = expensePriceSettingRepository;
        this.clock = clock;
    }

    public List<CompanyOptionResponse> getCompanies() {
        return companyRepository.findByActiveTrueOrderByCodeAscIdAsc()
                .stream()
                .map(this::toCompanyOption)
                .toList();
    }

    public List<CustomerOptionResponse> getCustomers() {
        return customerRepository.findByActiveTrueOrderByCodeAscIdAsc()
                .stream()
                .map(this::toCustomerOption)
                .toList();
    }

    public List<ExpenseTypeOptionResponse> getExpenseTypes() {
        return expenseTypeRepository.findByActiveTrueOrderByCodeAscIdAsc()
                .stream()
                .map(this::toExpenseTypeOption)
                .toList();
    }

    public List<ExpensePriceOptionResponse> getExpensePrices(Long expenseTypeId) {
        validateExpenseTypeId(expenseTypeId);

        ExpenseType expenseType = expenseTypeRepository.findById(expenseTypeId)
                .filter(type -> Boolean.TRUE.equals(type.getActive()))
                .orElseThrow(() -> businessError(
                        "EXPENSE_TYPE_NOT_FOUND",
                        "Expense type not found: " + expenseTypeId
                ));

        if (expenseType.getCalculationType() == CalculationType.MANUAL
                || expenseType.getCalculationType() == CalculationType.TRAVEL) {
            return List.of();
        }

        LocalDate today = LocalDate.now(clock);
        return expensePriceSettingRepository.findEffectivePrices(expenseTypeId, today)
                .stream()
                .map(this::toExpensePriceOption)
                .toList();
    }

    private CompanyOptionResponse toCompanyOption(Company company) {
        return new CompanyOptionResponse(
                company.getId(),
                company.getCode(),
                company.getName()
        );
    }

    private CustomerOptionResponse toCustomerOption(Customer customer) {
        return new CustomerOptionResponse(
                customer.getId(),
                customer.getCode(),
                customer.getName(),
                customer.getDefaultRequestCategory()
        );
    }

    private ExpenseTypeOptionResponse toExpenseTypeOption(ExpenseType expenseType) {
        return new ExpenseTypeOptionResponse(
                expenseType.getId(),
                expenseType.getCode(),
                expenseType.getName(),
                expenseType.getCalculationType()
        );
    }

    private ExpensePriceOptionResponse toExpensePriceOption(
            ExpensePriceSetting priceSetting
    ) {
        return new ExpensePriceOptionResponse(
                priceSetting.getId(),
                priceSetting.getPriceCode(),
                priceSetting.getPriceName(),
                priceSetting.getUnitPrice(),
                priceSetting.getEffectiveFrom(),
                priceSetting.getEffectiveTo()
        );
    }

    private void validateExpenseTypeId(Long expenseTypeId) {
        if (expenseTypeId == null || expenseTypeId <= 0) {
            throw businessError(
                    "INVALID_EXPENSE_TYPE_ID",
                    "expenseTypeId must be greater than zero"
            );
        }
    }

    private PaymentDraftBusinessException businessError(String code, String message) {
        return new PaymentDraftBusinessException(code, message);
    }
}
