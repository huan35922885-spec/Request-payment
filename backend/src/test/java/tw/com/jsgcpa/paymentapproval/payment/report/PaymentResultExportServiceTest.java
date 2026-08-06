package tw.com.jsgcpa.paymentapproval.payment.report;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;

@SpringBootTest
class PaymentResultExportServiceTest {

    @Autowired private PaymentResultExportService exportService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsInvalidPeriod() {
        assertThrows(
                PaymentDraftBusinessException.class,
                () -> exportService.exportExcel(
                        LocalDate.parse("2026-08-10"),
                        LocalDate.parse("2026-08-01")
                )
        );
    }

    @Test
    void buildsWorkbookForEmptyResultSet() {
        byte[] content = exportService.exportExcel(
                LocalDate.parse("2099-01-01"),
                LocalDate.parse("2099-01-31")
        );
        assertTrue(content.length > 0);
        assertTrue(jdbcTemplate != null);
    }
}
