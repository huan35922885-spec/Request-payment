package tw.com.jsgcpa.paymentapproval.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class PaymentOperatorRoleMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate publicJdbcTemplate;

    @Test
    void v10MergesPaymentOperatorIntoCashier() {
        String schemaName = "payment_operator_merge_"
                + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        try {
            publicJdbcTemplate.execute(
                    "CREATE SCHEMA " + quoteIdentifier(schemaName)
            );
            Flyway flyway = flyway(schemaName, MigrationVersion.fromVersion("9"));
            flyway.migrate();

            long operatorOnlyUserId = insertUser(jdbc, schemaName, "operator-only");
            long bothUserId = insertUser(jdbc, schemaName, "both-roles");
            insertRole(jdbc, schemaName, operatorOnlyUserId, "PAYMENT_OPERATOR");
            insertRole(jdbc, schemaName, bothUserId, "CASHIER");
            insertRole(jdbc, schemaName, bothUserId, "PAYMENT_OPERATOR");

            flyway(schemaName, MigrationVersion.fromVersion("10")).migrate();

            assertEquals(1, count(jdbc, schemaName, "flyway_schema_history",
                    "version = '10' AND success = TRUE"));
            assertEquals(0, count(jdbc, schemaName, "app_user_roles",
                    "role_code = 'PAYMENT_OPERATOR'"));
            assertEquals(1, count(jdbc, schemaName, "app_user_roles",
                    "user_id = " + operatorOnlyUserId + " AND role_code = 'CASHIER'"));
            assertEquals(1, count(jdbc, schemaName, "app_user_roles",
                    "user_id = " + bothUserId + " AND role_code = 'CASHIER'"));
            assertEquals(1, count(jdbc, schemaName, "app_user_roles",
                    "user_id = " + bothUserId));

            assertThrows(
                    DataAccessException.class,
                    () -> insertRole(jdbc, schemaName, operatorOnlyUserId, "PAYMENT_OPERATOR")
            );
        } finally {
            publicJdbcTemplate.execute(
                    "DROP SCHEMA IF EXISTS " + quoteIdentifier(schemaName) + " CASCADE"
            );
        }
    }

    private Flyway flyway(String schemaName, MigrationVersion target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .locations("classpath:db/migration")
                .createSchemas(false)
                .target(target)
                .load();
    }

    private long insertUser(JdbcTemplate jdbc, String schemaName, String suffix) {
        return jdbc.queryForObject(
                "INSERT INTO " + quoteIdentifier(schemaName) + ".app_users "
                        + "(username, display_name, active) VALUES (?, ?, TRUE) "
                        + "RETURNING id",
                Long.class,
                "user-" + suffix,
                "User " + suffix
        );
    }

    private void insertRole(
            JdbcTemplate jdbc,
            String schemaName,
            long userId,
            String roleCode
    ) {
        jdbc.update(
                "INSERT INTO " + quoteIdentifier(schemaName) + ".app_user_roles "
                        + "(user_id, role_code) VALUES (?, ?)",
                userId,
                roleCode
        );
    }

    private int count(
            JdbcTemplate jdbc,
            String schemaName,
            String table,
            String where
    ) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + quoteIdentifier(schemaName) + "."
                        + table + " WHERE " + where,
                Integer.class
        );
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
