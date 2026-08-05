package tw.com.jsgcpa.paymentapproval.master.admin.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

public record CreateExpensePriceSettingRequest(
        @NotBlank
        @Size(max = 50)
        String priceCode,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount,

        @NotNull
        LocalDate effectiveFrom,

        LocalDate effectiveTo
) {

    public CreateExpensePriceSettingRequest {
        priceCode = priceCode == null
                ? null
                : priceCode.strip().toUpperCase(Locale.ROOT);
    }
}
