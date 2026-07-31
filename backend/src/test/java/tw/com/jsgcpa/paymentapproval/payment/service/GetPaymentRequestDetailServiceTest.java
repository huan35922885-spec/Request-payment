package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import tw.com.jsgcpa.paymentapproval.approval.entity.ApprovalHistory;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalAction;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.approval.repository.ApprovalHistoryRepository;
import tw.com.jsgcpa.paymentapproval.master.entity.Company;
import tw.com.jsgcpa.paymentapproval.master.entity.Customer;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.entity.Department;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestItem;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestAttachmentRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestItemRepository;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@ExtendWith(MockitoExtension.class)
class GetPaymentRequestDetailServiceTest {

    private static final Long REQUEST_ID = 100L;
    private static final OffsetDateTime NOW = OffsetDateTime.parse(
            "2026-07-31T10:00:00+08:00"
    );

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    @Mock
    private PaymentRequestItemRepository paymentRequestItemRepository;

    @Mock
    private ApprovalHistoryRepository approvalHistoryRepository;

    @Mock
    private PaymentRequestAttachmentRepository paymentRequestAttachmentRepository;

    private GetPaymentRequestDetailService service;

    @BeforeEach
    void setUp() {
        service = new GetPaymentRequestDetailService(
                paymentRequestRepository,
                paymentRequestItemRepository,
                approvalHistoryRepository,
                paymentRequestAttachmentRepository
        );
    }

    @Test
    void returnsCompletePaidRequestWithAllRelationsAndCollections() {
        PaymentRequest request = completeRequest();
        List<PaymentRequestItem> items = List.of(
                item(20L, "交通費", CalculationType.TRAVEL, null, null, 2),
                item(10L, "郵資", CalculationType.QUANTITY_PRICE, 300L, "NORMAL", 1),
                item(30L, "餐費", CalculationType.MEAL, 400L, "DEFAULT", 3)
        );
        List<ApprovalHistory> histories = List.of(
                history(1L, ApprovalAction.SUBMIT),
                history(2L, ApprovalAction.MANAGER_APPROVE),
                history(3L, ApprovalAction.CASHIER_APPROVE),
                history(4L, ApprovalAction.PAYMENT_RECORDED)
        );
        List<PaymentRequestAttachment> attachments = List.of(
                attachment(2L, "receipt.pdf"),
                attachment(1L, "invoice.pdf")
        );
        stub(request, items, histories, attachments);

        PaymentRequestDetailResponse response = service.getDetail(REQUEST_ID);

        assertEquals(REQUEST_ID, response.id());
        assertEquals("PAY-20260731-000100", response.requestNo());
        assertEquals("applicant", response.applicant().username());
        assertEquals("FIN", response.department().code());
        assertEquals("supervisor", response.supervisor().username());
        assertEquals("COMPANY", response.company().code());
        assertEquals("CUSTOMER", response.customer().code());
        assertEquals(ApprovalStatus.APPROVED, response.approvalStatus());
        assertEquals(PaymentStatus.PAID, response.paymentStatus());
        assertEquals(PaymentMethod.BANK_TRANSFER, response.paymentMethod());
        assertEquals(3, response.items().size());
        assertEquals(20L, response.items().get(0).id());
        assertEquals(4, response.approvalHistories().size());
        assertEquals(ApprovalAction.SUBMIT, response.approvalHistories().get(0).action());
        assertEquals(ApprovalAction.PAYMENT_RECORDED,
                response.approvalHistories().get(3).action());
        assertEquals(2, response.attachments().size());
        assertEquals("receipt.pdf", response.attachments().get(0).originalFilename());
        assertEquals(4L, response.version());
    }

    @Test
    void mapsDraftWithNullOptionalFieldsAndEmptyCollections() {
        PaymentRequest request = basicRequest(ApprovalStatus.DRAFT, PaymentStatus.UNPAID);
        request.setSupervisorSnapshot(null);
        request.setSubmittedAt(null);
        request.setApprovedAt(null);
        request.setApprovedBy(null);
        request.setPaidAt(null);
        request.setPaidBy(null);
        stub(request, List.of(), List.of(), List.of());

        PaymentRequestDetailResponse response = service.getDetail(REQUEST_ID);

        assertNull(response.supervisor());
        assertNull(response.submittedAt());
        assertNull(response.approvedBy());
        assertNull(response.paidBy());
        assertEquals(List.of(), response.items());
        assertEquals(List.of(), response.approvalHistories());
        assertEquals(List.of(), response.attachments());
    }

    @Test
    void mapsRejectedClosedRequestAndPreservesTimes() {
        PaymentRequest request = basicRequest(
                ApprovalStatus.REJECTED_CLOSED,
                PaymentStatus.UNPAID
        );
        request.setRejectedAt(NOW);
        request.setClosedAt(NOW);
        stub(request, List.of(), List.of(history(1L, ApprovalAction.MANAGER_REJECT)), List.of());

        PaymentRequestDetailResponse response = service.getDetail(REQUEST_ID);

        assertEquals(ApprovalStatus.REJECTED_CLOSED, response.approvalStatus());
        assertEquals(NOW, response.rejectedAt());
        assertEquals(NOW, response.closedAt());
        assertEquals(ApprovalAction.MANAGER_REJECT, response.approvalHistories().get(0).action());
    }

    @Test
    void mapsManualItemWithNullPriceSetting() {
        PaymentRequest request = basicRequest(ApprovalStatus.DRAFT, PaymentStatus.UNPAID);
        PaymentRequestItem manual = item(
                50L, "手動", CalculationType.MANUAL, null, null, 1
        );
        stub(request, List.of(manual), List.of(), List.of());

        PaymentRequestDetailResponse.ItemDetail response = service.getDetail(REQUEST_ID)
                .items().get(0);

        assertEquals(CalculationType.MANUAL, response.calculationType());
        assertNull(response.priceSettingId());
        assertNull(response.priceCode());
        assertNull(response.unitPrice());
        assertEquals(new BigDecimal("99.00"), response.amount());
    }

    @Test
    void returnsSavedUnitPriceSnapshotWithoutRecalculating() {
        PaymentRequest request = basicRequest(ApprovalStatus.DRAFT, PaymentStatus.UNPAID);
        PaymentRequestItem item = item(
                60L, "快遞", CalculationType.QUANTITY_PRICE, 500L, "EXPRESS", 1
        );
        item.setUnitPrice(new BigDecimal("123.45"));
        item.setAmount(new BigDecimal("246.90"));
        stub(request, List.of(item), List.of(), List.of());

        PaymentRequestDetailResponse.ItemDetail response = service.getDetail(REQUEST_ID)
                .items().get(0);

        assertEquals(new BigDecimal("123.45"), response.unitPrice());
        assertEquals(new BigDecimal("246.90"), response.amount());
        assertEquals("EXPRESS", response.priceCode());
    }

    @Test
    void preservesRepositoryOrderForItemsHistoriesAndAttachments() {
        PaymentRequest request = basicRequest(ApprovalStatus.DRAFT, PaymentStatus.UNPAID);
        List<PaymentRequestItem> items = List.of(
                item(9L, "second", CalculationType.MANUAL, null, null, 2),
                item(8L, "first", CalculationType.MANUAL, null, null, 1)
        );
        List<ApprovalHistory> histories = List.of(
                history(9L, ApprovalAction.MANAGER_APPROVE),
                history(8L, ApprovalAction.SUBMIT)
        );
        List<PaymentRequestAttachment> attachments = List.of(
                attachment(9L, "second.txt"),
                attachment(8L, "first.txt")
        );
        stub(request, items, histories, attachments);

        PaymentRequestDetailResponse response = service.getDetail(REQUEST_ID);

        assertEquals(9L, response.items().get(0).id());
        assertEquals(9L, response.approvalHistories().get(0).id());
        assertEquals(9L, response.attachments().get(0).id());
    }

    @Test
    void returnsNotFoundWithoutLoadingCollections() {
        when(paymentRequestRepository.findById(999L)).thenReturn(Optional.empty());

        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.getDetail(999L)
        );

        assertEquals("PAYMENT_REQUEST_NOT_FOUND", exception.getCode());
        verify(paymentRequestItemRepository, never())
                .findByPaymentRequest_IdOrderBySortOrderAscIdAsc(any());
        verify(approvalHistoryRepository, never())
                .findByPaymentRequest_IdOrderByActedAtAscIdAsc(any());
        verify(paymentRequestAttachmentRepository, never())
                .findByPaymentRequest_IdOrderByCreatedAtAscIdAsc(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void rejectsInvalidPaymentRequestIds(Long id) {
        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> service.getDetail(id)
        );

        assertEquals("INVALID_PAYMENT_REQUEST_ID", exception.getCode());
        verify(paymentRequestRepository, never()).findById(any());
    }

    @Test
    void doesNotWriteAnyRepositoryDuringRead() {
        PaymentRequest request = basicRequest(ApprovalStatus.DRAFT, PaymentStatus.UNPAID);
        stub(request, List.of(), List.of(), List.of());

        service.getDetail(REQUEST_ID);

        verify(paymentRequestRepository, never()).save(any());
        verify(paymentRequestRepository, never()).delete(any());
        verify(paymentRequestItemRepository, never()).save(any());
        verify(paymentRequestItemRepository, never()).delete(any());
        verify(approvalHistoryRepository, never()).save(any());
        verify(approvalHistoryRepository, never()).delete(any());
        verify(paymentRequestAttachmentRepository, never()).save(any());
        verify(paymentRequestAttachmentRepository, never()).delete(any());
    }

    private void stub(
            PaymentRequest request,
            List<PaymentRequestItem> items,
            List<ApprovalHistory> histories,
            List<PaymentRequestAttachment> attachments
    ) {
        when(paymentRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(paymentRequestItemRepository.findByPaymentRequest_IdOrderBySortOrderAscIdAsc(REQUEST_ID))
                .thenReturn(items);
        when(approvalHistoryRepository.findByPaymentRequest_IdOrderByActedAtAscIdAsc(REQUEST_ID))
                .thenReturn(histories);
        when(paymentRequestAttachmentRepository.findByPaymentRequest_IdOrderByCreatedAtAscIdAsc(REQUEST_ID))
                .thenReturn(attachments);
    }

    private PaymentRequest completeRequest() {
        PaymentRequest request = basicRequest(ApprovalStatus.APPROVED, PaymentStatus.PAID);
        AppUser supervisor = user(2L, "supervisor", "主管");
        request.setSupervisorSnapshot(supervisor);
        request.setApprovedBy(supervisor);
        request.setPaidBy(user(3L, "cashier", "出納"));
        request.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        request.setPaymentReference("BANK-001");
        request.setPaymentNote("已付款");
        request.setPaidAt(NOW);
        request.setApprovedAt(NOW);
        return request;
    }

    private PaymentRequest basicRequest(ApprovalStatus approvalStatus, PaymentStatus paymentStatus) {
        PaymentRequest request = new PaymentRequest();
        setId(request, REQUEST_ID);
        setField(request, "version", 4L);
        setField(request, "createdAt", NOW.minusDays(1));
        setField(request, "updatedAt", NOW);
        request.setRequestNo("PAY-20260731-000100");
        request.setApplicant(user(1L, "applicant", "申請人"));
        Department department = new Department();
        setId(department, 7L);
        department.setCode("FIN");
        department.setName("財務部");
        request.setDepartment(department);
        request.setCompany(company(8L, "COMPANY", "公司"));
        request.setCustomer(customer(9L, "CUSTOMER", "客戶"));
        request.setRequestCategory(RequestCategory.EXPENSE);
        request.setReason("測試請款");
        request.setApprovalStatus(approvalStatus);
        request.setPaymentStatus(paymentStatus);
        request.setTotalAmount(new BigDecimal("456.78"));
        request.setSubmittedAt(NOW.minusHours(2));
        return request;
    }

    private PaymentRequestItem item(
            Long id,
            String description,
            CalculationType calculationType,
            Long priceSettingId,
            String priceCode,
            int sortOrder
    ) {
        PaymentRequestItem item = new PaymentRequestItem();
        setId(item, id);
        ExpenseType expenseType = new ExpenseType();
        setId(expenseType, id + 1000L);
        expenseType.setCode("TYPE-" + id);
        expenseType.setName("費用 " + id);
        expenseType.setCalculationType(calculationType);
        item.setExpenseType(expenseType);
        if (priceSettingId != null) {
            ExpensePriceSetting priceSetting = new ExpensePriceSetting();
            setId(priceSetting, priceSettingId);
            priceSetting.setPriceCode(priceCode);
            priceSetting.setPriceName("價格 " + priceCode);
            priceSetting.setUnitPrice(new BigDecimal("100.00"));
            item.setPriceSetting(priceSetting);
            item.setUnitPrice(new BigDecimal("100.00"));
        }
        item.setDescription(description);
        item.setQuantity(new BigDecimal("2.00"));
        item.setMultiplier(BigDecimal.ONE);
        item.setAmount(new BigDecimal("99.00"));
        item.setExtraData(new LinkedHashMap<>(Map.of("source", "test")));
        item.setSortOrder(sortOrder);
        return item;
    }

    private ApprovalHistory history(Long id, ApprovalAction action) {
        ApprovalHistory history = new ApprovalHistory();
        setId(history, id);
        history.setActor(user(2L, "supervisor", "主管"));
        history.setAction(action);
        history.setFromApprovalStatus(ApprovalStatus.PENDING_MANAGER);
        history.setToApprovalStatus(ApprovalStatus.PENDING_CASHIER);
        history.setFromPaymentStatus(PaymentStatus.UNPAID);
        history.setToPaymentStatus(PaymentStatus.UNPAID);
        history.setComment("確認");
        history.setActedAt(NOW);
        return history;
    }

    private PaymentRequestAttachment attachment(Long id, String filename) {
        PaymentRequestAttachment attachment = new PaymentRequestAttachment();
        setId(attachment, id);
        attachment.setAttachmentType(AttachmentType.RECEIPT);
        attachment.setOriginalFilename(filename);
        attachment.setStoredFilename("stored-" + filename);
        attachment.setStoragePath("/private/" + filename);
        attachment.setContentType("application/pdf");
        attachment.setFileSize(1234L);
        setField(attachment, "createdAt", NOW);
        return attachment;
    }

    private AppUser user(Long id, String username, String displayName) {
        AppUser user = new AppUser();
        setId(user, id);
        user.setUsername(username);
        user.setDisplayName(displayName);
        return user;
    }

    private Company company(Long id, String code, String name) {
        Company company = new Company();
        setId(company, id);
        company.setCode(code);
        company.setName(name);
        return company;
    }

    private Customer customer(Long id, String code, String name) {
        Customer customer = new Customer();
        setId(customer, id);
        customer.setCode(code);
        customer.setName(name);
        return customer;
    }

    private void setId(Object entity, Long id) {
        setField(entity, "id", id);
    }

    private void setField(Object target, String fieldName, Object value) {
        ReflectionTestUtils.setField(target, fieldName, value);
    }
}
