package tw.com.jsgcpa.paymentapproval.master.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpensePriceOptionResponse(
        Long id,
        String priceCode,
        String priceName,
        BigDecimal unitPrice,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
