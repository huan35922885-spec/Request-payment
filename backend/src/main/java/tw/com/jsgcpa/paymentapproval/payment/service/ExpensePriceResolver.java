package tw.com.jsgcpa.paymentapproval.payment.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpensePriceSettingRepository;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;

@Component
public class ExpensePriceResolver {

    private static final String DEFAULT_PRICE_CODE = "DEFAULT";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final ExpensePriceSettingRepository repository;
    private final Clock clock;

    @Autowired
    public ExpensePriceResolver(ExpensePriceSettingRepository repository) {
        this(repository, Clock.system(BUSINESS_ZONE));
    }

    ExpensePriceResolver(ExpensePriceSettingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public ExpensePriceSetting resolve(Long expenseTypeId, String requestedPriceCode) {
        return resolve(expenseTypeId, requestedPriceCode, LocalDate.now(clock));
    }

    public ExpensePriceSetting resolve(
            Long expenseTypeId,
            String requestedPriceCode,
            LocalDate effectiveDate
    ) {
        String priceCode = requestedPriceCode == null
                ? DEFAULT_PRICE_CODE
                : requestedPriceCode.strip().toUpperCase(Locale.ROOT);

        List<ExpensePriceSetting> priceSettings =
                repository.findEffectivePriceSettings(
                        expenseTypeId,
                        priceCode,
                        effectiveDate
                );

        if (priceSettings.isEmpty()) {
            throw new PaymentDraftBusinessException(
                    "PRICE_SETTING_NOT_FOUND",
                    "No effective price setting found for expenseTypeId="
                            + expenseTypeId
                            + ", priceCode="
                            + priceCode
                            + ", effectiveDate="
                            + effectiveDate
            );
        }

        if (priceSettings.size() > 1) {
            throw new PaymentDraftBusinessException(
                    "PRICE_SETTING_CONFLICT",
                    "Multiple effective price settings found for expenseTypeId="
                            + expenseTypeId
                            + ", priceCode="
                            + priceCode
                            + ", effectiveDate="
                            + effectiveDate
            );
        }

        return priceSettings.get(0);
    }
}
