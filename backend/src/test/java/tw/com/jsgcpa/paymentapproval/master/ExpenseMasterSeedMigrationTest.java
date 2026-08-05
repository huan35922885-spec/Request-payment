package tw.com.jsgcpa.paymentapproval.master;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ExpenseMasterSeedMigrationTest {

    private static final LocalDate EFFECTIVE_FROM = LocalDate.of(2026, 8, 4);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsConfirmedExpenseTypesWithoutFixedIds() {
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM expense_types
                        WHERE code = 'MEAL'
                          AND name = '餐費'
                          AND calculation_type = 'MEAL'
                          AND active = TRUE
                        """,
                        Integer.class
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM expense_types
                        WHERE code = 'CONFIRMATION'
                          AND name = '函證'
                          AND calculation_type = 'CONFIRMATION'
                          AND active = TRUE
                        """,
                        Integer.class
                )
        );
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM expense_price_settings
                        WHERE price_code = 'EXPRESS_REGISTERED'
                        """,
                        Integer.class
                )
        );
    }

    @Test
    void seedsConfirmedPriceSettings() {
        assertPrice("MEAL", "DEFAULT", "一般餐費", "80.00");
        assertPrice("CONFIRMATION", "NORMAL_MAIL", "平信", "8.00");
        assertPrice("CONFIRMATION", "REGISTERED_MAIL", "掛號", "28.00");
        assertPrice("CONFIRMATION", "EXPRESS_REGISTERED_MAIL", "限時掛號", "35.00");

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM expense_price_settings
                        WHERE expense_type_id IN (
                            SELECT id
                            FROM expense_types
                            WHERE code IN ('MEAL', 'CONFIRMATION')
                        )
                          AND unit_price = 0
                        """,
                        Integer.class
                )
        );
    }

    @Test
    void recordsFlywaySchemaVersionFive() {
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM flyway_schema_history
                        WHERE version = '5'
                          AND success = TRUE
                        """,
                        Integer.class
                )
        );
    }

    private void assertPrice(
            String expenseTypeCode,
            String priceCode,
            String priceName,
            String unitPrice
    ) {
        var row = jdbcTemplate.queryForMap(
                """
                SELECT eps.price_name,
                       eps.unit_price,
                       eps.effective_from,
                       eps.effective_to,
                       eps.active
                FROM expense_price_settings eps
                JOIN expense_types et ON et.id = eps.expense_type_id
                WHERE et.code = ?
                  AND eps.price_code = ?
                  AND eps.active = TRUE
                  AND eps.effective_from <= DATE '2026-08-04'
                  AND eps.effective_to IS NULL
                """,
                expenseTypeCode,
                priceCode
        );

        assertEquals(priceName, row.get("price_name"));
        assertEquals(new BigDecimal(unitPrice), row.get("unit_price"));
        assertEquals(
                EFFECTIVE_FROM,
                ((java.sql.Date) row.get("effective_from")).toLocalDate()
        );
        assertEquals(null, row.get("effective_to"));
        assertEquals(true, row.get("active"));
    }
}
