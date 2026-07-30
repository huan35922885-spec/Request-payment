package tw.com.jsgcpa.paymentapproval.payment.dto.response;

import java.math.BigDecimal;
import java.util.Map;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;

public record CreatePaymentDraftItemResponse(
        Long id,
        Long expenseTypeId,
        String expenseTypeCode,
        String expenseTypeName,
        CalculationType calculationType,
        Long priceSettingId,
        String priceCode,
        String priceName,
        String description,
        Integer peopleCount,
        Integer days,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal multiplier,
        BigDecimal amount,
        Map<String, Object> extraData,
        Integer sortOrder
) {
}
