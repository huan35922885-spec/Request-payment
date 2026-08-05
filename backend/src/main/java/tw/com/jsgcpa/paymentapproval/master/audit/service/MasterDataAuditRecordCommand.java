package tw.com.jsgcpa.paymentapproval.master.audit.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditAction;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditTargetType;

public record MasterDataAuditRecordCommand(
        UUID operationId,
        MasterDataAuditTargetType targetType,
        Long targetId,
        MasterDataAuditAction action,
        Long actorId,
        Map<String, Object> beforeData,
        Map<String, Object> afterData,
        Long beforeVersion,
        Long afterVersion,
        String reason
) {

    public MasterDataAuditRecordCommand {
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (targetId == null || targetId <= 0) {
            throw new IllegalArgumentException("targetId must be greater than zero");
        }
        if (actorId == null || actorId <= 0) {
            throw new IllegalArgumentException("actorId must be greater than zero");
        }
        if (action.getTargetType() != targetType) {
            throw new IllegalArgumentException("action and targetType do not match");
        }
        if (afterData == null) {
            throw new IllegalArgumentException("afterData must not be null");
        }
        if (afterVersion == null || afterVersion < 0) {
            throw new IllegalArgumentException("afterVersion must be non-negative");
        }
        if (beforeVersion != null && beforeVersion < 0) {
            throw new IllegalArgumentException("beforeVersion must be non-negative");
        }
        if ((beforeData == null) != (beforeVersion == null)) {
            throw new IllegalArgumentException("beforeData and beforeVersion must be supplied together");
        }

        reason = normalizeReason(reason);
        validateReasonRequirement(action, reason);
        validateSnapshotShape(action, beforeData, beforeVersion, afterVersion);
        beforeData = immutableCopy(beforeData);
        afterData = immutableCopy(afterData);
    }

    private static String normalizeReason(String value) {
        return value == null ? null : value.trim();
    }

    private static void validateReasonRequirement(MasterDataAuditAction action, String reason) {
        if ((action == MasterDataAuditAction.EXPENSE_TYPE_DEACTIVATE
                || action == MasterDataAuditAction.EXPENSE_PRICE_REPLACE)
                && (reason == null || reason.isEmpty())) {
            throw new IllegalArgumentException("reason is required for " + action);
        }
    }

    private static void validateSnapshotShape(
            MasterDataAuditAction action,
            Map<String, Object> beforeData,
            Long beforeVersion,
            Long afterVersion
    ) {
        if (action == MasterDataAuditAction.EXPENSE_TYPE_CREATE
                || action == MasterDataAuditAction.EXPENSE_PRICE_CREATE) {
            if (beforeData != null || beforeVersion != null || afterVersion != 0) {
                throw new IllegalArgumentException("create audit snapshot has an invalid shape");
            }
            return;
        }

        if (action == MasterDataAuditAction.EXPENSE_PRICE_REPLACE) {
            if (beforeData == null && beforeVersion == null && afterVersion == 0) {
                return;
            }
            if (beforeData != null
                    && beforeVersion != null
                    && afterVersion == beforeVersion + 1) {
                return;
            }
            throw new IllegalArgumentException("price replacement audit snapshot has an invalid shape");
        }

        if (beforeData == null || beforeVersion == null || afterVersion != beforeVersion + 1) {
            throw new IllegalArgumentException("update audit snapshot has an invalid shape");
        }
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> value) {
        return value == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
