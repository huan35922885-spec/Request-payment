package tw.com.jsgcpa.paymentapproval.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.CreatePaymentDraftItemRequest;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;

@Component
public class PaymentDraftItemCalculator {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final ExpensePriceResolver priceResolver;

    public PaymentDraftItemCalculator(ExpensePriceResolver priceResolver) {
        this.priceResolver = priceResolver;
    }

    public CalculatedPaymentDraftItem calculate(
            ExpenseType expenseType,
            CreatePaymentDraftItemRequest request
    ) {
        CalculationType calculationType = expenseType.getCalculationType();
        if (calculationType == null) {
            throw invalid(calculationType, "calculationType is required");
        }

        return switch (calculationType) {
            case MANUAL -> calculateManual(expenseType, request);
            case TRAVEL -> calculateTravel(expenseType, request);
            case MEAL -> calculateMeal(expenseType, request);
            case QUANTITY_PRICE -> calculateQuantityPrice(expenseType, request);
            case CONFIRMATION -> calculateConfirmation(expenseType, request);
        };
    }

    private CalculatedPaymentDraftItem calculateManual(
            ExpenseType expenseType,
            CreatePaymentDraftItemRequest request
    ) {
        validateManualInput(expenseType, request);
        return manualResult(request.manualAmount());
    }

    private CalculatedPaymentDraftItem calculateTravel(
            ExpenseType expenseType,
            CreatePaymentDraftItemRequest request
    ) {
        validateManualInput(expenseType, request);
        return manualResult(request.manualAmount());
    }

    private CalculatedPaymentDraftItem calculateMeal(
            ExpenseType expenseType,
            CreatePaymentDraftItemRequest request
    ) {
        validateMultiplier(expenseType.getCalculationType(), request.multiplier(), true);
        requirePositive(expenseType.getCalculationType(), "peopleCount", request.peopleCount());
        requirePositive(expenseType.getCalculationType(), "days", request.days());
        requirePositive(expenseType.getCalculationType(), "quantity", request.quantity());
        if (request.manualAmount() != null) {
            throw invalid(expenseType.getCalculationType(), "manualAmount must be null");
        }

        ExpensePriceSetting priceSetting =
                priceResolver.resolve(expenseType.getId(), request.priceCode());
        BigDecimal amount = BigDecimal.valueOf(request.peopleCount())
                .multiply(BigDecimal.valueOf(request.days()))
                .multiply(request.quantity())
                .multiply(priceSetting.getUnitPrice())
                .setScale(MONEY_SCALE, MONEY_ROUNDING);

        return new CalculatedPaymentDraftItem(
                priceSetting,
                priceSetting.getUnitPrice(),
                ONE,
                amount
        );
    }

    private CalculatedPaymentDraftItem calculateQuantityPrice(
            ExpenseType expenseType,
            CreatePaymentDraftItemRequest request
    ) {
        return calculateQuantityBased(expenseType, request);
    }

    private CalculatedPaymentDraftItem calculateConfirmation(
            ExpenseType expenseType,
            CreatePaymentDraftItemRequest request
    ) {
        return calculateQuantityBased(expenseType, request);
    }

    private CalculatedPaymentDraftItem calculateQuantityBased(
            ExpenseType expenseType,
            CreatePaymentDraftItemRequest request
    ) {
        CalculationType calculationType = expenseType.getCalculationType();
        requirePositive(calculationType, "quantity", request.quantity());
        if (request.manualAmount() != null) {
            throw invalid(calculationType, "manualAmount must be null");
        }

        BigDecimal multiplier = normalizedMultiplier(calculationType, request.multiplier());
        ExpensePriceSetting priceSetting =
                priceResolver.resolve(expenseType.getId(), request.priceCode());
        BigDecimal amount = request.quantity()
                .multiply(priceSetting.getUnitPrice())
                .multiply(multiplier)
                .setScale(MONEY_SCALE, MONEY_ROUNDING);

        return new CalculatedPaymentDraftItem(
                priceSetting,
                priceSetting.getUnitPrice(),
                multiplier,
                amount
        );
    }

    private void validateManualInput(
            ExpenseType expenseType,
            CreatePaymentDraftItemRequest request
    ) {
        CalculationType calculationType = expenseType.getCalculationType();
        requireNonNegative(calculationType, "manualAmount", request.manualAmount());
        if (request.priceCode() != null) {
            throw invalid(calculationType, "priceCode must be null");
        }
        validateMultiplier(calculationType, request.multiplier(), true);
    }

    private CalculatedPaymentDraftItem manualResult(BigDecimal manualAmount) {
        return new CalculatedPaymentDraftItem(
                null,
                null,
                ONE,
                manualAmount.setScale(MONEY_SCALE, MONEY_ROUNDING)
        );
    }

    private BigDecimal normalizedMultiplier(
            CalculationType calculationType,
            BigDecimal multiplier
    ) {
        if (multiplier == null) {
            return ONE;
        }
        if (multiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid(calculationType, "multiplier must be greater than zero");
        }
        return multiplier;
    }

    private void validateMultiplier(
            CalculationType calculationType,
            BigDecimal multiplier,
            boolean mustBeOne
    ) {
        BigDecimal normalized = normalizedMultiplier(calculationType, multiplier);
        if (mustBeOne && normalized.compareTo(ONE) != 0) {
            throw invalid(calculationType, "multiplier must be 1");
        }
    }

    private void requirePositive(
            CalculationType calculationType,
            String field,
            Integer value
    ) {
        if (value == null || value <= 0) {
            throw invalid(calculationType, field + " must be greater than zero");
        }
    }

    private void requirePositive(
            CalculationType calculationType,
            String field,
            BigDecimal value
    ) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid(calculationType, field + " must be greater than zero");
        }
    }

    private void requireNonNegative(
            CalculationType calculationType,
            String field,
            BigDecimal value
    ) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw invalid(calculationType, field + " must be zero or greater");
        }
    }

    private PaymentDraftBusinessException invalid(
            CalculationType calculationType,
            String detail
    ) {
        return new PaymentDraftBusinessException(
                "INVALID_CALCULATION_INPUT",
                "Invalid calculation input for " + calculationType + ": " + detail
        );
    }
}
