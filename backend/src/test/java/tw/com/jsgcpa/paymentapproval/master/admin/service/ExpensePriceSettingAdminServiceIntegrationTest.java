package tw.com.jsgcpa.paymentapproval.master.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CloseExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ExpensePriceSettingVersionRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ReplaceExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.audit.repository.MasterDataAuditLogRepository;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpensePriceSettingRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;

@SpringBootTest
@Transactional
class ExpensePriceSettingAdminServiceIntegrationTest {

    @Autowired private ExpensePriceSettingAdminService service;
    @Autowired private ExpenseTypeRepository expenseTypeRepository;
    @Autowired private ExpensePriceSettingRepository priceSettingRepository;
    @Autowired private MasterDataAuditLogRepository auditLogRepository;
    @Autowired private AppUserRepository appUserRepository;

    @Test
    void postgresLifecycleUsesHistoricalRowsAndAppendOnlyAudit() {
        AppUser actor = new AppUser();
        actor.setUsername("price-admin-" + UUID.randomUUID().toString().replace("-", ""));
        actor.setDisplayName("Price Admin Test");
        Long actorId = appUserRepository.saveAndFlush(actor).getId();

        ExpenseType type = new ExpenseType();
        type.setCode("PG_" + UUID.randomUUID().toString().replace("-", ""));
        type.setName("PostgreSQL price test");
        type.setCalculationType(CalculationType.MEAL);
        type.setActive(true);
        type = expenseTypeRepository.saveAndFlush(type);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        var created = service.create(
                type.getId(),
                new CreateExpensePriceSettingRequest(
                        " default ", " Initial price ", new BigDecimal("88.00"), today
                ),
                actorId
        );
        assertEquals("DEFAULT", created.priceCode());
        assertEquals("Initial price", created.priceName());
        assertTrue(created.active());
        assertEquals(0L, created.version());

        var replacement = service.replace(
                created.id(),
                new ReplaceExpensePriceSettingRequest(
                        "Updated price", new BigDecimal("99.00"), today.plusDays(10),
                        0L, "scheduled replacement"
                ),
                actorId
        );
        assertEquals(0L, replacement.version());
        assertEquals(today.plusDays(10), replacement.effectiveFrom());
        var historical = priceSettingRepository.findById(created.id()).orElseThrow();
        assertEquals(today.plusDays(9), historical.getEffectiveTo());
        assertEquals(1L, historical.getVersion());

        var closed = service.close(
                replacement.id(),
                new CloseExpensePriceSettingRequest(
                        today.plusDays(20), 0L, "close future period"
                ),
                actorId
        );
        assertEquals(today.plusDays(20), closed.effectiveTo());
        assertEquals(1L, closed.version());

        var effective = service.effective(type.getId(), " DEFAULT ", today);
        assertEquals(created.id(), effective.id());
        assertTrue(effective.effective());

        var inactive = service.deactivate(
                replacement.id(),
                new DeactivateExpensePriceSettingRequest(1L, " retire "),
                actorId
        );
        assertEquals(false, inactive.active());

        var audits = auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditTargetType
                        .EXPENSE_PRICE_SETTING,
                created.id()
        );
        assertEquals(2, audits.size());
        assertEquals("EXPENSE_PRICE_CREATE", audits.get(0).getAction().name());
        assertEquals("EXPENSE_PRICE_REPLACE", audits.get(1).getAction().name());
        assertTrue(audits.get(1).getAfterData().containsKey("active"));
        assertTrue(!audits.get(1).getAfterData().containsKey("version"));
    }
}
