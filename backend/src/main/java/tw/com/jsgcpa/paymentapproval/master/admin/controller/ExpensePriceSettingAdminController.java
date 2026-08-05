package tw.com.jsgcpa.paymentapproval.master.admin.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ExpensePriceSettingVersionRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.UpdateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.response.ExpensePriceSettingAdminResponse;
import tw.com.jsgcpa.paymentapproval.master.admin.service.ExpensePriceSettingAdminService;
import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;

@RestController
@RequestMapping("/api/admin/master")
public class ExpensePriceSettingAdminController {

    private static final String ISO_DATE = "yyyy-MM-dd";

    private final ExpensePriceSettingAdminService service;

    public ExpensePriceSettingAdminController(ExpensePriceSettingAdminService service) {
        this.service = service;
    }

    @GetMapping("/expense-price-settings")
    public ResponseEntity<List<ExpensePriceSettingAdminResponse>> list(
            @RequestParam(required = false) Long expenseTypeId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = ISO_DATE) LocalDate effectiveOn
    ) {
        return ResponseEntity.ok(service.list(expenseTypeId, active, effectiveOn));
    }

    @GetMapping("/expense-types/{expenseTypeId}/price-settings")
    public ResponseEntity<List<ExpensePriceSettingAdminResponse>> listForExpenseType(
            @PathVariable Long expenseTypeId
    ) {
        return ResponseEntity.ok(service.listForExpenseType(expenseTypeId));
    }

    @GetMapping("/expense-types/{expenseTypeId}/price-settings/effective")
    public ResponseEntity<ExpensePriceSettingAdminResponse> effective(
            @PathVariable Long expenseTypeId,
            @RequestParam
            @DateTimeFormat(pattern = ISO_DATE) LocalDate date
    ) {
        return ResponseEntity.ok(service.effective(expenseTypeId, date));
    }

    @PostMapping("/expense-types/{expenseTypeId}/price-settings")
    public ResponseEntity<ExpensePriceSettingAdminResponse> create(
            @PathVariable Long expenseTypeId,
            @Valid @RequestBody CreateExpensePriceSettingRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.create(expenseTypeId, request, principal.getUserId())
        );
    }

    @PatchMapping("/expense-price-settings/{id}")
    public ResponseEntity<ExpensePriceSettingAdminResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateExpensePriceSettingRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(service.update(id, request, principal.getUserId()));
    }

    @PostMapping("/expense-price-settings/{id}/activate")
    public ResponseEntity<ExpensePriceSettingAdminResponse> activate(
            @PathVariable Long id,
            @Valid @RequestBody ExpensePriceSettingVersionRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(service.activate(id, request, principal.getUserId()));
    }

    @PostMapping("/expense-price-settings/{id}/deactivate")
    public ResponseEntity<ExpensePriceSettingAdminResponse> deactivate(
            @PathVariable Long id,
            @Valid @RequestBody DeactivateExpensePriceSettingRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(
                service.deactivate(id, request, principal.getUserId())
        );
    }
}
