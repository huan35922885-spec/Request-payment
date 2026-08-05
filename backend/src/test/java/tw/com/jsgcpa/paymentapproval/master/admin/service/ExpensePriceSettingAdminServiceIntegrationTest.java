package tw.com.jsgcpa.paymentapproval.master.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ExpensePriceSettingVersionRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.exception.ExpenseTypeAdminBusinessException;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;

@SpringBootTest
@Transactional
class ExpensePriceSettingAdminServiceIntegrationTest {

    @Autowired
    private ExpensePriceSettingAdminService service;

    @Autowired
    private ExpenseTypeRepository expenseTypeRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void postgresCreateActivateEffectiveAndLastCurrentDeactivateGuard() {
        AppUser actor = new AppUser();
        actor.setUsername("price-admin-" + UUID.randomUUID().toString().replace("-", ""));
        actor.setDisplayName("Price Admin Test");
        Long actorId = appUserRepository.saveAndFlush(actor).getId();
        ExpenseType type = new ExpenseType();
        type.setCode("PG_" + UUID.randomUUID().toString().replace("-", ""));
        type.setName("PostgreSQL price test");
        type.setCalculationType(CalculationType.MEAL);
        type.setActive(false);
        type = expenseTypeRepository.saveAndFlush(type);

        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Taipei"));
        var created = service.create(
                type.getId(),
                new CreateExpensePriceSettingRequest(
                        " default ", new BigDecimal("88.00"), today, today
                ),
                actorId
        );
        assertEquals("DEFAULT", created.priceCode());
        assertEquals(0L, created.version());

        var activated = service.activate(
                created.id(), new ExpensePriceSettingVersionRequest(0L), actorId
        );
        assertEquals(true, activated.active());
        assertEquals(1L, activated.version());

        var effective = service.effective(type.getId(), today);
        assertEquals(created.id(), effective.id());
        assertEquals(true, effective.effective());

        ExpenseTypeAdminBusinessException exception = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.deactivate(
                        created.id(),
                        new DeactivateExpensePriceSettingRequest(1L, "test"),
                        actorId
                )
        );
        assertEquals("EXPENSE_PRICE_CURRENT_REQUIRED", exception.getCode());
    }
}
