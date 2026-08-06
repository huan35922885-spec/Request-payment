package tw.com.jsgcpa.paymentapproval.master.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CloseExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ReplaceExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.exception.ExpensePriceSettingAdminBusinessException;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.response.ExpensePriceSettingAdminResponse;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditAction;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditTargetType;
import tw.com.jsgcpa.paymentapproval.master.audit.repository.MasterDataAuditLogRepository;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpensePriceSettingRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;

@SpringBootTest
class ExpensePriceSettingAdminServiceIntegrationTest {

    @Autowired private ExpensePriceSettingAdminService service;
    @Autowired private ExpenseTypeRepository expenseTypeRepository;
    @Autowired private ExpensePriceSettingRepository priceSettingRepository;
    @Autowired private MasterDataAuditLogRepository auditLogRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void postgresLifecycleUsesHistoricalRowsAndAudit() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        Long actorId = createActor();

        Long lifecycleTypeId = createExpenseType(
                "MEAL", "PostgreSQL lifecycle test", true
        );
        var created = inTransaction(() -> service.create(
                lifecycleTypeId,
                new CreateExpensePriceSettingRequest(
                        " default ", " Initial price ", new BigDecimal("88.00"), today
                ),
                actorId
        ));
        assertEquals("DEFAULT", created.priceCode());
        assertEquals("Initial price", created.priceName());
        assertTrue(created.active());
        assertEquals(0L, created.version());

        var replacement = inTransaction(() -> service.replace(
                created.id(),
                new ReplaceExpensePriceSettingRequest(
                        "Updated price", new BigDecimal("99.00"), today.plusDays(10),
                        0L, "scheduled replacement"
                ),
                actorId
        ));
        assertEquals(0L, replacement.version());
        assertEquals(today.plusDays(10), replacement.effectiveFrom());
        var historical = inTransaction(
                () -> priceSettingRepository.findById(created.id()).orElseThrow()
        );
        assertEquals(today.plusDays(9), historical.getEffectiveTo());
        assertEquals(1L, historical.getVersion());

        var closed = inTransaction(() -> service.close(
                replacement.id(),
                new CloseExpensePriceSettingRequest(
                        today.plusDays(20), 0L, "close future period"
                ),
                actorId
        ));
        assertEquals(today.plusDays(20), closed.effectiveTo());
        assertEquals(1L, closed.version());
        var effective = inTransaction(
                () -> service.effective(lifecycleTypeId, " DEFAULT ", today)
        );
        assertEquals(created.id(), effective.id());
        assertTrue(effective.effective());

        var lifecycleAudits = inTransaction(() -> auditLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                        MasterDataAuditTargetType.EXPENSE_PRICE_SETTING, created.id()
                ));
        assertEquals(2, lifecycleAudits.size());
        assertEquals("EXPENSE_PRICE_CREATE", lifecycleAudits.get(0).getAction().name());
        assertEquals("EXPENSE_PRICE_REPLACE", lifecycleAudits.get(1).getAction().name());
        assertTrue(!lifecycleAudits.get(1).getAfterData().containsKey("version"));
    }

    @Test
    void postgresMultiPriceDeactivationPreservesLastCurrentPrice() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        Long actorId = createActor();
        Long confirmationTypeId = createExpenseType(
                "CONFIRMATION", "PostgreSQL multi-price test", true
        );
        var normal = inTransaction(() -> service.create(
                confirmationTypeId,
                new CreateExpensePriceSettingRequest(
                        " NORMAL_MAIL ", "Normal mail", new BigDecimal("10.00"), today
                ),
                actorId
        ));
        var registered = inTransaction(() -> service.create(
                confirmationTypeId,
                new CreateExpensePriceSettingRequest(
                        " REGISTERED_MAIL ", "Registered mail", new BigDecimal("20.00"), today
                ),
                actorId
        ));
        var deactivatedNormal = inTransaction(() -> service.deactivate(
                normal.id(), new DeactivateExpensePriceSettingRequest(
                        normal.version(), " retire normal "
                ), actorId
        ));
        assertEquals(1L, deactivatedNormal.version());
        assertEquals(false, deactivatedNormal.active());
        assertEquals(registered.id(), inTransaction(() -> service.effective(
                confirmationTypeId, "REGISTERED_MAIL", today
        )).id());
        var registeredBefore = inTransaction(
                () -> priceSettingRepository.findById(registered.id()).orElseThrow()
        );
        int registeredAuditCountBefore = inTransaction(() -> auditLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                        MasterDataAuditTargetType.EXPENSE_PRICE_SETTING, registered.id()
                )).size();
        assertTrue(inTransaction(() -> auditLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                        MasterDataAuditTargetType.EXPENSE_PRICE_SETTING, normal.id()
                )).stream().anyMatch(audit -> "retire normal".equals(audit.getReason())));

        ExpensePriceSettingAdminBusinessException required = assertThrows(
                ExpensePriceSettingAdminBusinessException.class,
                () -> inTransaction(() -> service.deactivate(
                        registered.id(),
                        new DeactivateExpensePriceSettingRequest(
                                registeredBefore.getVersion(), " retire registered "
                        ),
                        actorId
                ))
        );
        assertEquals("EXPENSE_PRICE_CURRENT_REQUIRED", required.getCode());

        var registeredAfter = inTransaction(
                () -> priceSettingRepository.findById(registered.id()).orElseThrow()
        );
        assertTrue(registeredAfter.getActive());
        assertEquals(registeredBefore.getVersion(), registeredAfter.getVersion());
        assertEquals(registeredAuditCountBefore, inTransaction(() -> auditLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                        MasterDataAuditTargetType.EXPENSE_PRICE_SETTING, registered.id()
                )).size());
        var confirmationType = inTransaction(
                () -> expenseTypeRepository.findById(confirmationTypeId).orElseThrow()
        );
        assertTrue(confirmationType.getActive());
    }

    @Test
    void concurrentReplaceAllowsOneCompleteWinner() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        Long actorId = createActor();
        Long concurrentTypeId = createExpenseType(
                "MEAL", "PostgreSQL concurrent replace test", true
        );
        var concurrentOriginal = inTransaction(() -> service.create(
                concurrentTypeId,
                new CreateExpensePriceSettingRequest(
                        "DEFAULT", "Concurrent original", new BigDecimal("30.00"), today
                ),
                actorId
        ));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Object> replace = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    return inTransaction(() -> service.replace(
                            concurrentOriginal.id(),
                            new ReplaceExpensePriceSettingRequest(
                                    Thread.currentThread().getName(),
                                    new BigDecimal("31.00"), today.plusDays(5),
                                    concurrentOriginal.version(), "concurrent replace"
                            ),
                            actorId
                    ));
                } catch (ExpensePriceSettingAdminBusinessException exception) {
                    return exception;
                }
            };
            Future<Object> first = executor.submit(replace);
            Future<Object> second = executor.submit(replace);
            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);
            List<Object> results = List.of(firstResult, secondResult);
            assertEquals(1, results.stream()
                    .filter(result -> result instanceof ExpensePriceSettingAdminBusinessException
                            && "EXPENSE_PRICE_SETTING_VERSION_CONFLICT".equals(
                                    ((ExpensePriceSettingAdminBusinessException) result).getCode()))
                    .count());
            assertEquals(1, results.stream()
                    .filter(result -> result instanceof ExpensePriceSettingAdminResponse)
                    .count());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        var concurrentRows = inTransaction(() -> priceSettingRepository
                .findByExpenseType_IdOrderByEffectiveFromDescIdDesc(concurrentTypeId));
        assertEquals(2, concurrentRows.size());
        assertEquals(1, concurrentRows.stream()
                .filter(row -> row.getEffectiveTo() != null).count());
        assertEquals(1, concurrentRows.stream()
                .filter(row -> row.getId().equals(concurrentOriginal.id())
                        && row.getEffectiveTo() != null)
                .count());
        assertEquals(1, concurrentRows.stream()
                .filter(row -> !row.getId().equals(concurrentOriginal.id())
                        && row.getActive())
                .count());
        var concurrentAudits = inTransaction(() -> auditLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                        MasterDataAuditTargetType.EXPENSE_PRICE_SETTING,
                        concurrentOriginal.id()
                ));
        assertEquals(2, concurrentAudits.size());
        var replaceAudit = concurrentAudits.stream()
                .filter(audit -> audit.getAction() == MasterDataAuditAction.EXPENSE_PRICE_REPLACE)
                .findFirst()
                .orElseThrow();
        var operationAudits = inTransaction(() -> auditLogRepository
                .findByOperationIdOrderByIdAsc(replaceAudit.getOperationId()));
        assertEquals(2, operationAudits.size());
        assertTrue(operationAudits.stream()
                .allMatch(audit -> audit.getAction() == MasterDataAuditAction.EXPENSE_PRICE_REPLACE));
        assertEquals(operationAudits.get(0).getOperationId(),
                operationAudits.get(1).getOperationId());
    }

    private Long createActor() {
        return inTransaction(() -> {
            AppUser actor = new AppUser();
            actor.setUsername("price-admin-" + UUID.randomUUID().toString().replace("-", ""));
            actor.setDisplayName("Price Admin Test");
            return appUserRepository.saveAndFlush(actor).getId();
        });
    }

    private Long createExpenseType(
            String calculationTypeCode, String name, boolean active
    ) {
        return inTransaction(() -> {
            ExpenseType type = new ExpenseType();
            type.setCode("PG_" + UUID.randomUUID().toString().replace("-", ""));
            type.setName(name);
            type.setCalculationType(CalculationType.valueOf(calculationTypeCode));
            type.setActive(active);
            return expenseTypeRepository.saveAndFlush(type).getId();
        });
    }

    private <T> T inTransaction(Supplier<T> operation) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> operation.get());
    }
}
