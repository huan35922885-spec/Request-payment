package tw.com.jsgcpa.paymentapproval.master.admin.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateExpensePriceSettingRequest(
        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount,

        @NotNull
        LocalDate effectiveFrom,

        LocalDate effectiveTo,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
