package tw.com.jsgcpa.paymentapproval.payment.service;

import java.math.BigDecimal;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;

public record CalculatedPaymentDraftItem(
        ExpensePriceSetting priceSetting,
        BigDecimal unitPrice,
        BigDecimal multiplier,
        BigDecimal amount
) {
}
