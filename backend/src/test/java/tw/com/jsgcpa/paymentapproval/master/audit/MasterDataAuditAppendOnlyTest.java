package tw.com.jsgcpa.paymentapproval.master.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.UUID;
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
class MasterDataAuditAppendOnlyTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate publicJdbcTemplate;

    @Test
    void allowsInsertButRejectsUpdateAndDeleteWithAppendOnlySqlState() {
        String schemaName = "master_audit_append_"
                + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String table = quote(schemaName) + ".\"master_data_audit_logs\"";
        String users = quote(schemaName) + ".\"app_users\"";

        try {
            publicJdbcTemplate.execute("CREATE SCHEMA " + quote(schemaName));
            migrate(jdbc, schemaName);
            jdbc.update("INSERT INTO " + users
                    + " (username, display_name, active) VALUES ('append_actor', 'Append Actor', TRUE)");
            jdbc.update(
                    "INSERT INTO " + table
                            + " (operation_id, target_type, target_id, action, actor_id, "
                            + "actor_username_snapshot, actor_display_name_snapshot, after_data, after_version) "
                            + "VALUES (?, 'EXPENSE_TYPE', 1, 'EXPENSE_TYPE_CREATE', "
                            + "(SELECT id FROM " + users + " WHERE username = 'append_actor'), "
                            + "'append_actor', 'Append Actor', '{}'::jsonb, 0)",
                    UUID.randomUUID()
            );

            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
            assertSqlState("55000", () -> jdbc.update(
                    "UPDATE " + table + " SET reason = 'changed'"
            ));
            assertSqlState("55000", () -> jdbc.update("DELETE FROM " + table));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        } finally {
            publicJdbcTemplate.execute("DROP SCHEMA IF EXISTS " + quote(schemaName) + " CASCADE");
        }
    }

    private void migrate(JdbcTemplate jdbc, String schemaName) {
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

    private void assertSqlState(String expected, Executable action) {
        DataAccessException failure = assertThrows(DataAccessException.class, action);
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                assertEquals(expected, sqlException.getSQLState());
                return;
            }
            current = current.getCause();
        }
        throw new AssertionError("No SQLState found", failure);
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
