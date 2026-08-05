package tw.com.jsgcpa.paymentapproval.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ExpensePricePeriodExclusionConstraintTest {

    private static final LocalDate AUGUST_FIRST = LocalDate.of(2026, 8, 1);
    private static final LocalDate AUGUST_THIRTY_FIRST = LocalDate.of(2026, 8, 31);
    private static final LocalDate SEPTEMBER_FIRST = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEPTEMBER_THIRTIETH = LocalDate.of(2026, 9, 30);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate publicJdbcTemplate;

    @Test
    void rejectsOverlappingActivePeriodsForSameExpenseTypeAndPriceCode() {
        withV8Schema(schema -> {
            long expenseTypeId = schema.insertExpenseType("OVERLAP");
            schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    AUGUST_FIRST,
                    AUGUST_THIRTY_FIRST,
                    true
            );

            assertSqlState(
                    "23P01",
                    () -> schema.insertPrice(
                            expenseTypeId,
                            "DEFAULT",
                            LocalDate.of(2026, 8, 15),
                            SEPTEMBER_FIRST,
                            true
                    )
            );
            assertEquals(1, schema.countPrices(expenseTypeId));
        });
    }

    @Test
    void rejectsPeriodFullyContainedByExistingPeriod() {
        withV8Schema(schema -> {
            long expenseTypeId = schema.insertExpenseType("CONTAINING");
            schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    AUGUST_FIRST,
                    LocalDate.of(2026, 12, 31),
                    true
            );

            assertSqlState(
                    "23P01",
                    () -> schema.insertPrice(
                            expenseTypeId,
                            "DEFAULT",
                            SEPTEMBER_FIRST,
                            SEPTEMBER_THIRTIETH,
                            true
                    )
            );
            assertEquals(1, schema.countPrices(expenseTypeId));
        });
    }

    @Test
    void rejectsPeriodThatFullyContainsExistingPeriod() {
        withV8Schema(schema -> {
            long expenseTypeId = schema.insertExpenseType("CONTAINED");
            schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    SEPTEMBER_FIRST,
                    SEPTEMBER_THIRTIETH,
                    true
            );

            assertSqlState(
                    "23P01",
                    () -> schema.insertPrice(
                            expenseTypeId,
                            "DEFAULT",
                            AUGUST_FIRST,
                            LocalDate.of(2026, 12, 31),
                            true
                    )
            );
            assertEquals(1, schema.countPrices(expenseTypeId));
        });
    }

    @Test
    void rejectsSameDayInclusiveBoundaryOverlap() {
        withV8Schema(schema -> {
            long expenseTypeId = schema.insertExpenseType("INCLUSIVE_BOUNDARY");
            schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    AUGUST_FIRST,
                    SEPTEMBER_FIRST,
                    true
            );

            assertSqlState(
                    "23P01",
                    () -> schema.insertPrice(
                            expenseTypeId,
                            "DEFAULT",
                            SEPTEMBER_FIRST,
                            null,
                            true
                    )
            );
            assertEquals(1, schema.countPrices(expenseTypeId));
        });
    }

    @Test
    void allowsAdjacentPeriodsForSameExpenseTypeAndPriceCode() {
        withV8Schema(schema -> {
            long expenseTypeId = schema.insertExpenseType("ADJACENT");
            schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    AUGUST_FIRST,
                    AUGUST_THIRTY_FIRST,
                    true
            );
            schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    SEPTEMBER_FIRST,
                    SEPTEMBER_THIRTIETH,
                    true
            );

            assertEquals(2, schema.countPrices(expenseTypeId));
        });
    }

    @Test
    void allowsOverlappingPeriodsForDifferentPriceCodes() {
        withV8Schema(schema -> {
            long expenseTypeId = schema.insertExpenseType("PRICE_CODE");
            schema.insertPrice(
                    expenseTypeId,
                    "NORMAL_MAIL",
                    AUGUST_FIRST,
                    SEPTEMBER_THIRTIETH,
                    true
            );
            schema.insertPrice(
                    expenseTypeId,
                    "REGISTERED_MAIL",
                    AUGUST_FIRST,
                    SEPTEMBER_THIRTIETH,
                    true
            );

            assertEquals(2, schema.countPrices(expenseTypeId));
        });
    }

    @Test
    void allowsOverlappingPeriodsForDifferentExpenseTypes() {
        withV8Schema(schema -> {
            long firstExpenseTypeId = schema.insertExpenseType("TYPE_A");
            long secondExpenseTypeId = schema.insertExpenseType("TYPE_B");
            schema.insertPrice(
                    firstExpenseTypeId,
                    "DEFAULT",
                    AUGUST_FIRST,
                    SEPTEMBER_THIRTIETH,
                    true
            );
            schema.insertPrice(
                    secondExpenseTypeId,
                    "DEFAULT",
                    AUGUST_FIRST,
                    SEPTEMBER_THIRTIETH,
                    true
            );

            assertEquals(2, schema.countPrices(firstExpenseTypeId)
                    + schema.countPrices(secondExpenseTypeId));
        });
    }

    @Test
    void allowsOverlappingInactivePeriods() {
        withV8Schema(schema -> {
            long expenseTypeId = schema.insertExpenseType("INACTIVE");
            schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    AUGUST_FIRST,
                    SEPTEMBER_THIRTIETH,
                    false
            );
            schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    AUGUST_FIRST,
                    SEPTEMBER_THIRTIETH,
                    true
            );

            assertEquals(2, schema.countPrices(expenseTypeId));
        });
    }

    @Test
    void allowsTwoOverlappingInactivePeriods() {
        withV8Schema(schema -> {
            long expenseTypeId = schema.insertExpenseType("TWO_INACTIVE");
            schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    AUGUST_FIRST,
                    SEPTEMBER_THIRTIETH,
                    false
            );
            schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    AUGUST_FIRST,
                    SEPTEMBER_THIRTIETH,
                    false
            );

            assertEquals(2, schema.countPrices(expenseTypeId));
        });
    }

    @Test
    void rejectsReactivationWhenItWouldOverlapAnActivePeriod() {
        withV8Schema(schema -> {
            long expenseTypeId = schema.insertExpenseType("REACTIVATE");
            long inactiveId = schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    AUGUST_FIRST,
                    SEPTEMBER_THIRTIETH,
                    false
            );
            schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    AUGUST_FIRST,
                    SEPTEMBER_THIRTIETH,
                    true
            );

            assertSqlState(
                    "23P01",
                    () -> schema.activatePrice(inactiveId)
            );
            assertEquals(1, schema.countPrices(expenseTypeId, "active = TRUE"));
        });
    }

    @Test
    void rejectsOverlappingOpenEndedPeriods() {
        withV8Schema(schema -> {
            long expenseTypeId = schema.insertExpenseType("OPEN_ENDED");
            schema.insertPrice(
                    expenseTypeId,
                    "DEFAULT",
                    SEPTEMBER_FIRST,
                    null,
                    true
            );

            assertSqlState(
                    "23P01",
                    () -> schema.insertPrice(
                            expenseTypeId,
                            "DEFAULT",
                            LocalDate.of(2026, 9, 15),
                            LocalDate.of(2026, 10, 15),
                            true
                    )
            );
        });
    }

    @Test
    void rejectsEffectiveToBeforeEffectiveFromWithCheckConstraint() {
        withV8Schema(schema -> {
            long expenseTypeId = schema.insertExpenseType("INVALID_PERIOD");

            assertSqlState(
                    "23514",
                    () -> schema.insertPrice(
                            expenseTypeId,
                            "DEFAULT",
                            SEPTEMBER_THIRTIETH,
                            SEPTEMBER_FIRST,
                            true
                    )
            );
        });
    }

    private void assertSqlState(String expectedSqlState, Executable action) {
        DataAccessException exception = assertThrows(DataAccessException.class, action);
        assertEquals(expectedSqlState, sqlState(exception));
    }

    private String sqlState(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        throw new AssertionError("No SQLState found in exception", failure);
    }

    private void withV8Schema(Consumer<SchemaContext> work) {
        String schemaName = "expense_period_constraint_"
                + UUID.randomUUID().toString().replace("-", "");
        SchemaContext schema = new SchemaContext(schemaName);

        try {
            publicJdbcTemplate.execute("CREATE SCHEMA " + quoteIdentifier(schemaName));
            schema.migrateToV8();
            work.accept(schema);
        } finally {
            publicJdbcTemplate.execute(
                    "DROP SCHEMA IF EXISTS " + quoteIdentifier(schemaName) + " CASCADE"
            );
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private final class SchemaContext {

        private final String schemaName;
        private final JdbcTemplate jdbc;

        private SchemaContext(String schemaName) {
            this.schemaName = schemaName;
            this.jdbc = new JdbcTemplate(dataSource);
        }

        private void migrateToV8() {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas(schemaName)
                    .defaultSchema(schemaName)
                    .createSchemas(false)
                    .target(MigrationVersion.fromVersion("8"))
                    .load()
                    .migrate();
        }

        private String table(String tableName) {
            return quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
        }

        private long insertExpenseType(String code) {
            return jdbc.queryForObject(
                    "INSERT INTO " + table("expense_types")
                            + " (code, name, calculation_type, active) "
                            + "VALUES (?, ?, 'MANUAL', TRUE) RETURNING id",
                    Long.class,
                    code,
                    code + " type"
            );
        }

        private long insertPrice(
                long expenseTypeId,
                String priceCode,
                LocalDate effectiveFrom,
                LocalDate effectiveTo,
                boolean active
        ) {
            return jdbc.queryForObject(
                    "INSERT INTO " + table("expense_price_settings")
                            + " (expense_type_id, price_code, price_name, unit_price, "
                            + "effective_from, effective_to, active) VALUES (?, ?, ?, ?, ?, ?, ?) "
                            + "RETURNING id",
                    Long.class,
                    expenseTypeId,
                    priceCode,
                    priceCode + " price",
                    new BigDecimal("10.00"),
                    effectiveFrom,
                    effectiveTo,
                    active
            );
        }

        private void activatePrice(long priceId) {
            jdbc.update(
                    "UPDATE " + table("expense_price_settings")
                            + " SET active = TRUE WHERE id = ?",
                    priceId
            );
        }

        private int countPrices(long expenseTypeId) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table("expense_price_settings")
                            + " WHERE expense_type_id = ?",
                    Integer.class,
                    expenseTypeId
            );
        }

        private int countPrices(long expenseTypeId, String predicate) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table("expense_price_settings")
                            + " WHERE expense_type_id = ? AND " + predicate,
                    Integer.class,
                    expenseTypeId
            );
        }

    }
}
