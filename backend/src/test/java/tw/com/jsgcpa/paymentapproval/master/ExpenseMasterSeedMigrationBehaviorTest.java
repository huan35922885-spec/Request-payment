package tw.com.jsgcpa.paymentapproval.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ExpenseMasterSeedMigrationBehaviorTest {

    private static final LocalDate SEED_DATE = LocalDate.of(2026, 8, 4);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate publicJdbcTemplate;

    @Test
    void acceptsIdenticalOpenEndedDataAndDoesNotDuplicateIt() {
        withIsolatedSchema(schema -> {
            schema.migrateToFour();
            schema.insertExpenseType("MEAL", "餐費", "MEAL", true);
            schema.insertExpenseType("CONFIRMATION", "函證", "CONFIRMATION", true);
            schema.insertPrice(
                    "MEAL",
                    "DEFAULT",
                    "一般餐費",
                    new BigDecimal("80.00"),
                    LocalDate.of(2026, 1, 1),
                    null,
                    true
            );

            schema.migrateToFive();

            assertEquals(1, schema.count("expense_types", "code = 'MEAL'"));
            assertEquals(1, schema.count("expense_types", "code = 'CONFIRMATION'"));
            assertEquals(
                    1,
                    schema.count(
                            "expense_price_settings",
                            "price_code = 'DEFAULT' AND expense_type_id = "
                                    + "(SELECT id FROM " + schema.table("expense_types")
                                    + " WHERE code = 'MEAL')"
                    )
            );
            assertEquals(
                    LocalDate.of(2026, 1, 1),
                    schema.jdbc.queryForObject(
                            "SELECT effective_from FROM " + schema.table("expense_price_settings")
                                    + " WHERE price_code = 'DEFAULT'",
                            java.sql.Date.class
                    ).toLocalDate()
            );
            assertEquals(1, schema.successfulVersionFiveCount());
        });
    }

    @Test
    void rollsBackWhenExpenseTypeConflicts() {
        withIsolatedSchema(schema -> {
            schema.migrateToFour();
            schema.insertExpenseType("MEAL", "錯誤名稱", "MEAL", true);

            assertV5Failure(schema);

            assertEquals(1, schema.count("expense_types", "code = 'MEAL'"));
            assertEquals(0, schema.count("expense_types", "code = 'CONFIRMATION'"));
            assertEquals(0, schema.countAll("expense_price_settings"));
            assertEquals(0, schema.successfulVersionFiveCount());
        });
    }

    @Test
    void rollsBackWhenPriceSettingConflicts() {
        withIsolatedSchema(schema -> {
            schema.migrateToFour();
            schema.insertExpenseType("MEAL", "餐費", "MEAL", true);
            schema.insertExpenseType("CONFIRMATION", "函證", "CONFIRMATION", true);
            schema.insertPrice(
                    "MEAL",
                    "DEFAULT",
                    "一般餐費",
                    new BigDecimal("99.00"),
                    SEED_DATE,
                    null,
                    true
            );

            assertV5Failure(schema);

            assertEquals(
                    new BigDecimal("99.00"),
                    schema.jdbc.queryForObject(
                            "SELECT unit_price FROM " + schema.table("expense_price_settings")
                                    + " WHERE price_code = 'DEFAULT'",
                            BigDecimal.class
                    )
            );
            assertEquals(1, schema.countAll("expense_price_settings"));
            assertEquals(0, schema.successfulVersionFiveCount());
        });
    }

    @Test
    void preservesEndedHistoricalPriceAndCreatesNewPrice() {
        withIsolatedSchema(schema -> {
            schema.migrateToFour();
            schema.insertExpenseType("MEAL", "餐費", "MEAL", true);
            schema.insertExpenseType("CONFIRMATION", "函證", "CONFIRMATION", true);
            schema.insertPrice(
                    "MEAL",
                    "DEFAULT",
                    "一般餐費",
                    new BigDecimal("70.00"),
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 8, 3),
                    true
            );

            schema.migrateToFive();

            assertEquals(2, schema.count("expense_price_settings", "price_code = 'DEFAULT'"));
            assertEquals(
                    1,
                    schema.count(
                            "expense_price_settings",
                            "price_code = 'DEFAULT' AND unit_price = 70.00"
                    )
            );
            assertEquals(
                    1,
                    schema.count(
                            "expense_price_settings",
                            "price_code = 'DEFAULT' AND unit_price = 80.00"
                    )
            );
            assertEquals(1, schema.successfulVersionFiveCount());
        });
    }

    @Test
    void rejectsFutureOpenEndedPriceOverlapAndRollsBack() {
        withIsolatedSchema(schema -> {
            schema.migrateToFour();
            schema.insertExpenseType("MEAL", "餐費", "MEAL", true);
            schema.insertPrice(
                    "MEAL",
                    "DEFAULT",
                    "一般餐費",
                    new BigDecimal("80.00"),
                    LocalDate.of(2026, 9, 1),
                    null,
                    true
            );

            assertV5Failure(schema);

            assertEquals(0, schema.count("expense_types", "code = 'CONFIRMATION'"));
            assertEquals(1, schema.countAll("expense_price_settings"));
            assertEquals(0, schema.successfulVersionFiveCount());
        });
    }

    private void withIsolatedSchema(SchemaWork work) {
        String schemaName = "expense_seed_test_" + UUID.randomUUID().toString().replace("-", "");
        SchemaWorkContext context = new SchemaWorkContext(schemaName);
        try {
            publicJdbcTemplate.execute("CREATE SCHEMA " + quoteIdentifier(schemaName));
            work.run(context);
        } finally {
            publicJdbcTemplate.execute("DROP SCHEMA IF EXISTS " + quoteIdentifier(schemaName) + " CASCADE");
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private void assertV5Failure(SchemaWorkContext schema) {
        FlywayException exception = assertThrows(FlywayException.class, schema::migrateToFive);
        assertTrue(
                exception.getMessage().contains("V5__seed_initial_expense_master_data.sql"),
                () -> "Expected a V5 migration failure but got: " + exception.getMessage()
        );
    }

    @FunctionalInterface
    private interface SchemaWork {
        void run(SchemaWorkContext schema);
    }

    private final class SchemaWorkContext {

        private final String schemaName;
        private final JdbcTemplate jdbc;

        private SchemaWorkContext(String schemaName) {
            this.schemaName = schemaName;
            this.jdbc = new JdbcTemplate(dataSource);
        }

        private String table(String tableName) {
            return quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
        }

        private void migrateToFour() {
            flyway(MigrationVersion.fromVersion("4")).migrate();
        }

        private void migrateToFive() {
            flyway(MigrationVersion.fromVersion("5")).migrate();
        }

        private Flyway flyway(MigrationVersion target) {
            return Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas(schemaName)
                    .defaultSchema(schemaName)
                    .createSchemas(false)
                    .target(target)
                    .load();
        }

        private void insertExpenseType(String code, String name, String calculationType, boolean active) {
            jdbc.update(
                    "INSERT INTO " + table("expense_types")
                            + " (code, name, calculation_type, active) VALUES (?, ?, ?, ?)",
                    code,
                    name,
                    calculationType,
                    active
            );
        }

        private void insertPrice(
                String expenseTypeCode,
                String priceCode,
                String priceName,
                BigDecimal unitPrice,
                LocalDate effectiveFrom,
                LocalDate effectiveTo,
                boolean active
        ) {
            Long expenseTypeId = jdbc.queryForObject(
                    "SELECT id FROM " + table("expense_types") + " WHERE code = ?",
                    Long.class,
                    expenseTypeCode
            );
            jdbc.update(
                    "INSERT INTO " + table("expense_price_settings")
                            + " (expense_type_id, price_code, price_name, unit_price, "
                            + "effective_from, effective_to, active) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    expenseTypeId,
                    priceCode,
                    priceName,
                    unitPrice,
                    effectiveFrom,
                    effectiveTo,
                    active
            );
        }

        private int count(String tableName, String predicate) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table(tableName) + " WHERE " + predicate,
                    Integer.class
            );
        }

        private int countAll(String tableName) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table(tableName),
                    Integer.class
            );
        }

        private int successfulVersionFiveCount() {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table("flyway_schema_history")
                            + " WHERE version = '5' AND success = TRUE",
                    Integer.class
            );
        }
    }
}
