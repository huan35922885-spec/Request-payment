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
class MasterDataAdminRoleMigrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate publicJdbcTemplate;

    @Test
    void v6ExpandsRoleConstraintWithoutAssigningUsers() {
        String schemaName = "master_role_test_"
                + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        try {
            publicJdbcTemplate.execute(
                    "CREATE SCHEMA " + quoteIdentifier(schemaName)
            );
            Flyway flyway = flyway(schemaName, MigrationVersion.fromVersion("5"));
            flyway.migrate();

            long cashierUserId = insertUser(jdbc, schemaName, "cashier");
            long paymentOperatorUserId = insertUser(jdbc, schemaName, "payment-operator");
            insertRole(jdbc, schemaName, cashierUserId, "CASHIER");
            insertRole(jdbc, schemaName, paymentOperatorUserId, "PAYMENT_OPERATOR");

            assertEquals(
                    0,
                    count(jdbc, schemaName, "app_user_roles", "role_code = 'MASTER_DATA_ADMIN'")
            );

            flyway(schemaName, MigrationVersion.fromVersion("6")).migrate();

            assertEquals(1, count(jdbc, schemaName, "flyway_schema_history",
                    "version = '6' AND success = TRUE"));
            assertEquals(1, count(jdbc, schemaName, "app_user_roles",
                    "user_id = " + cashierUserId + " AND role_code = 'CASHIER'"));
            assertEquals(1, count(jdbc, schemaName, "app_user_roles",
                    "user_id = " + paymentOperatorUserId
                            + " AND role_code = 'PAYMENT_OPERATOR'"));
            assertEquals(0, count(jdbc, schemaName, "app_user_roles",
                    "role_code = 'MASTER_DATA_ADMIN'"));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM information_schema.table_constraints "
                                    + "WHERE constraint_name = ? AND table_schema = ?",
                            Integer.class,
                            "chk_app_user_roles_role_code",
                            schemaName
                    )
            );

            long masterDataAdminUserId = insertUser(
                    jdbc,
                    schemaName,
                    "master-data-admin"
            );
            insertRole(jdbc, schemaName, masterDataAdminUserId, "MASTER_DATA_ADMIN");
            insertRole(jdbc, schemaName, masterDataAdminUserId, "CASHIER");

            assertEquals(2, count(jdbc, schemaName, "app_user_roles",
                    "user_id = " + masterDataAdminUserId));
            assertRoleRejected(jdbc, schemaName, masterDataAdminUserId, "UNKNOWN");
            assertRoleRejected(
                    jdbc,
                    schemaName,
                    masterDataAdminUserId,
                    "ROLE_MASTER_DATA_ADMIN"
            );
            assertDuplicateRoleRejected(
                    jdbc,
                    schemaName,
                    masterDataAdminUserId,
                    "MASTER_DATA_ADMIN"
            );
        } finally {
            publicJdbcTemplate.execute(
                    "DROP SCHEMA IF EXISTS " + quoteIdentifier(schemaName)
                            + " CASCADE"
            );
        }
    }

    private Flyway flyway(String schemaName, MigrationVersion target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(false)
                .target(target)
                .load();
    }

    private long insertUser(JdbcTemplate jdbc, String schema, String username) {
        return jdbc.queryForObject(
                "INSERT INTO " + quoteIdentifier(schema) + ".app_users "
                        + "(username, display_name, active) VALUES (?, ?, TRUE) "
                        + "RETURNING id",
                Long.class,
                username,
                username
        );
    }

    private void insertRole(
            JdbcTemplate jdbc,
            String schema,
            long userId,
            String roleCode
    ) {
        jdbc.update(
                "INSERT INTO " + quoteIdentifier(schema)
                        + ".app_user_roles (user_id, role_code) VALUES (?, ?)",
                userId,
                roleCode
        );
    }

    private void assertRoleRejected(
            JdbcTemplate jdbc,
            String schema,
            long userId,
            String roleCode
    ) {
        assertThrows(
                DataAccessException.class,
                () -> insertRole(jdbc, schema, userId, roleCode)
        );
    }

    private void assertDuplicateRoleRejected(
            JdbcTemplate jdbc,
            String schema,
            long userId,
            String roleCode
    ) {
        assertThrows(
                DataAccessException.class,
                () -> insertRole(jdbc, schema, userId, roleCode)
        );
    }

    private int count(
            JdbcTemplate jdbc,
            String schema,
            String table,
            String predicate
    ) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + quoteIdentifier(schema)
                        + "." + table + " WHERE " + predicate,
                Integer.class
        );
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
