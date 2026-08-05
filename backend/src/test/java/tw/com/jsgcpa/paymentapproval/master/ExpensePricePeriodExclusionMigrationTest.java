package tw.com.jsgcpa.paymentapproval.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class ExpensePricePeriodExclusionMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate publicJdbcTemplate;

    @Test
    void v8InstallsExtensionAndReplacesOpenEndedUniqueIndex() {
        String schemaName = "expense_period_migration_"
                + UUID.randomUUID().toString().replace("-", "");
        SchemaContext schema = new SchemaContext(schemaName);

        try {
            publicJdbcTemplate.execute("CREATE SCHEMA " + quoteIdentifier(schemaName));
            schema.migrateTo(MigrationVersion.fromVersion("7"));

            int expenseTypeCount = schema.countAll("expense_types");
            int priceSettingCount = schema.countAll("expense_price_settings");
            assertEquals(1, schema.indexCount("uq_expense_price_settings_current"));
            assertEquals(0, schema.constraintCount(
                    "excl_expense_price_settings_active_period"
            ));

            schema.migrateTo(MigrationVersion.fromVersion("8"));

            assertEquals(1, schema.successfulVersionEightCount());
            assertTrue(schema.btreeGistInstalled());
            assertEquals(1, schema.btreeGistPublicSchemaCount());
            assertEquals(0, schema.indexCount("uq_expense_price_settings_current"));
            assertEquals(1, schema.constraintCount(
                    "excl_expense_price_settings_active_period"
            ));

            Map<String, Object> constraint = schema.constraint();
            assertEquals("x", constraint.get("contype"));
            String definition = String.valueOf(constraint.get("definition"));
            assertTrue(definition.contains("EXCLUDE USING gist"));
            assertTrue(definition.contains("expense_type_id WITH ="));
            assertTrue(definition.contains("price_code WITH ="));
            assertTrue(definition.contains("daterange(effective_from, effective_to"));
            assertTrue(definition.contains("WITH &&"));
            assertEquals("(active = true)", constraint.get("predicate"));

            assertEquals(expenseTypeCount, schema.countAll("expense_types"));
            assertEquals(priceSettingCount, schema.countAll("expense_price_settings"));
            assertEquals(0, schema.count("expense_types", "version <> 0"));
            assertEquals(0, schema.count("expense_price_settings", "version <> 0"));
            schema.assertSeedPrice("MEAL", "DEFAULT", new BigDecimal("80.00"));
            schema.assertSeedPrice("CONFIRMATION", "NORMAL_MAIL", new BigDecimal("8.00"));
            schema.assertSeedPrice(
                    "CONFIRMATION",
                    "REGISTERED_MAIL",
                    new BigDecimal("28.00")
            );
            schema.assertSeedPrice(
                    "CONFIRMATION",
                    "EXPRESS_REGISTERED_MAIL",
                    new BigDecimal("35.00")
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

        private int successfulVersionEightCount() {
            return count("flyway_schema_history", "version = '8' AND success = TRUE");
        }

        private int indexCount(String indexName) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_class c "
                            + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                            + "WHERE n.nspname = ? AND c.relname = ? AND c.relkind = 'i'",
                    Integer.class,
                    schemaName,
                    indexName
            );
        }

        private int constraintCount(String constraintName) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint c "
                            + "JOIN pg_class r ON r.oid = c.conrelid "
                            + "JOIN pg_namespace n ON n.oid = r.relnamespace "
                            + "WHERE n.nspname = ? AND c.conname = ?",
                    Integer.class,
                    schemaName,
                    constraintName
            );
        }

        private Map<String, Object> constraint() {
            return jdbc.queryForMap(
                    "SELECT c.contype, pg_get_constraintdef(c.oid) AS definition, "
                            + "pg_get_expr(i.indpred, i.indrelid) AS predicate "
                            + "FROM pg_constraint c "
                            + "JOIN pg_class r ON r.oid = c.conrelid "
                            + "JOIN pg_namespace n ON n.oid = r.relnamespace "
                            + "JOIN pg_index i ON i.indexrelid = c.conindid "
                            + "WHERE n.nspname = ? "
                            + "AND c.conname = 'excl_expense_price_settings_active_period'",
                    schemaName
            );
        }

        private int btreeGistPublicSchemaCount() {
            return publicJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) "
                            + "FROM pg_extension e "
                            + "JOIN pg_namespace n ON n.oid = e.extnamespace "
                            + "WHERE e.extname = 'btree_gist' AND n.nspname = 'public'",
                    Integer.class
            );
        }

        private boolean btreeGistInstalled() {
            return publicJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_extension WHERE extname = 'btree_gist'",
                    Integer.class
            ) > 0;
        }

        private void assertSeedPrice(
                String expenseTypeCode,
                String priceCode,
                BigDecimal expectedUnitPrice
        ) {
            Map<String, Object> price = jdbc.queryForMap(
                    "SELECT eps.unit_price, eps.effective_from, eps.effective_to, "
                            + "eps.active, eps.version "
                            + "FROM " + table("expense_price_settings") + " eps "
                            + "JOIN " + table("expense_types") + " et "
                            + "ON et.id = eps.expense_type_id "
                            + "WHERE et.code = ? AND eps.price_code = ?",
                    expenseTypeCode,
                    priceCode
            );
            assertEquals(expectedUnitPrice, price.get("unit_price"));
            assertEquals(
                    LocalDate.of(2026, 8, 4),
                    ((java.sql.Date) price.get("effective_from")).toLocalDate()
            );
            assertEquals(null, price.get("effective_to"));
            assertEquals(true, price.get("active"));
            assertEquals(0L, ((Number) price.get("version")).longValue());
        }
    }
}
