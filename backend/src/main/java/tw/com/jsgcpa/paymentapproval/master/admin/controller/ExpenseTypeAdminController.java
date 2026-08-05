package tw.com.jsgcpa.paymentapproval.master.admin.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ExpenseTypeVersionRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.RenameExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.response.ExpenseTypeAdminResponse;
import tw.com.jsgcpa.paymentapproval.master.admin.service.ExpenseTypeAdminService;
import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;

@RestController
@RequestMapping("/api/admin/master/expense-types")
public class ExpenseTypeAdminController {

    private final ExpenseTypeAdminService service;

    public ExpenseTypeAdminController(ExpenseTypeAdminService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ExpenseTypeAdminResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    public ResponseEntity<ExpenseTypeAdminResponse> create(
            @Valid @RequestBody CreateExpenseTypeRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request, principal.getUserId()));
    }

    @PatchMapping("/{id}/name")
    public ResponseEntity<ExpenseTypeAdminResponse> rename(
            @PathVariable Long id,
            @Valid @RequestBody RenameExpenseTypeRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(service.rename(id, request, principal.getUserId()));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ExpenseTypeAdminResponse> activate(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseTypeVersionRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(service.activate(id, request, principal.getUserId()));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ExpenseTypeAdminResponse> deactivate(
            @PathVariable Long id,
            @Valid @RequestBody DeactivateExpenseTypeRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        return ResponseEntity.ok(
                service.deactivate(id, request, principal.getUserId())
        );
    }
}
