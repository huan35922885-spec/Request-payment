package tw.com.jsgcpa.paymentapproval.master.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ExpensePriceSettingVersionRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.UpdateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.response.ExpensePriceSettingAdminResponse;
import tw.com.jsgcpa.paymentapproval.master.admin.exception.ExpenseTypeAdminBusinessException;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditAction;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditRecordCommand;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditService;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpensePriceSettingRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;
import tw.com.jsgcpa.paymentapproval.payment.service.ExpensePriceResolver;

@ExtendWith(MockitoExtension.class)
class ExpensePriceSettingAdminServiceTest {

    private static final Long ACTOR_ID = 9L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 5);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T02:00:00Z"),
            ZoneId.of("Asia/Taipei")
    );
    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-08-05T10:00:00+08:00");

    @Mock
    private ExpensePriceSettingRepository priceRepository;
    @Mock
    private ExpenseTypeRepository typeRepository;
    @Mock
    private MasterDataAuditService auditService;
    @Mock
    private ExpensePriceResolver resolver;

    private ExpensePriceSettingAdminService service;

    @BeforeEach
    void setUp() {
        service = new ExpensePriceSettingAdminService(
                priceRepository, typeRepository, auditService, resolver, CLOCK
        );
    }

    @Test
    void createsInactivePriceWithNormalizedCodeAndAudit() {
        ExpenseType type = type(1L, "MEAL", CalculationType.MEAL);
        when(typeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(type));
        when(priceRepository.findOverlappingPeriods(
                1L, "STANDARD", LocalDate.of(2026, 8, 10), null, 0L
        )).thenReturn(List.of());
        when(priceRepository.saveAndFlush(any(ExpensePriceSetting.class)))
                .thenAnswer(invocation -> persist(invocation.getArgument(0), 20L, 0L));

        ExpensePriceSettingAdminResponse response = service.create(
                1L,
                new CreateExpensePriceSettingRequest(
                        " standard ", new BigDecimal("100.00"),
                        LocalDate.of(2026, 8, 10), null
                ),
                ACTOR_ID
        );

        assertEquals("STANDARD", response.priceCode());
        assertEquals(new BigDecimal("100.00"), response.amount());
        assertFalse(response.active());
        assertEquals(0L, response.version());
        MasterDataAuditRecordCommand command = capturedAudit();
        assertEquals(MasterDataAuditAction.EXPENSE_PRICE_CREATE, command.action());
        assertEquals(0L, command.afterVersion());
        assertEquals("STANDARD", command.afterData().get("priceName"));
    }

    @Test
    void rejectsManualPriceAndInvalidRange() {
        ExpenseType manual = type(2L, "MANUAL", CalculationType.MANUAL);
        when(typeRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(manual));
        ExpenseTypeAdminBusinessException unsupported = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.create(2L, request("DEFAULT", TODAY, TODAY), ACTOR_ID)
        );
        assertEquals("EXPENSE_PRICE_SETTING_UNSUPPORTED", unsupported.getCode());

        ExpenseType meal = type(3L, "MEAL", CalculationType.MEAL);
        when(typeRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(meal));
        ExpenseTypeAdminBusinessException invalid = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.create(
                        3L,
                        request("DEFAULT", TODAY.plusDays(1), TODAY),
                        ACTOR_ID
                )
        );
        assertEquals("EXPENSE_PRICE_PERIOD_INVALID", invalid.getCode());
        verify(priceRepository, never()).saveAndFlush(any());
    }

    @Test
    void updatesOnlyInactiveSettingAndAuditsReplacement() {
        ExpenseType type = type(4L, "MEAL", CalculationType.MEAL);
        ExpensePriceSetting setting = price(40L, type, "DEFAULT", false, 2L);
        when(priceRepository.findById(40L)).thenReturn(Optional.of(setting));
        when(typeRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(type));
        when(priceRepository.findOverlappingPeriods(
                4L, "DEFAULT", TODAY.plusDays(1), null, 40L
        )).thenReturn(List.of());
        when(priceRepository.saveAndFlush(setting))
                .thenAnswer(invocation -> persist(invocation.getArgument(0), 40L, 3L));

        ExpensePriceSettingAdminResponse response = service.update(
                40L,
                new UpdateExpensePriceSettingRequest(
                        new BigDecimal("120.00"), TODAY.plusDays(1), null, 2L
                ),
                ACTOR_ID
        );

        assertEquals(new BigDecimal("120.00"), response.amount());
        assertEquals(3L, response.version());
        MasterDataAuditRecordCommand command = capturedAudit();
        assertEquals(MasterDataAuditAction.EXPENSE_PRICE_REPLACE, command.action());
        assertEquals(2L, command.beforeVersion());
        assertEquals(3L, command.afterVersion());
        assertEquals(new BigDecimal("100.00"), command.beforeData().get("amount"));
    }

    @Test
    void rejectsActiveEditUnchangedAndStaleVersion() {
        ExpenseType type = type(5L, "MEAL", CalculationType.MEAL);
        ExpensePriceSetting active = price(50L, type, "DEFAULT", true, 1L);
        when(priceRepository.findById(50L)).thenReturn(Optional.of(active));
        when(typeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(type));
        ExpenseTypeAdminBusinessException activeError = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.update(
                        50L,
                        new UpdateExpensePriceSettingRequest(
                                new BigDecimal("120.00"), TODAY, null, 1L
                        ),
                        ACTOR_ID
                )
        );
        assertEquals("EXPENSE_PRICE_SETTING_ACTIVE_EDIT_FORBIDDEN", activeError.getCode());

        ExpensePriceSetting inactive = price(51L, type, "DEFAULT", false, 1L);
        when(priceRepository.findById(51L)).thenReturn(Optional.of(inactive));
        ExpenseTypeAdminBusinessException stale = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.update(
                        51L,
                        new UpdateExpensePriceSettingRequest(
                                new BigDecimal("120.00"), TODAY, null, 0L
                        ),
                        ACTOR_ID
                )
        );
        assertEquals("EXPENSE_PRICE_SETTING_VERSION_CONFLICT", stale.getCode());
    }

    @Test
    void activatesAndRejectsDuplicateActivation() {
        ExpenseType type = type(6L, "MEAL", CalculationType.MEAL);
        ExpensePriceSetting setting = price(60L, type, "DEFAULT", false, 0L);
        when(priceRepository.findById(60L)).thenReturn(Optional.of(setting));
        when(typeRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(type));
        when(priceRepository.findOverlappingActivePeriods(
                6L, "DEFAULT", TODAY, null, 60L
        )).thenReturn(List.of());
        when(priceRepository.saveAndFlush(setting))
                .thenAnswer(invocation -> persist(invocation.getArgument(0), 60L, 1L));

        ExpensePriceSettingAdminResponse response = service.activate(
                60L, new ExpensePriceSettingVersionRequest(0L), ACTOR_ID
        );
        assertTrue(response.active());
        assertEquals(1L, response.version());

        ExpenseTypeAdminBusinessException duplicate = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.activate(
                        60L, new ExpensePriceSettingVersionRequest(1L), ACTOR_ID
                )
        );
        assertEquals("EXPENSE_PRICE_SETTING_ALREADY_ACTIVE", duplicate.getCode());
    }

    @Test
    void deactivationCannotRemoveLastCurrentPrice() {
        ExpenseType type = type(7L, "MEAL", CalculationType.MEAL);
        ExpensePriceSetting setting = price(70L, type, "DEFAULT", true, 1L);
        when(priceRepository.findById(70L)).thenReturn(Optional.of(setting));
        when(typeRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(type));
        when(priceRepository.findEffectivePriceSettings(7L, "DEFAULT", TODAY))
                .thenReturn(List.of(setting));

        ExpenseTypeAdminBusinessException exception = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.deactivate(
                        70L,
                        new DeactivateExpensePriceSettingRequest(1L, " retire "),
                        ACTOR_ID
                )
        );
        assertEquals("EXPENSE_PRICE_CURRENT_REQUIRED", exception.getCode());
        verify(priceRepository, never()).saveAndFlush(any());
    }

    @Test
    void mapsOptimisticLockFailureAndDoesNotAudit() {
        ExpenseType type = type(8L, "MEAL", CalculationType.MEAL);
        ExpensePriceSetting setting = price(80L, type, "DEFAULT", false, 0L);
        when(priceRepository.findById(80L)).thenReturn(Optional.of(setting));
        when(typeRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(type));
        when(priceRepository.findOverlappingPeriods(
                8L, "DEFAULT", TODAY.plusDays(1), null, 80L
        )).thenReturn(List.of());
        when(priceRepository.saveAndFlush(setting))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        ExpenseTypeAdminBusinessException exception = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.update(
                        80L,
                        new UpdateExpensePriceSettingRequest(
                                new BigDecimal("120.00"), TODAY.plusDays(1), null, 0L
                        ),
                        ACTOR_ID
                )
        );
        assertEquals("EXPENSE_PRICE_SETTING_VERSION_CONFLICT", exception.getCode());
        verify(auditService, never()).record(any());
    }

    @Test
    void mapsDatabasePeriodConflict() {
        ExpenseType type = type(9L, "MEAL", CalculationType.MEAL);
        when(typeRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(type));
        when(priceRepository.findOverlappingPeriods(
                9L, "DEFAULT", TODAY, null, 0L
        )).thenReturn(List.of());
        when(priceRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "overlap", new java.sql.SQLException("overlap", "23P01")
                ));

        ExpenseTypeAdminBusinessException exception = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.create(9L, request("DEFAULT", TODAY, null), ACTOR_ID)
        );
        assertEquals("EXPENSE_PRICE_PERIOD_CONFLICT", exception.getCode());
        verify(auditService, never()).record(any());
    }

    private CreateExpensePriceSettingRequest request(
            String code, LocalDate from, LocalDate to
    ) {
        return new CreateExpensePriceSettingRequest(
                code, new BigDecimal("10.00"), from, to
        );
    }

    private MasterDataAuditRecordCommand capturedAudit() {
        ArgumentCaptor<MasterDataAuditRecordCommand> captor =
                ArgumentCaptor.forClass(MasterDataAuditRecordCommand.class);
        verify(auditService).record(captor.capture());
        return captor.getValue();
    }

    private ExpenseType type(Long id, String code, CalculationType calculationType) {
        ExpenseType type = new ExpenseType();
        ReflectionTestUtils.setField(type, "id", id);
        type.setCode(code);
        type.setName(code);
        type.setCalculationType(calculationType);
        type.setActive(true);
        return type;
    }

    private ExpensePriceSetting price(
            Long id, ExpenseType type, String code, boolean active, Long version
    ) {
        ExpensePriceSetting setting = new ExpensePriceSetting();
        ReflectionTestUtils.setField(setting, "id", id);
        ReflectionTestUtils.setField(setting, "version", version);
        ReflectionTestUtils.setField(setting, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(setting, "updatedAt", CREATED_AT);
        setting.setExpenseType(type);
        setting.setPriceCode(code);
        setting.setPriceName(code);
        setting.setUnitPrice(new BigDecimal("100.00"));
        setting.setEffectiveFrom(TODAY);
        setting.setEffectiveTo(null);
        setting.setActive(active);
        return setting;
    }

    private ExpensePriceSetting persist(
            ExpensePriceSetting setting, Long id, Long version
    ) {
        ReflectionTestUtils.setField(setting, "id", id);
        ReflectionTestUtils.setField(setting, "version", version);
        ReflectionTestUtils.setField(setting, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(setting, "updatedAt", CREATED_AT);
        return setting;
    }
}
