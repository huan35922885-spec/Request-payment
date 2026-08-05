package tw.com.jsgcpa.paymentapproval.master.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ExpenseTypeVersionRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.RenameExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.response.ExpenseTypeAdminResponse;
import tw.com.jsgcpa.paymentapproval.master.admin.exception.ExpenseTypeAdminBusinessException;
import tw.com.jsgcpa.paymentapproval.master.audit.entity.MasterDataAuditLog;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditAction;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditTargetType;
import tw.com.jsgcpa.paymentapproval.master.audit.repository.MasterDataAuditLogRepository;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpensePriceSettingRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;

@SpringBootTest
class ExpenseTypeAdminServiceIntegrationTest {

    @Autowired
    private ExpenseTypeAdminService service;

    @Autowired
    private ExpenseTypeRepository expenseTypeRepository;

    @Autowired
    private ExpensePriceSettingRepository priceSettingRepository;

    @Autowired
    private MasterDataAuditLogRepository auditLogRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void createPersistsInactiveVersionZeroAndAuditSnapshot() {
        String code = code();
        ExpenseTypeAdminResponse response = inRollback(() -> {
            AppUser actor = actor();
            ExpenseTypeAdminResponse created = service.create(
                    new CreateExpenseTypeRequest(
                            " " + code.toLowerCase() + " ",
                            " Travel Claim ",
                            CalculationType.TRAVEL
                    ),
                    actor.getId()
            );

            assertFalse(created.active());
            assertEquals(0L, created.version());
            ExpenseType persisted = expenseTypeRepository.findById(created.id())
                    .orElseThrow();
            assertFalse(persisted.getActive());
            assertEquals(0L, persisted.getVersion());

            MasterDataAuditLog audit = auditLog(created.id()).get(0);
            assertEquals(MasterDataAuditAction.EXPENSE_TYPE_CREATE, audit.getAction());
            assertEquals(actor.getUsername(), audit.getActorUsernameSnapshot());
            assertEquals(code, audit.getAfterData().get("code"));
            assertEquals("TRAVEL", audit.getAfterData().get("calculationType"));
            assertEquals(false, audit.getAfterData().get("active"));
            assertEquals(0L, audit.getAfterVersion());
            assertEquals(null, audit.getBeforeData());
            return created;
        });

        assertNotNull(response.id());
        assertFalse(expenseTypeRepository.existsByCode(code));
    }

    @Test
    void renameAndDeactivatePersistVersionedChangesAndAudits() {
        inRollback(() -> {
            AppUser actor = actor();
            ExpenseTypeAdminResponse created = service.create(
                    new CreateExpenseTypeRequest(code(), "Original", CalculationType.MANUAL),
                    actor.getId()
            );

            ExpenseTypeAdminResponse renamed = service.rename(
                    created.id(),
                    new RenameExpenseTypeRequest(" Renamed ", 0L),
                    actor.getId()
            );
            assertEquals("Renamed", renamed.name());
            assertEquals(1L, renamed.version());

            ExpenseTypeAdminResponse activated = service.activate(
                    created.id(),
                    new ExpenseTypeVersionRequest(1L),
                    actor.getId()
            );
            assertTrue(activated.active());
            assertEquals(2L, activated.version());

            ExpenseTypeAdminResponse deactivated = service.deactivate(
                    created.id(),
                    new DeactivateExpenseTypeRequest(" Retired for testing ", 2L),
                    actor.getId()
            );
            assertFalse(deactivated.active());
            assertEquals(3L, deactivated.version());

            var audits = auditLog(created.id());
            assertEquals(4, audits.size());
            assertEquals(MasterDataAuditAction.EXPENSE_TYPE_RENAME, audits.get(1).getAction());
            assertEquals(0L, audits.get(1).getBeforeVersion());
            assertEquals(1L, audits.get(1).getAfterVersion());
            assertEquals("Original", audits.get(1).getBeforeData().get("name"));
            assertEquals("Renamed", audits.get(1).getAfterData().get("name"));
            assertEquals(MasterDataAuditAction.EXPENSE_TYPE_ACTIVATE, audits.get(2).getAction());
            assertEquals(MasterDataAuditAction.EXPENSE_TYPE_DEACTIVATE, audits.get(3).getAction());
            assertEquals("Retired for testing", audits.get(3).getReason());
            assertEquals(2L, audits.get(3).getBeforeVersion());
            assertEquals(3L, audits.get(3).getAfterVersion());
            return null;
        });
    }

    @Test
    void pricedActivationRequiresCurrentPriceAndThenSucceeds() {
        inRollback(() -> {
            AppUser actor = actor();
            ExpenseTypeAdminResponse created = service.create(
                    new CreateExpenseTypeRequest(code(), "Meal", CalculationType.MEAL),
                    actor.getId()
            );

            ExpenseTypeAdminBusinessException missingPrice = assertThrows(
                    ExpenseTypeAdminBusinessException.class,
                    () -> service.activate(
                            created.id(),
                            new ExpenseTypeVersionRequest(0L),
                            actor.getId()
                    )
            );
            assertEquals("EXPENSE_TYPE_CURRENT_PRICE_REQUIRED", missingPrice.getCode());
            assertEquals(1, auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                    MasterDataAuditTargetType.EXPENSE_TYPE,
                    created.id()
            ).size());

            ExpenseType type = expenseTypeRepository.findById(created.id()).orElseThrow();
            ExpensePriceSetting price = new ExpensePriceSetting();
            price.setExpenseType(type);
            price.setPriceCode("DEFAULT");
            price.setPriceName("Current");
            price.setUnitPrice(new BigDecimal("100.00"));
            price.setEffectiveFrom(LocalDate.now());
            price.setEffectiveTo(null);
            price.setActive(true);
            priceSettingRepository.saveAndFlush(price);

            ExpenseTypeAdminResponse activated = service.activate(
                    created.id(),
                    new ExpenseTypeVersionRequest(0L),
                    actor.getId()
            );
            assertTrue(activated.active());
            assertEquals(1L, activated.version());
            assertEquals(MasterDataAuditAction.EXPENSE_TYPE_ACTIVATE,
                    auditLog(created.id()).get(1).getAction());
            return null;
        });
    }

    @Test
    void manualActivationDoesNotRequirePriceAndStaleVersionDoesNotAudit() {
        inRollback(() -> {
            AppUser actor = actor();
            ExpenseTypeAdminResponse created = service.create(
                    new CreateExpenseTypeRequest(code(), "Manual", CalculationType.MANUAL),
                    actor.getId()
            );
            ExpenseTypeAdminResponse activated = service.activate(
                    created.id(),
                    new ExpenseTypeVersionRequest(0L),
                    actor.getId()
            );
            assertTrue(activated.active());
            assertEquals(1L, activated.version());

            ExpenseTypeAdminBusinessException conflict = assertThrows(
                    ExpenseTypeAdminBusinessException.class,
                    () -> service.rename(
                            created.id(),
                            new RenameExpenseTypeRequest("Stale", 0L),
                            actor.getId()
                    )
            );
            assertEquals("EXPENSE_TYPE_VERSION_CONFLICT", conflict.getCode());
            assertEquals(2, auditLog(created.id()).size());
            return null;
        });
    }

    @Test
    void missingAuditActorRollsBackExpenseTypeMutation() {
        String code = code();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        assertThrows(
                IllegalArgumentException.class,
                () -> template.execute(status -> {
                    ExpenseTypeAdminBusinessException ignored = null;
                    try {
                        service.create(
                                new CreateExpenseTypeRequest(code, "Rollback", CalculationType.MANUAL),
                                999999999L
                        );
                    } catch (IllegalArgumentException exception) {
                        status.setRollbackOnly();
                        throw exception;
                    }
                    return ignored;
                })
        );

        assertFalse(expenseTypeRepository.existsByCode(code));
    }

    private <T> T inRollback(Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            T result = work.get();
            status.setRollbackOnly();
            return result;
        });
    }

    private AppUser actor() {
        AppUser actor = new AppUser();
        String suffix = UUID.randomUUID().toString();
        actor.setUsername("expense-admin-" + suffix);
        actor.setDisplayName("Expense Admin");
        actor.setEmail("expense-admin-" + suffix + "@example.test");
        actor.setActive(true);
        return appUserRepository.saveAndFlush(actor);
    }

    private String code() {
        return "E2E_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private java.util.List<MasterDataAuditLog> auditLog(Long targetId) {
        return auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                MasterDataAuditTargetType.EXPENSE_TYPE,
                targetId
        );
    }
}
