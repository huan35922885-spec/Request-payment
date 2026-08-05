package tw.com.jsgcpa.paymentapproval.master.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditAction;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditTargetType;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditRecordCommand;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditService;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;

@SpringBootTest
class MasterDataAuditServiceTransactionTest {

    @Autowired
    private MasterDataAuditService service;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void recordRequiresAnExistingTransaction() {
        MasterDataAuditRecordCommand command = command(UUID.randomUUID(), 10L, 1L);
        assertThrows(IllegalTransactionStateException.class, () -> service.record(command));
    }

    @Test
    void recordsActorSnapshotAndJsonDataInsideOuterTransaction() {
        UUID operationId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            AppUser actor = createActor();
            MasterDataAuditRecordCommand command = new MasterDataAuditRecordCommand(
                    operationId,
                    MasterDataAuditTargetType.EXPENSE_TYPE,
                    20L,
                    MasterDataAuditAction.EXPENSE_TYPE_DEACTIVATE,
                    actor.getId(),
                    Map.of("active", true),
                    Map.of("active", false),
                    0L,
                    1L,
                    "  deactivated for testing  "
            );

            var audit = service.record(command);
            assertNotNull(audit);
            assertEquals("audit.transaction", audit.getActorUsernameSnapshot());
            assertEquals("Transaction Actor", audit.getActorDisplayNameSnapshot());
            assertEquals("deactivated for testing", audit.getReason());
            assertEquals(Map.of("active", false), audit.getAfterData());

            status.setRollbackOnly();
        });

        assertEquals(0, auditCount(operationId));
    }

    @Test
    void outerTransactionRollbackRollsBackAuditTogetherWithActorWork() {
        UUID operationId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            AppUser actor = createActor();
            service.record(command(operationId, 30L, actor.getId()));
            status.setRollbackOnly();
        });

        assertEquals(0, auditCount(operationId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_users WHERE username = 'audit.transaction'",
                Integer.class
        ));
    }

    @Test
    void missingActorFailsBeforeSavingAudit() {
        UUID operationId = UUID.randomUUID();
        DataAccessException ignored = null;
        try {
            transactionTemplate.executeWithoutResult(status -> service.record(
                    command(operationId, 40L, 999999999L)
            ));
        } catch (DataAccessException exception) {
            ignored = exception;
        } catch (IllegalArgumentException exception) {
            assertEquals("Audit actor not found: 999999999", exception.getMessage());
        }
        assertEquals(0, auditCount(operationId));
        assertEquals(null, ignored);
    }

    private AppUser createActor() {
        AppUser actor = new AppUser();
        actor.setUsername("audit.transaction");
        actor.setDisplayName("Transaction Actor");
        return appUserRepository.saveAndFlush(actor);
    }

    private MasterDataAuditRecordCommand command(UUID operationId, long targetId, long actorId) {
        return new MasterDataAuditRecordCommand(
                operationId,
                MasterDataAuditTargetType.EXPENSE_TYPE,
                targetId,
                MasterDataAuditAction.EXPENSE_TYPE_CREATE,
                actorId,
                null,
                Map.of("code", "AUDIT"),
                null,
                0L,
                null
        );
    }

    private int auditCount(UUID operationId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM master_data_audit_logs WHERE operation_id = ?",
                Integer.class,
                operationId
        );
    }
}
