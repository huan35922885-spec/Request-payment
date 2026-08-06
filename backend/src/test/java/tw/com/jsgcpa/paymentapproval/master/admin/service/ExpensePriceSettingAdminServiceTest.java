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
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CloseExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ExpensePriceSettingVersionRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ReplaceExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.response.ExpensePriceSettingAdminResponse;
import tw.com.jsgcpa.paymentapproval.master.admin.exception.ExpensePriceSettingAdminBusinessException;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditAction;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditRecordCommand;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditService;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpensePriceSettingRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.service.ExpensePriceResolver;

@ExtendWith(MockitoExtension.class)
class ExpensePriceSettingAdminServiceTest {

    private static final Long ACTOR_ID = 9L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 5);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T02:00:00Z"), ZoneId.of("Asia/Taipei")
    );
    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-08-05T10:00:00+08:00");

    @Mock private ExpensePriceSettingRepository priceRepository;
    @Mock private ExpenseTypeRepository typeRepository;
    @Mock private MasterDataAuditService auditService;
    @Mock private ExpensePriceResolver resolver;

    private ExpensePriceSettingAdminService service;

    @BeforeEach
    void setUp() {
        service = new ExpensePriceSettingAdminService(
                priceRepository, typeRepository, auditService, resolver, CLOCK
        );
    }

    @Test
    void createIsActiveUsesPriceNameAndAuditsVersionSeparately() {
        ExpenseType type = type(1L, "MEAL", CalculationType.MEAL, true);
        when(typeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(type));
        when(priceRepository.findOverlappingActivePeriods(
                1L, "STANDARD", TODAY.plusDays(1), null, 0L
        )).thenReturn(List.of());
        when(priceRepository.saveAndFlush(any(ExpensePriceSetting.class)))
                .thenAnswer(invocation -> persist(invocation.getArgument(0), 20L, 0L));

        ExpensePriceSettingAdminResponse response = service.create(
                1L,
                new CreateExpensePriceSettingRequest(
                        " standard ", " Standard mail ", new BigDecimal("100.00"),
                        TODAY.plusDays(1)
                ),
                ACTOR_ID
        );

        assertEquals("STANDARD", response.priceCode());
        assertEquals("Standard mail", response.priceName());
        assertTrue(response.active());
        assertEquals(0L, response.version());
        MasterDataAuditRecordCommand command = capturedAudits().get(0);
        assertEquals(MasterDataAuditAction.EXPENSE_PRICE_CREATE, command.action());
        assertEquals(0L, command.afterVersion());
        assertFalse(command.afterData().containsKey("version"));
    }

    @Test
    void createRejectsBackdateAndManualType() {
        ExpenseType meal = type(2L, "MEAL", CalculationType.MEAL, true);
        when(typeRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(meal));
        ExpensePriceSettingAdminBusinessException backdate = assertThrows(
                ExpensePriceSettingAdminBusinessException.class,
                () -> service.create(2L, createRequest("DEFAULT", TODAY.minusDays(1)), ACTOR_ID)
        );
        assertEquals("EXPENSE_PRICE_BACKDATE_FORBIDDEN", backdate.getCode());

        ExpenseType manual = type(3L, "MANUAL", CalculationType.MANUAL, true);
        when(typeRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(manual));
        ExpensePriceSettingAdminBusinessException unsupported = assertThrows(
                ExpensePriceSettingAdminBusinessException.class,
                () -> service.create(3L, createRequest("DEFAULT", TODAY), ACTOR_ID)
        );
        assertEquals("EXPENSE_PRICE_SETTING_UNSUPPORTED", unsupported.getCode());
        verify(priceRepository, never()).saveAndFlush(any());
    }

    @Test
    void replaceClosesOldRowCreatesNewRowAndWritesTwoAudits() {
        ExpenseType type = type(4L, "MEAL", CalculationType.MEAL, true);
        ExpensePriceSetting old = price(40L, type, "DEFAULT", true, 2L, TODAY);
        when(priceRepository.findById(40L)).thenReturn(Optional.of(old));
        when(priceRepository.findByIdForUpdate(40L)).thenReturn(Optional.of(old));
        when(typeRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(type));
        when(priceRepository.saveAndFlush(any(ExpensePriceSetting.class)))
                .thenAnswer(invocation -> {
                    ExpensePriceSetting setting = invocation.getArgument(0);
                    return setting == old
                            ? persist(setting, 40L, 3L)
                            : persist(setting, 41L, 0L);
                });

        ExpensePriceSettingAdminResponse response = service.replace(
                40L,
                new ReplaceExpensePriceSettingRequest(
                        "New name", new BigDecimal("120.00"), TODAY.plusDays(5),
                        2L, " replace reason "
                ),
                ACTOR_ID
        );

        assertEquals(41L, response.id());
        assertEquals(TODAY.plusDays(5), response.effectiveFrom());
        assertEquals(0L, response.version());
        assertEquals(TODAY.plusDays(4), old.getEffectiveTo());
        List<MasterDataAuditRecordCommand> audits = capturedAudits();
        assertEquals(2, audits.size());
        assertEquals(audits.get(0).operationId(), audits.get(1).operationId());
        assertEquals(2L, audits.get(0).beforeVersion());
        assertEquals(3L, audits.get(0).afterVersion());
        assertEquals(0L, audits.get(1).afterVersion());
        assertEquals("replace reason", audits.get(0).reason());
        assertEquals("replace reason", audits.get(1).reason());
    }

    @Test
    void closeUpdatesOnlyEffectiveToAndRejectsBackdate() {
        ExpenseType type = type(5L, "MEAL", CalculationType.MEAL, true);
        ExpensePriceSetting setting = price(50L, type, "DEFAULT", true, 1L, TODAY);
        when(priceRepository.findById(50L)).thenReturn(Optional.of(setting));
        when(priceRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(setting));
        when(typeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(type));
        when(priceRepository.saveAndFlush(setting))
                .thenAnswer(invocation -> persist(invocation.getArgument(0), 50L, 2L));

        ExpensePriceSettingAdminResponse response = service.close(
                50L,
                new CloseExpensePriceSettingRequest(
                        TODAY.plusDays(3), 1L, " close reason "
                ),
                ACTOR_ID
        );
        assertEquals(TODAY.plusDays(3), response.effectiveTo());
        assertEquals(new BigDecimal("100.00"), response.amount());
        assertEquals(" close reason ".strip(), capturedAudits().get(0).reason());

        ExpensePriceSettingAdminBusinessException invalid = assertThrows(
                ExpensePriceSettingAdminBusinessException.class,
                () -> service.close(
                        50L,
                        new CloseExpensePriceSettingRequest(TODAY.minusDays(1), 2L, "again"),
                        ACTOR_ID
                )
        );
        assertEquals("EXPENSE_PRICE_PERIOD_INVALID", invalid.getCode());
    }

    @Test
    void deactivationChecksCurrentOnlyWhenParentIsActive() {
        ExpenseType activeType = type(6L, "MEAL", CalculationType.MEAL, true);
        ExpensePriceSetting current = price(60L, activeType, "DEFAULT", true, 1L, TODAY);
        ExpensePriceSetting otherPrice = price(
                61L, activeType, "REGISTERED_MAIL", true, 1L, TODAY
        );
        when(priceRepository.findById(60L)).thenReturn(Optional.of(current));
        when(priceRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(current));
        when(typeRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(activeType));
        when(priceRepository.findEffectivePrices(6L, TODAY))
                .thenReturn(List.of(current));
        ExpensePriceSettingAdminBusinessException required = assertThrows(
                ExpensePriceSettingAdminBusinessException.class,
                () -> service.deactivate(
                        60L, new DeactivateExpensePriceSettingRequest(1L, "retire"), ACTOR_ID
                )
        );
        assertEquals("EXPENSE_PRICE_CURRENT_REQUIRED", required.getCode());

        when(priceRepository.findEffectivePrices(6L, TODAY))
                .thenReturn(List.of(current, otherPrice));
        when(priceRepository.saveAndFlush(current))
                .thenAnswer(invocation -> persist(invocation.getArgument(0), 60L, 2L));
        ExpensePriceSettingAdminResponse response = service.deactivate(
                60L, new DeactivateExpensePriceSettingRequest(1L, " retire "), ACTOR_ID
        );
        assertFalse(response.active());
        assertEquals("retire", capturedAudits().get(0).reason());

        ExpensePriceSetting inactiveParentTarget = price(
                62L, activeType, "DEFAULT", true, 1L, TODAY
        );
        activeType.setActive(false);
        when(priceRepository.findById(62L)).thenReturn(Optional.of(inactiveParentTarget));
        when(priceRepository.findByIdForUpdate(62L))
                .thenReturn(Optional.of(inactiveParentTarget));
        when(priceRepository.saveAndFlush(inactiveParentTarget))
                .thenAnswer(invocation -> persist(invocation.getArgument(0), 62L, 2L));
        ExpensePriceSettingAdminResponse inactiveParentResponse = service.deactivate(
                62L, new DeactivateExpensePriceSettingRequest(1L, " inactive parent "), ACTOR_ID
        );
        assertFalse(inactiveParentResponse.active());
        verify(priceRepository, org.mockito.Mockito.times(2))
                .findEffectivePrices(6L, TODAY);
    }

    @Test
    void deactivatesFuturePriceWithoutCurrentPriceCheck() {
        ExpenseType type = type(10L, "MEAL", CalculationType.MEAL, true);
        ExpensePriceSetting future = price(
                100L, type, "DEFAULT", true, 1L, TODAY.plusDays(1)
        );
        when(priceRepository.findById(100L)).thenReturn(Optional.of(future));
        when(priceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(future));
        when(typeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(type));
        when(priceRepository.saveAndFlush(future))
                .thenAnswer(invocation -> persist(invocation.getArgument(0), 100L, 2L));

        ExpensePriceSettingAdminResponse response = service.deactivate(
                100L,
                new DeactivateExpensePriceSettingRequest(1L, " retire future "),
                ACTOR_ID
        );

        assertFalse(response.active());
        assertEquals(2L, response.version());
        assertEquals("retire future", capturedAudits().get(0).reason());
        verify(priceRepository, never()).findEffectivePrices(any(), any());
    }

    @Test
    void deactivatesExpiredPriceWithoutCurrentPriceCheck() {
        ExpenseType type = type(11L, "MEAL", CalculationType.MEAL, true);
        ExpensePriceSetting expired = price(
                110L, type, "DEFAULT", true, 1L, TODAY.minusDays(10)
        );
        expired.setEffectiveTo(TODAY.minusDays(1));
        when(priceRepository.findById(110L)).thenReturn(Optional.of(expired));
        when(priceRepository.findByIdForUpdate(110L)).thenReturn(Optional.of(expired));
        when(typeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(type));
        when(priceRepository.saveAndFlush(expired))
                .thenAnswer(invocation -> persist(invocation.getArgument(0), 110L, 2L));

        ExpensePriceSettingAdminResponse response = service.deactivate(
                110L,
                new DeactivateExpensePriceSettingRequest(1L, " retire expired "),
                ACTOR_ID
        );

        assertFalse(response.active());
        assertEquals(2L, response.version());
        assertEquals("retire expired", capturedAudits().get(0).reason());
        verify(priceRepository, never()).findEffectivePrices(any(), any());
    }

    @Test
    void effectiveUsesRequestedPriceCodeAndMapsNotFound() {
        ExpenseType type = type(7L, "CONFIRMATION", CalculationType.CONFIRMATION, true);
        ExpensePriceSetting setting = price(70L, type, "NORMAL_MAIL", true, 0L, TODAY);
        when(typeRepository.findById(7L)).thenReturn(Optional.of(type));
        when(resolver.resolve(7L, "NORMAL_MAIL", TODAY)).thenReturn(setting);
        assertEquals(
                "NORMAL_MAIL",
                service.effective(7L, " normal_mail ", TODAY).priceCode()
        );

        when(resolver.resolve(7L, "REGISTERED_MAIL", TODAY))
                .thenThrow(new PaymentDraftBusinessException("PRICE_SETTING_NOT_FOUND", "missing"));
        ExpensePriceSettingAdminBusinessException notFound = assertThrows(
                ExpensePriceSettingAdminBusinessException.class,
                () -> service.effective(7L, "REGISTERED_MAIL", TODAY)
        );
        assertEquals("EXPENSE_PRICE_SETTING_NOT_FOUND", notFound.getCode());
    }

    @Test
    void optimisticLockFailureDoesNotWriteAudit() {
        ExpenseType type = type(8L, "MEAL", CalculationType.MEAL, true);
        ExpensePriceSetting old = price(80L, type, "DEFAULT", true, 0L, TODAY);
        when(priceRepository.findById(80L)).thenReturn(Optional.of(old));
        when(priceRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(old));
        when(typeRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(type));
        when(priceRepository.saveAndFlush(old))
                .thenThrow(new OptimisticLockingFailureException("stale"));
        ExpensePriceSettingAdminBusinessException exception = assertThrows(
                ExpensePriceSettingAdminBusinessException.class,
                () -> service.replace(
                        80L,
                        new ReplaceExpensePriceSettingRequest(
                                "new", new BigDecimal("120.00"), TODAY.plusDays(1),
                                0L, "reason"
                        ),
                        ACTOR_ID
                )
        );
        assertEquals("EXPENSE_PRICE_SETTING_VERSION_CONFLICT", exception.getCode());
        verify(auditService, never()).record(any());
    }

    @Test
    void auditFailureIsNotSwallowed() {
        ExpenseType type = type(9L, "MEAL", CalculationType.MEAL, true);
        when(typeRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(type));
        when(priceRepository.findOverlappingActivePeriods(
                9L, "DEFAULT", TODAY, null, 0L
        )).thenReturn(List.of());
        when(priceRepository.saveAndFlush(any(ExpensePriceSetting.class)))
                .thenAnswer(invocation -> persist(invocation.getArgument(0), 90L, 0L));
        when(auditService.record(any())).thenThrow(new IllegalStateException("audit failed"));
        assertThrows(
                IllegalStateException.class,
                () -> service.create(9L, createRequest("DEFAULT", TODAY), ACTOR_ID)
        );
    }

    private CreateExpensePriceSettingRequest createRequest(String code, LocalDate from) {
        return new CreateExpensePriceSettingRequest(
                code, "Test price", new BigDecimal("10.00"), from
        );
    }

    private List<MasterDataAuditRecordCommand> capturedAudits() {
        ArgumentCaptor<MasterDataAuditRecordCommand> captor =
                ArgumentCaptor.forClass(MasterDataAuditRecordCommand.class);
        verify(auditService, org.mockito.Mockito.atLeastOnce()).record(captor.capture());
        return captor.getAllValues();
    }

    private ExpenseType type(
            Long id, String code, CalculationType calculationType, boolean active
    ) {
        ExpenseType type = new ExpenseType();
        ReflectionTestUtils.setField(type, "id", id);
        type.setCode(code);
        type.setName(code);
        type.setCalculationType(calculationType);
        type.setActive(active);
        return type;
    }

    private ExpensePriceSetting price(
            Long id, ExpenseType type, String code, boolean active, Long version, LocalDate from
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
        setting.setEffectiveFrom(from);
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
