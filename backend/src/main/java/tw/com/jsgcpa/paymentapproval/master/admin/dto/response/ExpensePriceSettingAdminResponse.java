package tw.com.jsgcpa.paymentapproval.master.admin.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ExpensePriceSettingAdminResponse(
        Long id,
        Long expenseTypeId,
        String expenseTypeCode,
        String expenseTypeName,
        String priceCode,
        String priceName,
        BigDecimal amount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active,
        Long version,
        Boolean effective,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
