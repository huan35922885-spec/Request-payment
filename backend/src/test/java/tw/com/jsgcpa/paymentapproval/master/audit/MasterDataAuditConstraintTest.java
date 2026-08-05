package tw.com.jsgcpa.paymentapproval.master.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
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
class MasterDataAuditConstraintTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate publicJdbcTemplate;

    @Test
    void acceptsSupportedActionsAndSnapshotShapes() {
        withV9Schema(schema -> {
            schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_CREATE",
                    "actor", "Actor", null, "{}", null, 0L, null
            );
            schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 2L, "EXPENSE_TYPE_RENAME",
                    "actor", "Actor", "{}", "{}", 0L, 1L, null
            );
            schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 3L, "EXPENSE_TYPE_ACTIVATE",
                    "actor", "Actor", "{}", "{}", 1L, 2L, null
            );
            schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 4L, "EXPENSE_TYPE_DEACTIVATE",
                    "actor", "Actor", "{}", "{}", 2L, 3L, "required reason"
            );
            schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_PRICE_SETTING", 5L, "EXPENSE_PRICE_CREATE",
                    "actor", "Actor", null, "{}", null, 0L, null
            );
            UUID operationId = UUID.randomUUID();
            schema.insertAudit(
                    operationId, "EXPENSE_PRICE_SETTING", 6L, "EXPENSE_PRICE_REPLACE",
                    "actor", "Actor", "{}", "{}", 4L, 5L, "replace reason"
            );
            schema.insertAudit(
                    operationId, "EXPENSE_PRICE_SETTING", 7L, "EXPENSE_PRICE_REPLACE",
                    "actor", "Actor", null, "{}", null, 0L, "new price reason"
            );
            assertEquals(2, schema.countByOperation(operationId));
        });
    }

    @Test
    void rejectsInvalidTargetActionAndSnapshotValues() {
        withV9Schema(schema -> {
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "WRONG", 1L, "EXPENSE_TYPE_CREATE",
                    "actor", "Actor", null, "{}", null, 0L, null
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_PRICE_SETTING", 1L, "EXPENSE_TYPE_CREATE",
                    "actor", "Actor", null, "{}", null, 0L, null
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 0L, "EXPENSE_TYPE_CREATE",
                    "actor", "Actor", null, "{}", null, 0L, null
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_CREATE",
                    "actor", "Actor", "{}", "{}", 0L, 0L, null
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_CREATE",
                    "actor", "Actor", null, "{}", null, 1L, null
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_RENAME",
                    "actor", "Actor", "{}", "{}", null, 1L, null
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_RENAME",
                    "actor", "Actor", "{}", "{}", 1L, 1L, null
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_PRICE_SETTING", 1L, "EXPENSE_PRICE_REPLACE",
                    "actor", "Actor", "{}", "{}", 1L, 1L, "reason"
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_PRICE_SETTING", 1L, "EXPENSE_PRICE_REPLACE",
                    "actor", "Actor", null, "{}", null, 1L, "reason"
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_DEACTIVATE",
                    "actor", "Actor", "{}", "{}", 1L, 2L, "   "
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_RENAME",
                    "actor", "Actor", "{}", "[]", 1L, 2L, null
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_RENAME",
                    "actor", "Actor", "[]", "{}", 1L, 2L, null
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_RENAME",
                    "actor", "Actor", "{}", "null", 1L, 2L, null
            ));
            schema.assertSqlState("23502", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_RENAME",
                    "actor", "Actor", "{}", null, 1L, 2L, null
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_RENAME",
                    "actor", "Actor", "{}", "{}", -1L, 0L, null
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_RENAME",
                    "actor", "Actor", "{}", "{}", 1L, 2L, "   "
            ));
        });
    }

    @Test
    void rejectsBlankActorSnapshotsAndUnknownActor() {
        withV9Schema(schema -> {
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_CREATE",
                    "   ", "Actor", null, "{}", null, 0L, null
            ));
            schema.assertSqlState("23514", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_CREATE",
                    "actor", "   ", null, "{}", null, 0L, null
            ));
            schema.assertSqlState("23503", () -> schema.insertAudit(
                    UUID.randomUUID(), "EXPENSE_TYPE", 1L, "EXPENSE_TYPE_CREATE",
                    "missing", "Actor", null, "{}", null, 0L, null
            ));
        });
    }

    private void withV9Schema(Consumer<SchemaContext> work) {
        String schemaName = "master_audit_constraint_"
                + UUID.randomUUID().toString().replace("-", "");
        SchemaContext schema = new SchemaContext(schemaName);
        try {
            publicJdbcTemplate.execute("CREATE SCHEMA " + quote(schemaName));
            schema.migrate();
            schema.insertActor();
            work.accept(schema);
        } finally {
            publicJdbcTemplate.execute("DROP SCHEMA IF EXISTS " + quote(schemaName) + " CASCADE");
        }
    }

    private String sqlState(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        throw new AssertionError("No SQLState found", failure);
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private final class SchemaContext {

        private final String schemaName;
        private final JdbcTemplate jdbc;

        private SchemaContext(String schemaName) {
            this.schemaName = schemaName;
            this.jdbc = new JdbcTemplate(dataSource);
        }

        private String table() {
            return quote(schemaName) + ".\"master_data_audit_logs\"";
        }

        private String appUsers() {
            return quote(schemaName) + ".\"app_users\"";
        }

        private void migrate() {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas(schemaName)
                    .defaultSchema(schemaName)
                    .createSchemas(false)
                    .target(MigrationVersion.fromVersion("9"))
                    .load()
                    .migrate();
        }

        private void insertActor() {
            jdbc.update(
                    "INSERT INTO " + appUsers()
                            + " (username, display_name, active) VALUES (?, ?, TRUE)",
                    "actor",
                    "Actor"
            );
        }

        private void insertAudit(
                UUID operationId,
                String targetType,
                long targetId,
                String action,
                String username,
                String displayName,
                String beforeData,
                String afterData,
                Long beforeVersion,
                Long afterVersion,
                String reason
        ) {
            jdbc.update(
                    "INSERT INTO " + table()
                            + " (operation_id, target_type, target_id, action, actor_id, "
                            + "actor_username_snapshot, actor_display_name_snapshot, before_data, "
                            + "after_data, before_version, after_version, reason) "
                            + "VALUES (?, ?, ?, ?, COALESCE((SELECT id FROM " + appUsers()
                            + " WHERE username = ?), 999999999), ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?)",
                    operationId,
                    targetType,
                    targetId,
                    action,
                    username,
                    username,
                    displayName,
                    beforeData,
                    afterData,
                    beforeVersion,
                    afterVersion,
                    reason
            );
        }

        private int countByOperation(UUID operationId) {
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table() + " WHERE operation_id = ?",
                    Integer.class,
                    operationId
            );
        }

        private void assertSqlState(String expected, Executable action) {
            DataAccessException failure = assertThrows(DataAccessException.class, action);
            assertEquals(expected, sqlState(failure));
        }
    }
}
