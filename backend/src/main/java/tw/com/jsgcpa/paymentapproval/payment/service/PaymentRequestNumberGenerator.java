package tw.com.jsgcpa.paymentapproval.payment.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestNumberGenerator {

    private static final String SEQUENCE_SQL =
            "SELECT nextval('payment_request_no_seq')";
    private static final String PREFIX = "PAY";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public PaymentRequestNumberGenerator(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.system(BUSINESS_ZONE));
    }

    PaymentRequestNumberGenerator(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public String generate() {
        Long sequenceValue = jdbcTemplate.queryForObject(
                SEQUENCE_SQL,
                Long.class
        );

        if (sequenceValue == null) {
            throw new IllegalStateException(
                    "Unable to obtain payment request sequence value"
            );
        }

        String datePart = LocalDate.now(clock).format(DATE_FORMATTER);
        return "%s-%s-%06d".formatted(PREFIX, datePart, sequenceValue);
    }
}
