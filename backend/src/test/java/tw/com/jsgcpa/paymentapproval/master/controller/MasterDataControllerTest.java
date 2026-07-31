package tw.com.jsgcpa.paymentapproval.master.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tw.com.jsgcpa.paymentapproval.common.exception.GlobalExceptionHandler;
import tw.com.jsgcpa.paymentapproval.master.dto.response.CompanyOptionResponse;
import tw.com.jsgcpa.paymentapproval.master.dto.response.CustomerOptionResponse;
import tw.com.jsgcpa.paymentapproval.master.dto.response.ExpensePriceOptionResponse;
import tw.com.jsgcpa.paymentapproval.master.dto.response.ExpenseTypeOptionResponse;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.service.MasterDataQueryService;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;

@WebMvcTest(MasterDataController.class)
@Import(GlobalExceptionHandler.class)
class MasterDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MasterDataQueryService masterDataQueryService;

    @Test
    void getsCompanies() throws Exception {
        when(masterDataQueryService.getCompanies()).thenReturn(List.of(
                new CompanyOptionResponse(1L, "COMPANY", "Company")
        ));

        mockMvc.perform(get("/api/master/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("COMPANY"))
                .andExpect(jsonPath("$[0].name").value("Company"));
    }

    @Test
    void getsCustomersIncludingDefaultCategory() throws Exception {
        when(masterDataQueryService.getCustomers()).thenReturn(List.of(
                new CustomerOptionResponse(
                        2L, "CUSTOMER", "Customer", RequestCategory.EXPENSE
                )
        ));

        mockMvc.perform(get("/api/master/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].defaultRequestCategory").value("EXPENSE"));
    }

    @Test
    void getsExpenseTypesIncludingCalculationType() throws Exception {
        when(masterDataQueryService.getExpenseTypes()).thenReturn(List.of(
                new ExpenseTypeOptionResponse(
                        3L, "MEAL", "Meal", CalculationType.MEAL
                )
        ));

        mockMvc.perform(get("/api/master/expense-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].calculationType").value("MEAL"));
    }

    @Test
    void getsExpensePrices() throws Exception {
        when(masterDataQueryService.getExpensePrices(3L)).thenReturn(List.of(
                new ExpensePriceOptionResponse(
                        8L,
                        "REGISTERED_MAIL",
                        "Registered mail",
                        new BigDecimal("28.00"),
                        LocalDate.of(2026, 1, 1),
                        null
                )
        ));

        mockMvc.perform(get("/api/master/expense-types/3/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].priceCode").value("REGISTERED_MAIL"))
                .andExpect(jsonPath("$[0].unitPrice").value(28.00))
                .andExpect(jsonPath("$[0].effectiveFrom").value("2026-01-01"))
                .andExpect(jsonPath("$[0].effectiveTo").doesNotExist());
    }

    @Test
    void returnsEmptyList() throws Exception {
        when(masterDataQueryService.getCompanies()).thenReturn(List.of());

        mockMvc.perform(get("/api/master/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void mapsInvalidExpenseTypeIdToBadRequest() throws Exception {
        when(masterDataQueryService.getExpensePrices(0L)).thenThrow(
                new PaymentDraftBusinessException(
                        "INVALID_EXPENSE_TYPE_ID",
                        "expenseTypeId must be greater than zero"
                )
        );

        mockMvc.perform(get("/api/master/expense-types/0/prices"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXPENSE_TYPE_ID"));
    }

    @Test
    void mapsMissingExpenseTypeToNotFound() throws Exception {
        when(masterDataQueryService.getExpensePrices(99L)).thenThrow(
                new PaymentDraftBusinessException(
                        "EXPENSE_TYPE_NOT_FOUND",
                        "Expense type not found: 99"
                )
        );

        mockMvc.perform(get("/api/master/expense-types/99/prices"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXPENSE_TYPE_NOT_FOUND"));
    }

    @Test
    void hidesUnexpectedException() throws Exception {
        when(masterDataQueryService.getExpensePrices(3L))
                .thenThrow(new RuntimeException("sensitive database detail"));

        mockMvc.perform(get("/api/master/expense-types/3/prices"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
