package tw.com.jsgcpa.paymentapproval.payment.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.com.jsgcpa.paymentapproval.master.entity.Company;
import tw.com.jsgcpa.paymentapproval.master.entity.Customer;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.repository.CompanyRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.CustomerRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.entity.Department;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CreatePaymentDraftItemRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CreatePaymentDraftRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.CreatePaymentDraftItemResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.CreatePaymentDraftResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestItem;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestItemRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@Service
public class CreatePaymentDraftService {

    private final AppUserRepository appUserRepository;
    private final CompanyRepository companyRepository;
    private final CustomerRepository customerRepository;
    private final ExpenseTypeRepository expenseTypeRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentRequestItemRepository paymentRequestItemRepository;
    private final PaymentRequestNumberGenerator paymentRequestNumberGenerator;
    private final PaymentDraftItemCalculator paymentDraftItemCalculator;

    @Autowired
    public CreatePaymentDraftService(
            AppUserRepository appUserRepository,
            CompanyRepository companyRepository,
            CustomerRepository customerRepository,
            ExpenseTypeRepository expenseTypeRepository,
            PaymentRequestRepository paymentRequestRepository,
            PaymentRequestItemRepository paymentRequestItemRepository,
            PaymentRequestNumberGenerator paymentRequestNumberGenerator,
            PaymentDraftItemCalculator paymentDraftItemCalculator
    ) {
        this.appUserRepository = appUserRepository;
        this.companyRepository = companyRepository;
        this.customerRepository = customerRepository;
        this.expenseTypeRepository = expenseTypeRepository;
        this.paymentRequestRepository = paymentRequestRepository;
        this.paymentRequestItemRepository = paymentRequestItemRepository;
        this.paymentRequestNumberGenerator = paymentRequestNumberGenerator;
        this.paymentDraftItemCalculator = paymentDraftItemCalculator;
    }

    @Transactional
    public CreatePaymentDraftResponse createDraft(
            Long applicantId,
            CreatePaymentDraftRequest request
    ) {
        AppUser applicant = findApplicant(applicantId);
        Department department = findApplicantDepartment(applicant);
        Company company = findCompany(request.companyId());
        Customer customer = findCustomer(request.customerId());
        validateRequestCategory(request.requestCategory(), customer);

        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setRequestNo(paymentRequestNumberGenerator.generate());
        paymentRequest.setApplicant(applicant);
        paymentRequest.setDepartment(department);
        paymentRequest.setCompany(company);
        paymentRequest.setCustomer(customer);
        paymentRequest.setRequestCategory(request.requestCategory());
        paymentRequest.setReason(request.reason());

        Map<Long, ExpenseType> expenseTypeCache = new HashMap<>();
        List<PaymentRequestItem> paymentRequestItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (int index = 0; index < request.items().size(); index++) {
            CreatePaymentDraftItemRequest itemRequest = request.items().get(index);
            int itemNumber = index + 1;
            ExpenseType expenseType = findExpenseType(
                    itemRequest.expenseTypeId(),
                    itemNumber,
                    expenseTypeCache
            );
            CalculatedPaymentDraftItem calculated =
                    paymentDraftItemCalculator.calculate(expenseType, itemRequest);

            PaymentRequestItem paymentRequestItem = new PaymentRequestItem();
            paymentRequestItem.setPaymentRequest(paymentRequest);
            paymentRequestItem.setExpenseType(expenseType);
            paymentRequestItem.setPriceSetting(calculated.priceSetting());
            paymentRequestItem.setDescription(itemRequest.description());
            paymentRequestItem.setPeopleCount(itemRequest.peopleCount());
            paymentRequestItem.setDays(itemRequest.days());
            paymentRequestItem.setQuantity(itemRequest.quantity());
            paymentRequestItem.setUnitPrice(calculated.unitPrice());
            paymentRequestItem.setMultiplier(calculated.multiplier());
            paymentRequestItem.setAmount(calculated.amount());
            paymentRequestItem.setExtraData(copyExtraData(itemRequest.extraData()));
            paymentRequestItem.setSortOrder(
                    itemRequest.sortOrder() != null
                            ? itemRequest.sortOrder()
                            : itemNumber
            );

            paymentRequestItems.add(paymentRequestItem);
            totalAmount = totalAmount.add(calculated.amount());
        }

        paymentRequest.setTotalAmount(totalAmount);
        paymentRequestRepository.save(paymentRequest);
        List<PaymentRequestItem> savedItems =
                paymentRequestItemRepository.saveAll(paymentRequestItems);
        return toResponse(paymentRequest, savedItems);
    }

    private AppUser findApplicant(Long applicantId) {
        if (applicantId == null || applicantId <= 0) {
            throw businessError(
                    "INVALID_APPLICANT_ID",
                    "Applicant ID must be positive"
            );
        }
        AppUser applicant = appUserRepository.findById(applicantId)
                .orElseThrow(() -> businessError(
                        "APPLICANT_NOT_FOUND",
                        "Applicant not found: " + applicantId
                ));
        if (!Boolean.TRUE.equals(applicant.getActive())) {
            throw businessError(
                    "APPLICANT_INACTIVE",
                    "Applicant is inactive: " + applicantId
            );
        }
        return applicant;
    }

    private Department findApplicantDepartment(AppUser applicant) {
        Department department = applicant.getDepartment();
        if (department == null) {
            throw businessError(
                    "APPLICANT_DEPARTMENT_MISSING",
                    "Applicant department is missing: " + applicant.getId()
            );
        }
        if (!Boolean.TRUE.equals(department.getActive())) {
            throw businessError(
                    "DEPARTMENT_INACTIVE",
                    "Applicant department is inactive: " + department.getId()
            );
        }
        return department;
    }

    private Company findCompany(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> businessError(
                        "COMPANY_NOT_FOUND",
                        "Company not found: " + companyId
                ));
        if (!Boolean.TRUE.equals(company.getActive())) {
            throw businessError(
                    "COMPANY_INACTIVE",
                    "Company is inactive: " + companyId
            );
        }
        return company;
    }

    private Customer findCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> businessError(
                        "CUSTOMER_NOT_FOUND",
                        "Customer not found: " + customerId
                ));
        if (!Boolean.TRUE.equals(customer.getActive())) {
            throw businessError(
                    "CUSTOMER_INACTIVE",
                    "Customer is inactive: " + customerId
            );
        }
        return customer;
    }

    private void validateRequestCategory(
            RequestCategory requestCategory,
            Customer customer
    ) {
        if (requestCategory == null) {
            throw businessError(
                    "INVALID_REQUEST_CATEGORY",
                    "Request category is required"
            );
        }
        RequestCategory defaultRequestCategory = customer.getDefaultRequestCategory();
        if (defaultRequestCategory != null
                && defaultRequestCategory != requestCategory) {
            throw businessError(
                    "CUSTOMER_CATEGORY_MISMATCH",
                    "Request category does not match customer default category"
            );
        }
    }

    private ExpenseType findExpenseType(
            Long expenseTypeId,
            int itemNumber,
            Map<Long, ExpenseType> expenseTypeCache
    ) {
        if (!expenseTypeCache.containsKey(expenseTypeId)) {
            ExpenseType expenseType = expenseTypeRepository.findById(expenseTypeId)
                    .orElseThrow(() -> businessError(
                            "EXPENSE_TYPE_NOT_FOUND",
                            "Expense type not found for item "
                                    + itemNumber + ": " + expenseTypeId
                    ));
            if (!Boolean.TRUE.equals(expenseType.getActive())) {
                throw businessError(
                        "EXPENSE_TYPE_INACTIVE",
                        "Expense type is inactive for item "
                                + itemNumber + ": " + expenseTypeId
                );
            }
            expenseTypeCache.put(expenseTypeId, expenseType);
        }
        return expenseTypeCache.get(expenseTypeId);
    }

    private CreatePaymentDraftResponse toResponse(
            PaymentRequest paymentRequest,
            List<PaymentRequestItem> items
    ) {
        return new CreatePaymentDraftResponse(
                paymentRequest.getId(),
                paymentRequest.getRequestNo(),
                paymentRequest.getApplicant().getId(),
                paymentRequest.getDepartment().getId(),
                paymentRequest.getCompany().getId(),
                paymentRequest.getCustomer().getId(),
                paymentRequest.getRequestCategory(),
                paymentRequest.getReason(),
                paymentRequest.getApprovalStatus(),
                paymentRequest.getPaymentStatus(),
                paymentRequest.getTotalAmount(),
                items.stream().map(this::toItemResponse).toList(),
                paymentRequest.getCreatedAt(),
                paymentRequest.getVersion()
        );
    }

    private CreatePaymentDraftItemResponse toItemResponse(PaymentRequestItem item) {
        ExpenseType expenseType = item.getExpenseType();
        ExpensePriceSetting priceSetting = item.getPriceSetting();
        return new CreatePaymentDraftItemResponse(
                item.getId(),
                expenseType.getId(),
                expenseType.getCode(),
                expenseType.getName(),
                expenseType.getCalculationType(),
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
                copyResponseExtraData(item.getExtraData()),
                item.getSortOrder()
        );
    }

    private Map<String, Object> copyExtraData(Map<String, Object> extraData) {
        return extraData == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(extraData);
    }

    private Map<String, Object> copyResponseExtraData(Map<String, Object> extraData) {
        return extraData == null
                ? Map.of()
                : new LinkedHashMap<>(extraData);
    }

    private PaymentDraftBusinessException businessError(String code, String message) {
        return new PaymentDraftBusinessException(code, message);
    }
}
