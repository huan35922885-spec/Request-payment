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
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ExpenseTypeVersionRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.RenameExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.response.ExpenseTypeAdminResponse;
import tw.com.jsgcpa.paymentapproval.master.admin.exception.ExpenseTypeAdminBusinessException;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditAction;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditRecordCommand;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditService;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpensePriceSettingRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;

@ExtendWith(MockitoExtension.class)
class ExpenseTypeAdminServiceTest {

    private static final Long ACTOR_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 5);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-05T02:00:00Z"),
            ZoneId.of("Asia/Taipei")
    );
    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.parse("2026-08-05T10:00:00+08:00");

    @Mock
    private ExpenseTypeRepository expenseTypeRepository;

    @Mock
    private ExpensePriceSettingRepository expensePriceSettingRepository;

    @Mock
    private MasterDataAuditService auditService;

    private ExpenseTypeAdminService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseTypeAdminService(
                expenseTypeRepository,
                expensePriceSettingRepository,
                auditService,
                FIXED_CLOCK
        );
    }

    @Test
    void listsActiveAndInactiveExpenseTypesInRepositoryOrder() {
        ExpenseType first = expenseType(1L, "A", "A name", CalculationType.MANUAL, true, 2L);
        ExpenseType second = expenseType(2L, "B", "B name", CalculationType.MEAL, false, 3L);
        when(expenseTypeRepository.findAllByOrderByCodeAscIdAsc())
                .thenReturn(List.of(first, second));

        List<ExpenseTypeAdminResponse> result = service.list();

        assertEquals(2, result.size());
        assertEquals("A", result.get(0).code());
        assertTrue(result.get(0).active());
        assertEquals("B", result.get(1).code());
        assertFalse(result.get(1).active());
    }

    @Test
    void createsInactiveExpenseTypeAtVersionZeroAndAuditsSnapshot() {
        when(expenseTypeRepository.existsByCode("TRANSPORTATION")).thenReturn(false);
        when(expenseTypeRepository.saveAndFlush(any(ExpenseType.class)))
                .thenAnswer(invocation -> persist(invocation.getArgument(0), 10L));

        ExpenseTypeAdminResponse response = service.create(
                new CreateExpenseTypeRequest(
                        " transportation ",
                        "  Travel  ",
                        CalculationType.QUANTITY_PRICE
                ),
                ACTOR_ID
        );

        assertEquals(10L, response.id());
        assertEquals("TRANSPORTATION", response.code());
        assertEquals("Travel", response.name());
        assertEquals(CalculationType.QUANTITY_PRICE, response.calculationType());
        assertFalse(response.active());
        assertEquals(0L, response.version());
        ArgumentCaptor<MasterDataAuditRecordCommand> captor =
                ArgumentCaptor.forClass(MasterDataAuditRecordCommand.class);
        verify(auditService).record(captor.capture());
        MasterDataAuditRecordCommand command = captor.getValue();
        assertEquals(MasterDataAuditAction.EXPENSE_TYPE_CREATE, command.action());
        assertEquals(ACTOR_ID, command.actorId());
        assertEquals(null, command.beforeData());
        assertEquals(null, command.beforeVersion());
        assertEquals(0L, command.afterVersion());
        assertEquals(
                Map.of(
                        "id", 10L,
                        "code", "TRANSPORTATION",
                        "name", "Travel",
                        "calculationType", "QUANTITY_PRICE",
                        "active", false
                ),
                command.afterData()
        );
    }

    @Test
    void rejectsDuplicateCodeBeforeSaving() {
        when(expenseTypeRepository.existsByCode("MEAL")).thenReturn(true);

        ExpenseTypeAdminBusinessException exception = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.create(
                        new CreateExpenseTypeRequest("MEAL", "Meal", CalculationType.MEAL),
                        ACTOR_ID
                )
        );

        assertEquals("EXPENSE_TYPE_CODE_CONFLICT", exception.getCode());
        verify(expenseTypeRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void mapsConcurrentDuplicateCodeToConflict() {
        when(expenseTypeRepository.existsByCode("RACE")).thenReturn(false);
        when(expenseTypeRepository.saveAndFlush(any(ExpenseType.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate",
                        new SQLException("duplicate", "23505")
                ));

        ExpenseTypeAdminBusinessException exception = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.create(
                        new CreateExpenseTypeRequest("RACE", "Race", CalculationType.MANUAL),
                        ACTOR_ID
                )
        );

        assertEquals("EXPENSE_TYPE_CODE_CONFLICT", exception.getCode());
        verify(auditService, never()).record(any());
    }

    @Test
    void renamesAndAuditsBeforeAndAfterSnapshots() {
        ExpenseType expenseType = expenseType(
                20L,
                "MAIL",
                "Old name",
                CalculationType.QUANTITY_PRICE,
                false,
                0L
        );
        when(expenseTypeRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(expenseType));
        when(expenseTypeRepository.saveAndFlush(expenseType))
                .thenAnswer(invocation -> incrementVersion(invocation.getArgument(0)));

        ExpenseTypeAdminResponse response = service.rename(
                20L,
                new RenameExpenseTypeRequest(" New name ", 0L),
                ACTOR_ID
        );

        assertEquals("New name", response.name());
        assertEquals(1L, response.version());
        MasterDataAuditRecordCommand command = capturedAudit();
        assertEquals(MasterDataAuditAction.EXPENSE_TYPE_RENAME, command.action());
        assertEquals(0L, command.beforeVersion());
        assertEquals(1L, command.afterVersion());
        assertEquals("Old name", command.beforeData().get("name"));
        assertEquals("New name", command.afterData().get("name"));
        assertEquals(false, command.afterData().get("active"));
    }

    @Test
    void rejectsRenameWhenNameIsUnchangedWithoutMutationOrAudit() {
        ExpenseType expenseType = expenseType(
                20L, "MAIL", "Same", CalculationType.MANUAL, true, 2L
        );
        when(expenseTypeRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(expenseType));

        ExpenseTypeAdminBusinessException exception = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.rename(20L, new RenameExpenseTypeRequest(" Same ", 2L), ACTOR_ID)
        );

        assertEquals("EXPENSE_TYPE_NAME_UNCHANGED", exception.getCode());
        verify(expenseTypeRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void rejectsRenameWhenVersionIsStale() {
        ExpenseType expenseType = expenseType(
                20L, "MAIL", "Same", CalculationType.MANUAL, true, 2L
        );
        when(expenseTypeRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(expenseType));

        ExpenseTypeAdminBusinessException exception = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.rename(20L, new RenameExpenseTypeRequest("New", 1L), ACTOR_ID)
        );

        assertEquals("EXPENSE_TYPE_VERSION_CONFLICT", exception.getCode());
        verify(expenseTypeRepository, never()).saveAndFlush(any());
    }

    @Test
    void allowsManualActivationWithoutPriceLookup() {
        ExpenseType expenseType = expenseType(
                21L, "MANUAL", "Manual", CalculationType.MANUAL, false, 0L
        );
        when(expenseTypeRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(expenseType));
        when(expenseTypeRepository.saveAndFlush(expenseType))
                .thenAnswer(invocation -> incrementVersion(invocation.getArgument(0)));

        ExpenseTypeAdminResponse response = service.activate(
                21L, new ExpenseTypeVersionRequest(0L), ACTOR_ID
        );

        assertTrue(response.active());
        assertEquals(1L, response.version());
        verify(expensePriceSettingRepository, never()).findEffectivePrices(any(), any());
        assertEquals(MasterDataAuditAction.EXPENSE_TYPE_ACTIVATE, capturedAudit().action());
    }

    @Test
    void allowsTravelActivationWithoutPriceLookup() {
        ExpenseType expenseType = expenseType(
                22L, "TRAVEL", "Travel", CalculationType.TRAVEL, false, 0L
        );
        when(expenseTypeRepository.findByIdForUpdate(22L)).thenReturn(Optional.of(expenseType));
        when(expenseTypeRepository.saveAndFlush(expenseType))
                .thenAnswer(invocation -> incrementVersion(invocation.getArgument(0)));

        service.activate(22L, new ExpenseTypeVersionRequest(0L), ACTOR_ID);

        verify(expensePriceSettingRepository, never()).findEffectivePrices(any(), any());
    }

    @Test
    void requiresCurrentPriceForPricedActivation() {
        ExpenseType expenseType = expenseType(
                23L, "MEAL_NEW", "Meal", CalculationType.MEAL, false, 0L
        );
        when(expenseTypeRepository.findByIdForUpdate(23L)).thenReturn(Optional.of(expenseType));
        when(expensePriceSettingRepository.findEffectivePrices(23L, TODAY))
                .thenReturn(List.of());

        ExpenseTypeAdminBusinessException exception = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.activate(23L, new ExpenseTypeVersionRequest(0L), ACTOR_ID)
        );

        assertEquals("EXPENSE_TYPE_CURRENT_PRICE_REQUIRED", exception.getCode());
        assertFalse(expenseType.getActive());
        assertEquals(0L, expenseType.getVersion());
        verify(expenseTypeRepository, never()).saveAndFlush(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void activatesPricedTypeWhenCurrentPriceExists() {
        ExpenseType expenseType = expenseType(
                24L, "MEAL_NEW", "Meal", CalculationType.MEAL, false, 0L
        );
        when(expenseTypeRepository.findByIdForUpdate(24L)).thenReturn(Optional.of(expenseType));
        when(expensePriceSettingRepository.findEffectivePrices(24L, TODAY))
                .thenReturn(List.of(new ExpensePriceSetting()));
        when(expenseTypeRepository.saveAndFlush(expenseType))
                .thenAnswer(invocation -> incrementVersion(invocation.getArgument(0)));

        service.activate(24L, new ExpenseTypeVersionRequest(0L), ACTOR_ID);

        assertTrue(expenseType.getActive());
        assertEquals(1L, expenseType.getVersion());
        assertEquals(MasterDataAuditAction.EXPENSE_TYPE_ACTIVATE, capturedAudit().action());
    }

    @Test
    void rejectsAlreadyActiveAndAlreadyInactiveOperations() {
        ExpenseType active = expenseType(
                25L, "ACTIVE", "Active", CalculationType.MANUAL, true, 0L
        );
        when(expenseTypeRepository.findByIdForUpdate(25L)).thenReturn(Optional.of(active));
        ExpenseTypeAdminBusinessException activeException = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.activate(25L, new ExpenseTypeVersionRequest(0L), ACTOR_ID)
        );
        assertEquals("EXPENSE_TYPE_ALREADY_ACTIVE", activeException.getCode());

        ExpenseType inactive = expenseType(
                26L, "INACTIVE", "Inactive", CalculationType.MANUAL, false, 0L
        );
        when(expenseTypeRepository.findByIdForUpdate(26L)).thenReturn(Optional.of(inactive));
        ExpenseTypeAdminBusinessException inactiveException = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.deactivate(
                        26L,
                        new DeactivateExpenseTypeRequest("reason", 0L),
                        ACTOR_ID
                )
        );
        assertEquals("EXPENSE_TYPE_ALREADY_INACTIVE", inactiveException.getCode());
        verify(expenseTypeRepository, never()).saveAndFlush(any());
    }

    @Test
    void deactivatesWithStrippedReasonAndAuditsIt() {
        ExpenseType expenseType = expenseType(
                27L, "ACTIVE", "Active", CalculationType.MANUAL, true, 2L
        );
        when(expenseTypeRepository.findByIdForUpdate(27L)).thenReturn(Optional.of(expenseType));
        when(expenseTypeRepository.saveAndFlush(expenseType))
                .thenAnswer(invocation -> incrementVersion(invocation.getArgument(0)));

        ExpenseTypeAdminResponse response = service.deactivate(
                27L,
                new DeactivateExpenseTypeRequest("  obsolete  ", 2L),
                ACTOR_ID
        );

        assertFalse(response.active());
        assertEquals(3L, response.version());
        MasterDataAuditRecordCommand command = capturedAudit();
        assertEquals(MasterDataAuditAction.EXPENSE_TYPE_DEACTIVATE, command.action());
        assertEquals("obsolete", command.reason());
        assertEquals(true, command.beforeData().get("active"));
        assertEquals(false, command.afterData().get("active"));
    }

    @Test
    void mapsOptimisticLockFailureAndDoesNotAudit() {
        ExpenseType expenseType = expenseType(
                28L, "LOCK", "Before", CalculationType.MANUAL, true, 0L
        );
        when(expenseTypeRepository.findByIdForUpdate(28L)).thenReturn(Optional.of(expenseType));
        when(expenseTypeRepository.saveAndFlush(expenseType))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        ExpenseTypeAdminBusinessException exception = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.rename(28L, new RenameExpenseTypeRequest("After", 0L), ACTOR_ID)
        );

        assertEquals("EXPENSE_TYPE_VERSION_CONFLICT", exception.getCode());
        verify(auditService, never()).record(any());
    }

    @Test
    void propagatesAuditFailureAfterEntityMutation() {
        ExpenseType expenseType = expenseType(
                29L, "AUDIT", "Before", CalculationType.MANUAL, true, 0L
        );
        when(expenseTypeRepository.findByIdForUpdate(29L)).thenReturn(Optional.of(expenseType));
        when(expenseTypeRepository.saveAndFlush(expenseType))
                .thenAnswer(invocation -> incrementVersion(invocation.getArgument(0)));
        when(auditService.record(any()))
                .thenThrow(new IllegalStateException("audit failed"));

        assertThrows(
                IllegalStateException.class,
                () -> service.rename(29L, new RenameExpenseTypeRequest("After", 0L), ACTOR_ID)
        );
        assertEquals("After", expenseType.getName());
        assertEquals(1L, expenseType.getVersion());
    }

    @Test
    void returnsNotFoundForMissingExpenseType() {
        when(expenseTypeRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        ExpenseTypeAdminBusinessException exception = assertThrows(
                ExpenseTypeAdminBusinessException.class,
                () -> service.rename(99L, new RenameExpenseTypeRequest("Name", 0L), ACTOR_ID)
        );

        assertEquals("EXPENSE_TYPE_NOT_FOUND", exception.getCode());
    }

    private MasterDataAuditRecordCommand capturedAudit() {
        ArgumentCaptor<MasterDataAuditRecordCommand> captor =
                ArgumentCaptor.forClass(MasterDataAuditRecordCommand.class);
        verify(auditService).record(captor.capture());
        return captor.getValue();
    }

    private ExpenseType expenseType(
            Long id,
            String code,
            String name,
            CalculationType calculationType,
            boolean active,
            Long version
    ) {
        ExpenseType expenseType = new ExpenseType();
        ReflectionTestUtils.setField(expenseType, "id", id);
        ReflectionTestUtils.setField(expenseType, "version", version);
        ReflectionTestUtils.setField(expenseType, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(expenseType, "updatedAt", CREATED_AT);
        expenseType.setCode(code);
        expenseType.setName(name);
        expenseType.setCalculationType(calculationType);
        expenseType.setActive(active);
        return expenseType;
    }

    private ExpenseType persist(ExpenseType expenseType, Long id) {
        ReflectionTestUtils.setField(expenseType, "id", id);
        ReflectionTestUtils.setField(expenseType, "version", 0L);
        ReflectionTestUtils.setField(expenseType, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(expenseType, "updatedAt", CREATED_AT);
        return expenseType;
    }

    private ExpenseType incrementVersion(ExpenseType expenseType) {
        ReflectionTestUtils.setField(
                expenseType,
                "version",
                expenseType.getVersion() + 1
        );
        ReflectionTestUtils.setField(expenseType, "updatedAt", CREATED_AT.plusMinutes(1));
        return expenseType;
    }
}
