package tw.com.jsgcpa.paymentapproval.master.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditAction;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditTargetType;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;

@Entity
@Table(name = "master_data_audit_logs")
public class MasterDataAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40, updatable = false)
    private MasterDataAuditTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50, updatable = false)
    private MasterDataAuditAction action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private AppUser actor;

    @Column(name = "actor_username_snapshot", nullable = false, length = 100, updatable = false)
    private String actorUsernameSnapshot;

    @Column(name = "actor_display_name_snapshot", nullable = false, length = 100, updatable = false)
    private String actorDisplayNameSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> afterData;

    @Column(name = "before_version", updatable = false)
    private Long beforeVersion;

    @Column(name = "after_version", nullable = false, updatable = false)
    private Long afterVersion;

    @Column(name = "reason", columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public MasterDataAuditLog() {
    }

    private MasterDataAuditLog(
            UUID operationId,
            MasterDataAuditTargetType targetType,
            Long targetId,
            MasterDataAuditAction action,
            AppUser actor,
            String actorUsernameSnapshot,
            String actorDisplayNameSnapshot,
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            Long beforeVersion,
            Long afterVersion,
            String reason
    ) {
        this.operationId = operationId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.action = action;
        this.actor = actor;
        this.actorUsernameSnapshot = actorUsernameSnapshot;
        this.actorDisplayNameSnapshot = actorDisplayNameSnapshot;
        this.beforeData = copy(beforeData);
        this.afterData = copy(afterData);
        this.beforeVersion = beforeVersion;
        this.afterVersion = afterVersion;
        this.reason = reason;
    }

    public static MasterDataAuditLog create(
            UUID operationId,
            MasterDataAuditTargetType targetType,
            Long targetId,
            MasterDataAuditAction action,
            AppUser actor,
            String actorUsernameSnapshot,
            String actorDisplayNameSnapshot,
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            Long beforeVersion,
            Long afterVersion,
            String reason
    ) {
        return new MasterDataAuditLog(
                operationId,
                targetType,
                targetId,
                action,
                actor,
                actorUsernameSnapshot,
                actorDisplayNameSnapshot,
                beforeData,
                afterData,
                beforeVersion,
                afterVersion,
                reason
        );
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    private static Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }

    public Long getId() {
        return id;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public MasterDataAuditTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public MasterDataAuditAction getAction() {
        return action;
    }

    public AppUser getActor() {
        return actor;
    }

    public String getActorUsernameSnapshot() {
        return actorUsernameSnapshot;
    }

    public String getActorDisplayNameSnapshot() {
        return actorDisplayNameSnapshot;
    }

    public Map<String, Object> getBeforeData() {
        return beforeData == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(beforeData));
    }

    public Map<String, Object> getAfterData() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(afterData));
    }

    public Long getBeforeVersion() {
        return beforeVersion;
    }

    public Long getAfterVersion() {
        return afterVersion;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
