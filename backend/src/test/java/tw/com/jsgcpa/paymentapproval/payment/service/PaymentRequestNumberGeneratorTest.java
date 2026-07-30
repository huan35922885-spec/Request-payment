package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PaymentRequestNumberGeneratorTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    @Test
    void generateReturnsDateBasedNumberWithSequenceValue() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Clock fixedClock = Clock.fixed(
                ZonedDateTime.of(
                        2026, 7, 30, 10, 0, 0, 0, BUSINESS_ZONE
                ).toInstant(),
                BUSINESS_ZONE
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(42L);

        PaymentRequestNumberGenerator generator =
                new PaymentRequestNumberGenerator(jdbcTemplate, fixedClock);

        assertEquals("PAY-20260730-000042", generator.generate());
    }

    @Test
    void generateThrowsWhenSequenceValueIsNull() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Clock fixedClock = Clock.fixed(
                ZonedDateTime.of(
                        2026, 7, 30, 10, 0, 0, 0, BUSINESS_ZONE
                ).toInstant(),
                BUSINESS_ZONE
        );
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(null);

        PaymentRequestNumberGenerator generator =
                new PaymentRequestNumberGenerator(jdbcTemplate, fixedClock);

        assertThrows(IllegalStateException.class, generator::generate);
    }
}
