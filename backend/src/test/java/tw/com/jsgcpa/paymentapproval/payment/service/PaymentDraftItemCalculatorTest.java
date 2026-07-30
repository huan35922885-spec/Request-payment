package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CreatePaymentDraftItemRequest;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;

class PaymentDraftItemCalculatorTest {

    @Test
    void manualUsesManualAmountWithoutResolver() {
        ExpensePriceResolver resolver = mock(ExpensePriceResolver.class);
        PaymentDraftItemCalculator calculator = new PaymentDraftItemCalculator(resolver);

        CalculatedPaymentDraftItem result = calculator.calculate(
                expenseType(CalculationType.MANUAL),
                request(null, null, null, null, null, "100.005", null, null)
        );

        assertEquals(new BigDecimal("100.01"), result.amount());
        assertEquals(BigDecimal.ONE, result.multiplier());
        verifyNoInteractions(resolver);
    }

    @Test
    void travelUsesManualAmountWithoutResolver() {
        ExpensePriceResolver resolver = mock(ExpensePriceResolver.class);
        PaymentDraftItemCalculator calculator = new PaymentDraftItemCalculator(resolver);

        CalculatedPaymentDraftItem result = calculator.calculate(
                expenseType(CalculationType.TRAVEL),
                request(null, null, null, null, null, "250.00", null, null)
        );

        assertEquals(new BigDecimal("250.00"), result.amount());
        verifyNoInteractions(resolver);
    }

    @Test
    void mealCalculatesPeopleDaysQuantityAndUnitPrice() {
        ExpensePriceResolver resolver = mock(ExpensePriceResolver.class);
        ExpensePriceSetting priceSetting = price("80");
        when(resolver.resolve(1L, null)).thenReturn(priceSetting);
        PaymentDraftItemCalculator calculator = new PaymentDraftItemCalculator(resolver);

        CalculatedPaymentDraftItem result = calculator.calculate(
                expenseType(CalculationType.MEAL),
                request(null, 2, 3, "2", null, null, null, null)
        );

        assertEquals(new BigDecimal("960.00"), result.amount());
        assertEquals(BigDecimal.ONE, result.multiplier());
    }

    @Test
    void quantityPriceUsesQuantityUnitPriceAndMultiplier() {
        ExpensePriceResolver resolver = mock(ExpensePriceResolver.class);
        when(resolver.resolve(1L, null)).thenReturn(price("12.50"));
        PaymentDraftItemCalculator calculator = new PaymentDraftItemCalculator(resolver);

        CalculatedPaymentDraftItem result = calculator.calculate(
                expenseType(CalculationType.QUANTITY_PRICE),
                request(null, null, null, "10", "1.5", null, null, null)
        );

        assertEquals(new BigDecimal("187.50"), result.amount());
    }

    @Test
    void confirmationUsesMultiplier() {
        ExpensePriceResolver resolver = mock(ExpensePriceResolver.class);
        when(resolver.resolve(1L, null)).thenReturn(price("28"));
        PaymentDraftItemCalculator calculator = new PaymentDraftItemCalculator(resolver);

        CalculatedPaymentDraftItem result = calculator.calculate(
                expenseType(CalculationType.CONFIRMATION),
                request(null, null, null, "10", "2", null, null, null)
        );

        assertEquals(new BigDecimal("560.00"), result.amount());
    }

    @Test
    void nullMultiplierDefaultsToOne() {
        ExpensePriceResolver resolver = mock(ExpensePriceResolver.class);
        when(resolver.resolve(1L, null)).thenReturn(price("12.50"));
        PaymentDraftItemCalculator calculator = new PaymentDraftItemCalculator(resolver);

        CalculatedPaymentDraftItem result = calculator.calculate(
                expenseType(CalculationType.QUANTITY_PRICE),
                request(null, null, null, "10", null, null, null, null)
        );

        assertEquals(BigDecimal.ONE, result.multiplier());
        assertEquals(new BigDecimal("125.00"), result.amount());
    }

    @Test
    void missingManualAmountThrowsBusinessException() {
        PaymentDraftItemCalculator calculator =
                new PaymentDraftItemCalculator(mock(ExpensePriceResolver.class));

        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> calculator.calculate(
                        expenseType(CalculationType.MANUAL),
                        request(null, null, null, null, null, null, null, null)
                )
        );

        assertEquals("INVALID_CALCULATION_INPUT", exception.getCode());
    }

    @Test
    void missingMealPeopleCountThrowsBusinessException() {
        PaymentDraftItemCalculator calculator =
                new PaymentDraftItemCalculator(mock(ExpensePriceResolver.class));

        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> calculator.calculate(
                        expenseType(CalculationType.MEAL),
                        request(null, null, 3, "2", null, null, null, null)
                )
        );

        assertEquals("INVALID_CALCULATION_INPUT", exception.getCode());
    }

    @Test
    void manualAmountIsRejectedForQuantityCalculation() {
        PaymentDraftItemCalculator calculator =
                new PaymentDraftItemCalculator(mock(ExpensePriceResolver.class));

        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> calculator.calculate(
                        expenseType(CalculationType.QUANTITY_PRICE),
                        request(null, null, null, "10", null, "5", null, null)
                )
        );

        assertEquals("INVALID_CALCULATION_INPUT", exception.getCode());
    }

    @Test
    void manualRejectsPriceCode() {
        PaymentDraftItemCalculator calculator =
                new PaymentDraftItemCalculator(mock(ExpensePriceResolver.class));

        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> calculator.calculate(
                        expenseType(CalculationType.MANUAL),
                        request("DEFAULT", null, null, null, null, "5", null, null)
                )
        );

        assertEquals("INVALID_CALCULATION_INPUT", exception.getCode());
    }

    @Test
    void roundingUsesHalfUp() {
        ExpensePriceResolver resolver = mock(ExpensePriceResolver.class);
        when(resolver.resolve(1L, null)).thenReturn(price("1"));
        PaymentDraftItemCalculator calculator = new PaymentDraftItemCalculator(resolver);

        CalculatedPaymentDraftItem result = calculator.calculate(
                expenseType(CalculationType.QUANTITY_PRICE),
                request(null, null, null, "1.005", null, null, null, null)
        );

        assertEquals(new BigDecimal("1.01"), result.amount());
    }

    private ExpenseType expenseType(CalculationType calculationType) {
        ExpenseType expenseType = mock(ExpenseType.class);
        when(expenseType.getId()).thenReturn(1L);
        when(expenseType.getCalculationType()).thenReturn(calculationType);
        return expenseType;
    }

    private ExpensePriceSetting price(String unitPrice) {
        ExpensePriceSetting priceSetting = new ExpensePriceSetting();
        priceSetting.setUnitPrice(new BigDecimal(unitPrice));
        return priceSetting;
    }

    private CreatePaymentDraftItemRequest request(
            String priceCode,
            Integer peopleCount,
            Integer days,
            String quantity,
            String multiplier,
            String manualAmount,
            Map<String, Object> extraData,
            Integer sortOrder
    ) {
        return new CreatePaymentDraftItemRequest(
                1L,
                priceCode,
                null,
                peopleCount,
                days,
                quantity == null ? null : new BigDecimal(quantity),
                multiplier == null ? null : new BigDecimal(multiplier),
                manualAmount == null ? null : new BigDecimal(manualAmount),
                extraData,
                sortOrder
        );
    }
}
