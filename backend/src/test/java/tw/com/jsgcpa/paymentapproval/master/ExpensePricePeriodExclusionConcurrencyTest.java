package tw.com.jsgcpa.paymentapproval.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ExpensePricePeriodExclusionConcurrencyTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 9, 30);
    private static final long COORDINATION_TIMEOUT_SECONDS = 10;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate publicJdbcTemplate;

    @Test
    void concurrentOverlappingInsertsCannotBothCommit() throws Exception {
        String schemaName = "expense_period_concurrency_"
                + UUID.randomUUID().toString().replace("-", "");
        String schema = quoteIdentifier(schemaName);
        boolean extensionExistedBefore = btreeGistInstalled();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstInsertCompleted = new CountDownLatch(1);
        CountDownLatch secondInsertStarted = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);

        try {
            publicJdbcTemplate.execute("CREATE SCHEMA " + schema);
            migrateToV8(schemaName);
            long expenseTypeId = insertExpenseType(schema);

            Future<InsertOutcome> first = executor.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    try {
                        configureConnection(connection, "expense-period-first");
                        insertPrice(connection, schema, expenseTypeId);
                        firstInsertCompleted.countDown();
                        if (!allowFirstCommit.await(
                                COORDINATION_TIMEOUT_SECONDS,
                                TimeUnit.SECONDS
                        )) {
                            throw new TimeoutException(
                                    "Timed out waiting to commit first insert"
                            );
                        }
                        connection.commit();
                        return InsertOutcome.success();
                    } catch (Exception exception) {
                        rollback(connection, exception);
                        return InsertOutcome.failure(exception);
                    }
                }
            });

            Future<InsertOutcome> second = executor.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    try {
                        configureConnection(connection, "expense-period-second");
                        if (!firstInsertCompleted.await(
                                COORDINATION_TIMEOUT_SECONDS,
                                TimeUnit.SECONDS
                        )) {
                            throw new TimeoutException("Timed out waiting for first insert");
                        }
                        secondInsertStarted.countDown();
                        insertPrice(connection, schema, expenseTypeId);
                        connection.commit();
                        return InsertOutcome.success();
                    } catch (Exception exception) {
                        rollback(connection, exception);
                        return InsertOutcome.failure(exception);
                    }
                }
            });

            assertTrue(
                    firstInsertCompleted.await(
                            COORDINATION_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    ),
                    "First transaction did not insert"
            );
            assertTrue(
                    secondInsertStarted.await(
                            COORDINATION_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    ),
                    "Second transaction did not start"
            );
            assertTrue(
                    awaitSecondTransactionBlocked("expense-period-second"),
                    "Second transaction was not observed waiting on the exclusion constraint"
            );

            allowFirstCommit.countDown();

            InsertOutcome firstOutcome = first.get(
                    COORDINATION_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            InsertOutcome secondOutcome = second.get(
                    COORDINATION_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            assertTrue(firstOutcome.committed, firstOutcome.failureMessage());
            assertEquals("23P01", secondOutcome.sqlState(), secondOutcome.failureMessage());
            assertEquals(1, countPrices(schema, expenseTypeId));
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();
            executor.awaitTermination(COORDINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            publicJdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            if (!extensionExistedBefore && btreeGistInstalled()) {
                publicJdbcTemplate.execute("DROP EXTENSION IF EXISTS btree_gist");
            }
        }
    }

    private void configureConnection(Connection connection, String applicationName)
            throws SQLException {
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            statement.execute("SET application_name = '" + applicationName + "'");
            statement.execute("SET statement_timeout = '10000ms'");
        }
    }

    private void insertPrice(Connection connection, String schema, long expenseTypeId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + schema + ".\"expense_price_settings\" "
                        + "(expense_type_id, price_code, price_name, unit_price, "
                        + "effective_from, effective_to, active) VALUES (?, 'CONCURRENT', "
                        + "'Concurrent price', 10.00, ?, ?, TRUE)"
        )) {
            statement.setLong(1, expenseTypeId);
            statement.setObject(2, PERIOD_START);
            statement.setObject(3, PERIOD_END);
            statement.executeUpdate();
        }
    }

    private boolean awaitSecondTransactionBlocked(String applicationName) {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(COORDINATION_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            Integer blockedCount = publicJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) "
                            + "FROM pg_stat_activity a "
                            + "WHERE a.application_name = ? "
                            + "AND a.state = 'active' "
                            + "AND cardinality(pg_blocking_pids(a.pid)) > 0",
                    Integer.class,
                    applicationName
            );
            if (blockedCount != null && blockedCount > 0) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        return false;
    }

    private long insertExpenseType(String schema) {
        return publicJdbcTemplate.queryForObject(
                "INSERT INTO " + schema + ".\"expense_types\" "
                        + "(code, name, calculation_type, active) "
                        + "VALUES ('CONCURRENT_TYPE', 'Concurrent type', 'MANUAL', TRUE) "
                        + "RETURNING id",
                Long.class
        );
    }

    private int countPrices(String schema, long expenseTypeId) {
            return publicJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".\"expense_price_settings\" "
                        + "WHERE expense_type_id = ?",
                Integer.class,
                expenseTypeId
        );
    }

    private void migrateToV8(String schemaName) {
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

    private boolean btreeGistInstalled() {
        return publicJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'btree_gist'",
                Integer.class
        ) > 0;
    }

    private void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record InsertOutcome(boolean committed, Throwable failure) {

        private static InsertOutcome success() {
            return new InsertOutcome(true, null);
        }

        private static InsertOutcome failure(Throwable failure) {
            return new InsertOutcome(false, failure);
        }

        private String sqlState() {
            Throwable current = failure;
            while (current != null) {
                if (current instanceof SQLException sqlException) {
                    return sqlException.getSQLState();
                }
                current = current.getCause();
            }
            return null;
        }

        private String failureMessage() {
            return failure == null ? "" : failure.toString();
        }
    }
}
