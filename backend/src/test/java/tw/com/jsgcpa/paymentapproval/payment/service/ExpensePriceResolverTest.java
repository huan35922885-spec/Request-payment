package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpensePriceSettingRepository;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;

class ExpensePriceResolverTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");
    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 7, 30);

    @Test
    void nullPriceCodeUsesDefault() {
        ExpensePriceSettingRepository repository = mock(ExpensePriceSettingRepository.class);
        ExpensePriceSetting expected = new ExpensePriceSetting();
        when(repository.findEffectivePriceSettings(
                7L, "DEFAULT", EFFECTIVE_DATE
        )).thenReturn(List.of(expected));

        ExpensePriceSetting actual = resolver(repository).resolve(7L, null);

        assertSame(expected, actual);
    }

    @Test
    void explicitPriceCodeIsUsed() {
        ExpensePriceSettingRepository repository = mock(ExpensePriceSettingRepository.class);
        ExpensePriceSetting expected = new ExpensePriceSetting();
        when(repository.findEffectivePriceSettings(
                7L, "REGISTERED_MAIL", EFFECTIVE_DATE
        )).thenReturn(List.of(expected));

        ExpensePriceSetting actual = resolver(repository)
                .resolve(7L, "REGISTERED_MAIL");

        assertSame(expected, actual);
    }

    @Test
    void priceCodeIsTrimmedBeforeLookup() {
        ExpensePriceSettingRepository repository = mock(ExpensePriceSettingRepository.class);
        ExpensePriceSetting expected = new ExpensePriceSetting();
        when(repository.findEffectivePriceSettings(
                7L, "REGISTERED_MAIL", EFFECTIVE_DATE
        )).thenReturn(List.of(expected));

        ExpensePriceSetting actual = resolver(repository)
                .resolve(7L, "  REGISTERED_MAIL  ");

        assertSame(expected, actual);
    }

    @Test
    void oneEffectiveSettingIsReturned() {
        ExpensePriceSettingRepository repository = mock(ExpensePriceSettingRepository.class);
        ExpensePriceSetting expected = new ExpensePriceSetting();
        when(repository.findEffectivePriceSettings(
                eq(7L), eq("DEFAULT"), eq(EFFECTIVE_DATE)
        )).thenReturn(List.of(expected));

        assertSame(expected, resolver(repository).resolve(7L, null));
    }

    @Test
    void noEffectiveSettingThrowsNotFound() {
        ExpensePriceSettingRepository repository = mock(ExpensePriceSettingRepository.class);
        when(repository.findEffectivePriceSettings(
                7L, "DEFAULT", EFFECTIVE_DATE
        )).thenReturn(List.of());

        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> resolver(repository).resolve(7L, null)
        );

        assertEquals("PRICE_SETTING_NOT_FOUND", exception.getCode());
    }

    @Test
    void multipleEffectiveSettingsThrowsConflict() {
        ExpensePriceSettingRepository repository = mock(ExpensePriceSettingRepository.class);
        when(repository.findEffectivePriceSettings(
                7L, "DEFAULT", EFFECTIVE_DATE
        )).thenReturn(List.of(new ExpensePriceSetting(), new ExpensePriceSetting()));

        PaymentDraftBusinessException exception = assertThrows(
                PaymentDraftBusinessException.class,
                () -> resolver(repository).resolve(7L, null)
        );

        assertEquals("PRICE_SETTING_CONFLICT", exception.getCode());
    }

    @Test
    void lookupUsesBusinessZoneClockDate() {
        ExpensePriceSettingRepository repository = mock(ExpensePriceSettingRepository.class);
        ExpensePriceSetting expected = new ExpensePriceSetting();
        when(repository.findEffectivePriceSettings(
                7L, "DEFAULT", EFFECTIVE_DATE
        )).thenReturn(List.of(expected));

        assertSame(expected, resolver(repository).resolve(7L, null));
    }

    private ExpensePriceResolver resolver(ExpensePriceSettingRepository repository) {
        Clock fixedClock = Clock.fixed(
                ZonedDateTime.of(
                        2026, 7, 30, 23, 30, 0, 0, BUSINESS_ZONE
                ).toInstant(),
                BUSINESS_ZONE
        );
        return new ExpensePriceResolver(repository, fixedClock);
    }
}
