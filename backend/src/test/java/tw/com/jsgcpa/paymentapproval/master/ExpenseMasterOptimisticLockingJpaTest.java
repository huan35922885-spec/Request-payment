package tw.com.jsgcpa.paymentapproval.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;
import jakarta.persistence.Column;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpensePriceSettingRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;

@SpringBootTest
class ExpenseMasterOptimisticLockingJpaTest {

    @Autowired
    private ExpenseTypeRepository expenseTypeRepository;

    @Autowired
    private ExpensePriceSettingRepository expensePriceSettingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long expenseTypeId;
    private Long priceSettingId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    @AfterEach
    void cleanUp() {
        inNewTransaction(() -> {
            if (priceSettingId != null) {
                expensePriceSettingRepository.deleteById(priceSettingId);
                expensePriceSettingRepository.flush();
            }
            if (expenseTypeId != null) {
                expenseTypeRepository.deleteById(expenseTypeId);
                expenseTypeRepository.flush();
            }
            return null;
        });
    }

    @Test
    void mapsVersionAsManagedLongWithoutSetter() throws Exception {
        assertVersionMapping(ExpenseType.class);
        assertVersionMapping(ExpensePriceSetting.class);
    }

    @Test
    void expenseTypeVersionStartsAtZeroAndIncrementsOnUpdate() {
        ExpenseType created = createExpenseType();
        assertEquals(0L, created.getVersion());

        ExpenseType loaded = loadExpenseType();
        loaded.setName("Updated " + loaded.getCode());
        ExpenseType updated = inNewTransaction(
                () -> expenseTypeRepository.saveAndFlush(loaded)
        );

        assertEquals(1L, updated.getVersion());
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "SELECT version FROM expense_types WHERE id = ?",
                        Long.class,
                        expenseTypeId
                )
        );
    }

    @Test
    void staleExpenseTypeUpdateIsRejected() {
        createExpenseType();
        ExpenseType first = loadExpenseType();
        ExpenseType second = loadExpenseType();

        first.setName("First update");
        inNewTransaction(() -> expenseTypeRepository.saveAndFlush(first));
        second.setName("Stale update");

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> inNewTransaction(() -> expenseTypeRepository.saveAndFlush(second))
        );
        assertOptimisticLockFailure(failure);
        assertEquals(
                "First update",
                jdbcTemplate.queryForObject(
                        "SELECT name FROM expense_types WHERE id = ?",
                        String.class,
                        expenseTypeId
                )
        );
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "SELECT version FROM expense_types WHERE id = ?",
                        Long.class,
                        expenseTypeId
                )
        );
    }

    @Test
    void expensePriceSettingVersionStartsAtZeroAndIncrementsOnUpdate() {
        ExpensePriceSetting created = createPriceSetting();
        assertEquals(0L, created.getVersion());

        ExpensePriceSetting loaded = loadPriceSetting();
        loaded.setPriceName("Updated price");
        ExpensePriceSetting updated = inNewTransaction(
                () -> expensePriceSettingRepository.saveAndFlush(loaded)
        );

        assertEquals(1L, updated.getVersion());
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "SELECT version FROM expense_price_settings WHERE id = ?",
                        Long.class,
                        priceSettingId
                )
        );
    }

    @Test
    void staleExpensePriceSettingUpdateIsRejected() {
        createPriceSetting();
        ExpensePriceSetting first = loadPriceSetting();
        ExpensePriceSetting second = loadPriceSetting();

        first.setPriceName("First price update");
        inNewTransaction(() -> expensePriceSettingRepository.saveAndFlush(first));
        second.setPriceName("Stale price update");

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> inNewTransaction(() -> expensePriceSettingRepository.saveAndFlush(second))
        );
        assertOptimisticLockFailure(failure);
        assertEquals(
                "First price update",
                jdbcTemplate.queryForObject(
                        "SELECT price_name FROM expense_price_settings WHERE id = ?",
                        String.class,
                        priceSettingId
                )
        );
        assertEquals(
                1L,
                jdbcTemplate.queryForObject(
                        "SELECT version FROM expense_price_settings WHERE id = ?",
                        Long.class,
                        priceSettingId
                )
        );
    }

    private ExpenseType createExpenseType() {
        ExpenseType created = inNewTransaction(() -> {
            ExpenseType expenseType = new ExpenseType();
            expenseType.setCode("LOCK_" + UUID.randomUUID().toString().replace("-", ""));
            expenseType.setName("Lock test type");
            expenseType.setCalculationType(CalculationType.MANUAL);
            return expenseTypeRepository.saveAndFlush(expenseType);
        });
        expenseTypeId = created.getId();
        return created;
    }

    private ExpensePriceSetting createPriceSetting() {
        ExpensePriceSetting created = inNewTransaction(() -> {
            ExpenseType expenseType = new ExpenseType();
            expenseType.setCode("LOCK_" + UUID.randomUUID().toString().replace("-", ""));
            expenseType.setName("Lock test type");
            expenseType.setCalculationType(CalculationType.MANUAL);
            ExpenseType savedType = expenseTypeRepository.saveAndFlush(expenseType);

            ExpensePriceSetting priceSetting = new ExpensePriceSetting();
            priceSetting.setExpenseType(savedType);
            priceSetting.setPriceCode("DEFAULT");
            priceSetting.setPriceName("Lock test price");
            priceSetting.setUnitPrice(new BigDecimal("10.00"));
            priceSetting.setEffectiveFrom(LocalDate.of(2026, 8, 5));
            return expensePriceSettingRepository.saveAndFlush(priceSetting);
        });
        priceSettingId = created.getId();
        expenseTypeId = inNewTransaction(
                () -> expensePriceSettingRepository.findById(priceSettingId)
                        .orElseThrow()
                        .getExpenseType()
                        .getId()
        );
        return created;
    }

    private ExpenseType loadExpenseType() {
        return inNewTransaction(() -> expenseTypeRepository.findById(expenseTypeId).orElseThrow());
    }

    private ExpensePriceSetting loadPriceSetting() {
        return inNewTransaction(
                () -> expensePriceSettingRepository.findById(priceSettingId).orElseThrow()
        );
    }

    private <T> T inNewTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    private void assertVersionMapping(Class<?> entityType) throws Exception {
        Field version = entityType.getDeclaredField("version");
        assertTrue(version.isAnnotationPresent(Version.class));
        Column column = version.getAnnotation(Column.class);
        assertEquals("version", column.name());
        assertFalse(column.nullable());
        assertEquals(Long.class, version.getType());
        assertThrows(NoSuchMethodException.class, () -> entityType.getDeclaredMethod(
                "setVersion",
                Long.class
        ));
    }

    private void assertOptimisticLockFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ObjectOptimisticLockingFailureException
                    || current instanceof OptimisticLockException) {
                return;
            }
            current = current.getCause();
        }
        throw new AssertionError(
                "Expected an optimistic locking failure but got " + failure,
                failure
        );
    }
}
