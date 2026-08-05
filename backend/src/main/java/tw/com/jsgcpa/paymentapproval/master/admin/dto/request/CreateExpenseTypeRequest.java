package tw.com.jsgcpa.paymentapproval.master.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;

public record CreateExpenseTypeRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "[A-Z][A-Z0-9_]*")
        String code,

        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        CalculationType calculationType
) {

    public CreateExpenseTypeRequest {
        code = code == null ? null : code.strip().toUpperCase(Locale.ROOT);
        name = name == null ? null : name.strip();
    }
}
