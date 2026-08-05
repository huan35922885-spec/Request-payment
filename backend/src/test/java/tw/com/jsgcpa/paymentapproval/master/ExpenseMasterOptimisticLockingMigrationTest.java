package tw.com.jsgcpa.paymentapproval.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ExpenseMasterOptimisticLockingMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate publicJdbcTemplate;

    @Test
    void v7AddsVersionColumnsWithDefaultsAndPreservesMasterData() {
        String schemaName = "expense_lock_test_"
                + UUID.randomUUID().toString().replace("-", "");
        SchemaContext schema = new SchemaContext(schemaName);

        try {
            publicJdbcTemplate.execute("CREATE SCHEMA " + quoteIdentifier(schemaName));
            schema.migrateTo(MigrationVersion.fromVersion("6"));

            int expenseTypeCount = schema.countAll("expense_types");
            int priceSettingCount = schema.countAll("expense_price_settings");
            assertEquals(0, schema.columnCount("expense_types"));
            assertEquals(0, schema.columnCount("expense_price_settings"));

            schema.migrateTo(MigrationVersion.fromVersion("7"));

            schema.assertVersionColumn("expense_types");
            schema.assertVersionColumn("expense_price_settings");
            assertEquals(expenseTypeCount, schema.countAll("expense_types"));
            assertEquals(priceSettingCount, schema.countAll("expense_price_settings"));
            assertEquals(0, schema.count("expense_types", "version <> 0"));
            assertEquals(0, schema.count("expense_price_settings", "version <> 0"));

            schema.insertExpenseTypeAfterV7();
            assertEquals(
                    0,
                    schema.jdbc.queryForObject(
                            "SELECT version FROM " + schema.table("expense_types")
                                    + " WHERE code = 'AFTER_V7'",
                            Integer.class
                    )
            );

            assertEquals(
                    1,
                    schema.count(
                            "flyway_schema_history",
                            "version = '7' AND success = TRUE"
                    )
            );
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

        private void assertVersionColumn(String tableName) {
            Map<String, Object> column = publicJdbcTemplate.queryForMap(
                    "SELECT data_type, is_nullable, column_default "
                            + "FROM information_schema.columns "
                            + "WHERE table_schema = ? AND table_name = ? "
                            + "AND column_name = 'version'",
                    schemaName,
                    tableName
            );
            assertEquals("bigint", column.get("data_type"));
            assertEquals("NO", column.get("is_nullable"));
            assertTrue(String.valueOf(column.get("column_default")).contains("0"));
        }

        private void migrateTo(MigrationVersion target) {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas(schemaName)
                    .defaultSchema(schemaName)
                    .createSchemas(false)
                    .target(target)
                    .load()
                    .migrate();
        }

        private String table(String tableName) {
            return quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
        }

        private int countAll(String tableName) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table(tableName),
                    Integer.class
            );
        }

        private int count(String tableName, String predicate) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table(tableName) + " WHERE " + predicate,
                    Integer.class
            );
        }

        private int columnCount(String tableName) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_schema = ? AND table_name = ? "
                            + "AND column_name = 'version'",
                    Integer.class,
                    schemaName,
                    tableName
            );
        }

        private void insertExpenseTypeAfterV7() {
            Long expenseTypeId = jdbc.queryForObject(
                    "INSERT INTO " + table("expense_types")
                            + " (code, name, calculation_type, active) "
                            + "VALUES ('AFTER_V7', 'After V7', 'MANUAL', TRUE) RETURNING id",
                    Long.class
            );
            jdbc.update(
                    "INSERT INTO " + table("expense_price_settings")
                            + " (expense_type_id, price_code, price_name, unit_price, "
                            + "effective_from, active) VALUES (?, 'DEFAULT', 'After V7', ?, "
                            + "DATE '2026-08-05', TRUE)",
                    expenseTypeId,
                    new BigDecimal("1.00")
            );
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT version FROM " + table("expense_price_settings")
                                    + " WHERE expense_type_id = ?",
                            Integer.class,
                            expenseTypeId
                    )
            );
        }
    }
}
