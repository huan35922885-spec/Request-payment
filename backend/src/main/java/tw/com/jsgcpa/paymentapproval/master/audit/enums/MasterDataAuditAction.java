package tw.com.jsgcpa.paymentapproval.master.audit.enums;

public enum MasterDataAuditAction {
    EXPENSE_TYPE_CREATE(MasterDataAuditTargetType.EXPENSE_TYPE),
    EXPENSE_TYPE_RENAME(MasterDataAuditTargetType.EXPENSE_TYPE),
    EXPENSE_TYPE_ACTIVATE(MasterDataAuditTargetType.EXPENSE_TYPE),
    EXPENSE_TYPE_DEACTIVATE(MasterDataAuditTargetType.EXPENSE_TYPE),
    EXPENSE_PRICE_CREATE(MasterDataAuditTargetType.EXPENSE_PRICE_SETTING),
    EXPENSE_PRICE_REPLACE(MasterDataAuditTargetType.EXPENSE_PRICE_SETTING);

    private final MasterDataAuditTargetType targetType;

    MasterDataAuditAction(MasterDataAuditTargetType targetType) {
        this.targetType = targetType;
    }

    public MasterDataAuditTargetType getTargetType() {
        return targetType;
    }
}
