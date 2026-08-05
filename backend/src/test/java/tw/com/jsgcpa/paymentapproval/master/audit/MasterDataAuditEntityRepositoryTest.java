package tw.com.jsgcpa.paymentapproval.master.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tw.com.jsgcpa.paymentapproval.common.entity.BaseTimeEntity;
import tw.com.jsgcpa.paymentapproval.master.audit.entity.MasterDataAuditLog;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditAction;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditTargetType;
import tw.com.jsgcpa.paymentapproval.master.audit.repository.MasterDataAuditLogRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;

@SpringBootTest
class MasterDataAuditEntityRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private MasterDataAuditLogRepository auditLogRepository;

    @Test
    void mapsAuditEntityAsAppendOnlyWithoutVersionOrBaseTimeInheritance() throws Exception {
        assertFalse(BaseTimeEntity.class.isAssignableFrom(MasterDataAuditLog.class));
        assertThrows(NoSuchFieldException.class,
                () -> MasterDataAuditLog.class.getDeclaredField("version"));
        assertThrows(NoSuchMethodException.class,
                () -> MasterDataAuditLog.class.getDeclaredMethod("setReason", String.class));
        assertEquals(EnumType.STRING,
                MasterDataAuditLog.class.getDeclaredField("action")
                        .getAnnotation(Enumerated.class).value());
        assertEquals(EnumType.STRING,
                MasterDataAuditLog.class.getDeclaredField("targetType")
                        .getAnnotation(Enumerated.class).value());

        Field actor = MasterDataAuditLog.class.getDeclaredField("actor");
        ManyToOne manyToOne = actor.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = actor.getAnnotation(JoinColumn.class);
        assertEquals(FetchType.LAZY, manyToOne.fetch());
        assertEquals(0, manyToOne.cascade().length);
        assertEquals("actor_id", joinColumn.name());
        assertFalse(joinColumn.updatable());
        assertFalse(joinColumn.nullable());

        assertEquals(SqlTypes.JSON,
                MasterDataAuditLog.class.getDeclaredField("beforeData")
                        .getAnnotation(JdbcTypeCode.class).value());
        assertEquals(SqlTypes.JSON,
                MasterDataAuditLog.class.getDeclaredField("afterData")
                        .getAnnotation(JdbcTypeCode.class).value());
    }

    @Test
    @Transactional
    void persistsJsonSnapshotsActorAndCreatedAtThroughRepository() {
        AppUser actor = new AppUser();
        actor.setUsername("audit-entity-" + UUID.randomUUID());
        actor.setDisplayName("Audit Entity Actor");
        AppUser savedActor = appUserRepository.saveAndFlush(actor);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("effectiveTo", null);
        before.put("active", true);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("effectiveTo", "2026-12-31");
        after.put("active", false);

        MasterDataAuditLog audit = MasterDataAuditLog.create(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_PRICE_SETTING,
                901L,
                MasterDataAuditAction.EXPENSE_PRICE_REPLACE,
                savedActor,
                savedActor.getUsername(),
                savedActor.getDisplayName(),
                before,
                after,
                0L,
                1L,
                "  replace reason  "
        );
        MasterDataAuditLog saved = auditLogRepository.save(audit);
        entityManager.flush();
        entityManager.clear();

        MasterDataAuditLog loaded = auditLogRepository.findById(saved.getId()).orElseThrow();
        assertNotNull(loaded.getCreatedAt());
        assertTrue(loaded.getCreatedAt().isBefore(OffsetDateTime.now().plusSeconds(1)));
        assertEquals(savedActor.getId(), loaded.getActor().getId());
        assertEquals(before, loaded.getBeforeData());
        assertEquals(after, loaded.getAfterData());
        assertEquals(0L, loaded.getBeforeVersion());
        assertEquals(1L, loaded.getAfterVersion());
        assertEquals("  replace reason  ", loaded.getReason());
        assertThrows(UnsupportedOperationException.class,
                () -> loaded.getAfterData().put("new", "value"));
    }

    @Test
    void repositoryDoesNotExposeDeleteOrUpdateMethods() {
        for (Method method : MasterDataAuditLogRepository.class.getMethods()) {
            assertFalse(method.getName().equals("delete")
                    || method.getName().equals("deleteById")
                    || method.getName().equals("deleteAll"));
        }
    }
}
