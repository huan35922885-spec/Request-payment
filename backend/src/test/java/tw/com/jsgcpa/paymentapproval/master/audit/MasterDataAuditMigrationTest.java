package tw.com.jsgcpa.paymentapproval.master.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
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
class MasterDataAuditMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate publicJdbcTemplate;

    @Test
    void createsAppendOnlyMasterDataAuditSchemaAtVersionNine() {
        String schemaName = "master_audit_migration_"
                + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        try {
            publicJdbcTemplate.execute("CREATE SCHEMA " + quote(schemaName));
            migrate(jdbc, schemaName, "8");
            assertEquals(0, tableCount(jdbc, schemaName, "master_data_audit_logs"));

            migrate(jdbc, schemaName, "9");

            assertEquals(1, tableCount(jdbc, schemaName, "master_data_audit_logs"));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table(schemaName, "flyway_schema_history")
                            + " WHERE version = '9' AND success = TRUE",
                    Integer.class
            ));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table(schemaName, "master_data_audit_logs"),
                    Integer.class
            ));

            assertColumn(jdbc, schemaName, "operation_id", "uuid", "NO");
            assertColumn(jdbc, schemaName, "target_type", "character varying", "NO");
            assertColumn(jdbc, schemaName, "target_id", "bigint", "NO");
            assertColumn(jdbc, schemaName, "action", "character varying", "NO");
            assertColumn(jdbc, schemaName, "actor_id", "bigint", "NO");
            assertColumn(jdbc, schemaName, "before_data", "jsonb", "YES");
            assertColumn(jdbc, schemaName, "after_data", "jsonb", "NO");
            assertColumn(jdbc, schemaName, "before_version", "bigint", "YES");
            assertColumn(jdbc, schemaName, "after_version", "bigint", "NO");
            assertColumn(jdbc, schemaName, "created_at", "timestamp with time zone", "NO");

            assertEquals(1, constraintCount(jdbc, schemaName,
                    "fk_master_data_audit_logs_actor"));
            assertEquals(1, constraintCount(jdbc, schemaName,
                    "chk_master_data_audit_logs_target_type"));
            assertEquals(1, constraintCount(jdbc, schemaName,
                    "chk_master_data_audit_logs_action"));
            assertEquals(1, constraintCount(jdbc, schemaName,
                    "chk_master_data_audit_logs_action_target"));
            assertEquals(1, constraintCount(jdbc, schemaName,
                    "chk_master_data_audit_logs_snapshot_shape"));

            assertEquals(1, indexCount(jdbc, schemaName, "idx_master_data_audit_logs_target"));
            assertEquals(1, indexCount(jdbc, schemaName,
                    "idx_master_data_audit_logs_operation_id"));
            assertEquals(1, indexCount(jdbc, schemaName, "idx_master_data_audit_logs_actor"));
            assertEquals(1, triggerCount(jdbc, schemaName,
                    "trg_master_data_audit_logs_append_only"));
            assertEquals(1, functionCount(jdbc, schemaName,
                    "prevent_master_data_audit_log_mutation"));
            String triggerDefinition = triggerDefinition(jdbc, schemaName)
                    .toUpperCase(Locale.ROOT);
            assertTrue(triggerDefinition.contains("BEFORE DELETE"), triggerDefinition);
            assertTrue(triggerDefinition.contains("OR UPDATE"), triggerDefinition);
        } finally {
            publicJdbcTemplate.execute("DROP SCHEMA IF EXISTS " + quote(schemaName) + " CASCADE");
        }
    }

    private void migrate(JdbcTemplate jdbc, String schemaName, String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(false)
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private int count(JdbcTemplate jdbc, String schemaName, String tableName) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table(schemaName, tableName),
                Integer.class
        );
    }

    private int tableCount(JdbcTemplate jdbc, String schemaName, String tableName) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_name = ?",
                Integer.class,
                schemaName,
                tableName
        );
    }

    private void assertColumn(
            JdbcTemplate jdbc,
            String schemaName,
            String columnName,
            String dataType,
            String nullable
    ) {
        Map<String, Object> column = jdbc.queryForMap(
                "SELECT data_type, is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = 'master_data_audit_logs' "
                        + "AND column_name = ?",
                schemaName,
                columnName
        );
        assertEquals(dataType, column.get("data_type"));
        assertEquals(nullable, column.get("is_nullable"));
    }

    private int constraintCount(JdbcTemplate jdbc, String schemaName, String name) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint c "
                        + "JOIN pg_class r ON r.oid = c.conrelid "
                        + "JOIN pg_namespace n ON n.oid = r.relnamespace "
                        + "WHERE n.nspname = ? AND c.conname = ?",
                Integer.class,
                schemaName,
                name
        );
    }

    private int indexCount(JdbcTemplate jdbc, String schemaName, String name) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_class c "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = ? AND c.relname = ? AND c.relkind = 'i'",
                Integer.class,
                schemaName,
                name
        );
    }

    private int triggerCount(JdbcTemplate jdbc, String schemaName, String name) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_trigger t "
                        + "JOIN pg_class r ON r.oid = t.tgrelid "
                        + "JOIN pg_namespace n ON n.oid = r.relnamespace "
                        + "WHERE n.nspname = ? AND t.tgname = ? AND NOT t.tgisinternal",
                Integer.class,
                schemaName,
                name
        );
    }

    private int functionCount(JdbcTemplate jdbc, String schemaName, String name) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_proc p "
                        + "JOIN pg_namespace n ON n.oid = p.pronamespace "
                        + "WHERE n.nspname = ? AND p.proname = ?",
                Integer.class,
                schemaName,
                name
        );
    }

    private String triggerDefinition(JdbcTemplate jdbc, String schemaName) {
        return jdbc.queryForObject(
                "SELECT pg_get_triggerdef(t.oid) FROM pg_trigger t "
                        + "JOIN pg_class r ON r.oid = t.tgrelid "
                        + "JOIN pg_namespace n ON n.oid = r.relnamespace "
                        + "WHERE n.nspname = ? "
                        + "AND t.tgname = 'trg_master_data_audit_logs_append_only'",
                String.class,
                schemaName
        );
    }

    private String table(String schemaName, String tableName) {
        return quote(schemaName) + "." + quote(tableName);
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
