package tw.com.jsgcpa.paymentapproval.master.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RenameExpenseTypeRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        @PositiveOrZero
        Long version
) {

    public RenameExpenseTypeRequest {
        name = name == null ? null : name.strip();
    }
}
