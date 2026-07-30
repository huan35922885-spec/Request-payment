package tw.com.jsgcpa.paymentapproval.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

public record CreatePaymentDraftItemRequest(
        @NotNull
        @Positive
        Long expenseTypeId,

        @Size(max = 50)
        String priceCode,

        @Size(max = 2000)
        String description,

        @Positive
        Integer peopleCount,

        @Positive
        Integer days,

        @DecimalMin(value = "0.01")
        @Digits(integer = 12, fraction = 2)
        BigDecimal quantity,

        @DecimalMin(value = "0.01")
        @Digits(integer = 8, fraction = 2)
        BigDecimal multiplier,

        @DecimalMin(value = "0.00")
        @Digits(integer = 12, fraction = 2)
        BigDecimal manualAmount,

        Map<String, Object> extraData,

        @Positive
        Integer sortOrder
) {
}
