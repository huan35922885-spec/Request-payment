package tw.com.jsgcpa.paymentapproval.master.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tw.com.jsgcpa.paymentapproval.master.audit.entity.MasterDataAuditLog;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditAction;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditTargetType;
import tw.com.jsgcpa.paymentapproval.master.audit.repository.MasterDataAuditLogRepository;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditRecordCommand;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditService;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class MasterDataAuditServiceTest {

    @Mock
    private MasterDataAuditLogRepository auditLogRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private MasterDataAuditService service;

    @Test
    void recordsAuthenticatedActorSnapshotsAndDoesNotUseTargetEntity() {
        AppUser actor = new AppUser();
        actor.setUsername("master.admin");
        actor.setDisplayName("Master Admin");
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(auditLogRepository.save(any(MasterDataAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("code", "MEAL");
        MasterDataAuditRecordCommand command = new MasterDataAuditRecordCommand(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_TYPE,
                10L,
                MasterDataAuditAction.EXPENSE_TYPE_CREATE,
                7L,
                null,
                after,
                null,
                0L,
                null
        );

        MasterDataAuditLog result = service.record(command);

        ArgumentCaptor<MasterDataAuditLog> captor =
                ArgumentCaptor.forClass(MasterDataAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        MasterDataAuditLog saved = captor.getValue();
        assertEquals("master.admin", saved.getActorUsernameSnapshot());
        assertEquals("Master Admin", saved.getActorDisplayNameSnapshot());
        assertEquals(actor, saved.getActor());
        assertEquals(after, saved.getAfterData());
        assertEquals(saved, result);
    }

    @Test
    void commandStripsReasonAndCopiesSnapshotMaps() {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("name", "Meal");
        MasterDataAuditRecordCommand command = new MasterDataAuditRecordCommand(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_TYPE,
                10L,
                MasterDataAuditAction.EXPENSE_TYPE_DEACTIVATE,
                7L,
                Map.of("active", true),
                after,
                0L,
                1L,
                "\u2003no longer used\u2003"
        );

        after.put("mutated", true);

        assertEquals("no longer used", command.reason());
        assertEquals(Map.of("name", "Meal"), command.afterData());
        assertThrows(UnsupportedOperationException.class,
                () -> command.afterData().put("new", "value"));
    }

    @Test
    void rejectsInvalidCommandShapes() {
        assertThrows(IllegalArgumentException.class, () -> new MasterDataAuditRecordCommand(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_TYPE,
                10L,
                MasterDataAuditAction.EXPENSE_TYPE_CREATE,
                7L,
                Map.of(),
                Map.of(),
                0L,
                0L,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new MasterDataAuditRecordCommand(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_TYPE,
                10L,
                MasterDataAuditAction.EXPENSE_TYPE_CREATE,
                7L,
                null,
                Map.of(),
                1L,
                0L,
                null
        ));
    }

    @Test
    void allowsNullReasonForExpenseTypeDeactivation() {
        MasterDataAuditRecordCommand command = new MasterDataAuditRecordCommand(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_TYPE,
                10L,
                MasterDataAuditAction.EXPENSE_TYPE_DEACTIVATE,
                7L,
                Map.of("active", true),
                Map.of("active", false),
                0L,
                1L,
                null
        );

        assertNull(command.reason());
    }

    @Test
    void allowsNullReasonForExpensePriceReplacementWithBothValidShapes() {
        MasterDataAuditRecordCommand updateCommand = new MasterDataAuditRecordCommand(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_PRICE_SETTING,
                10L,
                MasterDataAuditAction.EXPENSE_PRICE_REPLACE,
                7L,
                Map.of("unitPrice", 10),
                Map.of("unitPrice", 12),
                0L,
                1L,
                null
        );
        MasterDataAuditRecordCommand createCommand = new MasterDataAuditRecordCommand(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_PRICE_SETTING,
                11L,
                MasterDataAuditAction.EXPENSE_PRICE_REPLACE,
                7L,
                null,
                Map.of("unitPrice", 12),
                null,
                0L,
                null
        );

        assertNull(updateCommand.reason());
        assertNull(createCommand.reason());
    }

    @Test
    void rejectsBlankReasonForEveryAction() {
        assertThrows(IllegalArgumentException.class, () -> new MasterDataAuditRecordCommand(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_TYPE,
                10L,
                MasterDataAuditAction.EXPENSE_TYPE_CREATE,
                7L,
                null,
                Map.of("code", "MEAL"),
                null,
                0L,
                ""
        ));
        assertThrows(IllegalArgumentException.class, () -> new MasterDataAuditRecordCommand(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_TYPE,
                10L,
                MasterDataAuditAction.EXPENSE_TYPE_DEACTIVATE,
                7L,
                Map.of("active", true),
                Map.of("active", false),
                0L,
                1L,
                "   "
        ));
        assertThrows(IllegalArgumentException.class, () -> new MasterDataAuditRecordCommand(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_PRICE_SETTING,
                10L,
                MasterDataAuditAction.EXPENSE_PRICE_REPLACE,
                7L,
                null,
                Map.of("unitPrice", 12),
                null,
                0L,
                "\t "
        ));
    }

    @Test
    void delegatesReadMethods() {
        UUID operationId = UUID.randomUUID();
        when(auditLogRepository.findById(1L)).thenReturn(Optional.empty());
        when(auditLogRepository.findByOperationIdOrderByIdAsc(operationId))
                .thenReturn(java.util.List.of());
        when(auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                MasterDataAuditTargetType.EXPENSE_TYPE,
                10L
        )).thenReturn(java.util.List.of());

        assertEquals(Optional.empty(), service.findById(1L));
        assertEquals(java.util.List.of(), service.findByOperationId(operationId));
        assertEquals(java.util.List.of(), service.findByTarget(
                MasterDataAuditTargetType.EXPENSE_TYPE,
                10L
        ));
        verify(auditLogRepository).findById(1L);
        verify(auditLogRepository).findByOperationIdOrderByIdAsc(operationId);
    }
}
