package tw.com.jsgcpa.paymentapproval.master.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.com.jsgcpa.paymentapproval.master.dto.response.CompanyOptionResponse;
import tw.com.jsgcpa.paymentapproval.master.dto.response.CustomerOptionResponse;
import tw.com.jsgcpa.paymentapproval.master.dto.response.ExpensePriceOptionResponse;
import tw.com.jsgcpa.paymentapproval.master.dto.response.ExpenseTypeOptionResponse;
import tw.com.jsgcpa.paymentapproval.master.service.MasterDataQueryService;

@RestController
@RequestMapping("/api/master")
public class MasterDataController {

    private final MasterDataQueryService masterDataQueryService;

    public MasterDataController(MasterDataQueryService masterDataQueryService) {
        this.masterDataQueryService = masterDataQueryService;
    }

    @GetMapping("/companies")
    public ResponseEntity<List<CompanyOptionResponse>> getCompanies() {
        return ResponseEntity.ok(masterDataQueryService.getCompanies());
    }

    @GetMapping("/customers")
    public ResponseEntity<List<CustomerOptionResponse>> getCustomers() {
        return ResponseEntity.ok(masterDataQueryService.getCustomers());
    }

    @GetMapping("/expense-types")
    public ResponseEntity<List<ExpenseTypeOptionResponse>> getExpenseTypes() {
        return ResponseEntity.ok(masterDataQueryService.getExpenseTypes());
    }

    @GetMapping("/expense-types/{expenseTypeId}/prices")
    public ResponseEntity<List<ExpensePriceOptionResponse>> getExpensePrices(
            @PathVariable Long expenseTypeId
    ) {
        return ResponseEntity.ok(
                masterDataQueryService.getExpensePrices(expenseTypeId)
        );
    }
}
