package tw.com.jsgcpa.paymentapproval.master.admin.service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ExpensePriceSettingVersionRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.UpdateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.response.ExpensePriceSettingAdminResponse;
import tw.com.jsgcpa.paymentapproval.master.admin.exception.ExpenseTypeAdminBusinessException;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditAction;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditTargetType;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditRecordCommand;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditService;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpensePriceSettingRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.service.ExpensePriceResolver;

@Service
@Transactional
public class ExpensePriceSettingAdminService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final ExpensePriceSettingRepository priceSettingRepository;
    private final ExpenseTypeRepository expenseTypeRepository;
    private final MasterDataAuditService auditService;
    private final ExpensePriceResolver priceResolver;
    private final Clock clock;

    @Autowired
    public ExpensePriceSettingAdminService(
            ExpensePriceSettingRepository priceSettingRepository,
            ExpenseTypeRepository expenseTypeRepository,
            MasterDataAuditService auditService,
            ExpensePriceResolver priceResolver
    ) {
        this(
                priceSettingRepository,
                expenseTypeRepository,
                auditService,
                priceResolver,
                Clock.system(BUSINESS_ZONE)
        );
    }

    ExpensePriceSettingAdminService(
            ExpensePriceSettingRepository priceSettingRepository,
            ExpenseTypeRepository expenseTypeRepository,
            MasterDataAuditService auditService,
            ExpensePriceResolver priceResolver,
            Clock clock
    ) {
        this.priceSettingRepository = priceSettingRepository;
        this.expenseTypeRepository = expenseTypeRepository;
        this.auditService = auditService;
        this.priceResolver = priceResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ExpensePriceSettingAdminResponse> list(
            Long expenseTypeId,
            Boolean active,
            LocalDate effectiveOn
    ) {
        List<ExpensePriceSetting> settings;
        if (expenseTypeId == null && active == null && effectiveOn == null) {
            settings = priceSettingRepository.findAdminAll();
        } else if (expenseTypeId != null && active == null && effectiveOn == null) {
            settings = priceSettingRepository.findAdminByExpenseTypeId(expenseTypeId);
        } else if (expenseTypeId == null && active != null && effectiveOn == null) {
            settings = priceSettingRepository.findAdminByActive(active);
        } else if (expenseTypeId == null && active == null) {
            settings = priceSettingRepository.findAdminByEffectiveOn(effectiveOn);
        } else if (expenseTypeId != null && active != null && effectiveOn == null) {
            settings = priceSettingRepository.findAdminByExpenseTypeIdAndActive(
                    expenseTypeId, active
            );
        } else if (expenseTypeId != null && active == null) {
            settings = priceSettingRepository.findAdminByExpenseTypeIdAndEffectiveOn(
                    expenseTypeId, effectiveOn
            );
        } else if (expenseTypeId == null) {
            settings = priceSettingRepository.findAdminByActiveAndEffectiveOn(
                    active, effectiveOn
            );
        } else {
            settings = priceSettingRepository.findAdminByExpenseTypeIdAndActiveAndEffectiveOn(
                    expenseTypeId, active, effectiveOn
            );
        }

        LocalDate effectiveDate = effectiveOn == null ? today() : effectiveOn;
        return settings.stream()
                .map(setting -> toResponse(setting, effectiveDate))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExpensePriceSettingAdminResponse> listForExpenseType(Long expenseTypeId) {
        findExpenseType(expenseTypeId);
        LocalDate today = today();
        return priceSettingRepository.findByExpenseType_IdOrderByEffectiveFromDescIdDesc(
                        expenseTypeId
                ).stream()
                .map(setting -> toResponse(setting, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpensePriceSettingAdminResponse effective(
            Long expenseTypeId,
            LocalDate date
    ) {
        ExpenseType type = findExpenseType(expenseTypeId);
        ensurePricedType(type);
        try {
            ExpensePriceSetting setting = priceResolver.resolve(
                    expenseTypeId,
                    "DEFAULT",
                    date
            );
            return toResponse(setting, date);
        } catch (PaymentDraftBusinessException exception) {
            if ("PRICE_SETTING_NOT_FOUND".equals(exception.getCode())) {
                throw business(
                        "EXPENSE_PRICE_SETTING_NOT_FOUND",
                        "No effective price setting found for expense type: " + expenseTypeId
                );
            }
            throw business(
                    "EXPENSE_PRICE_DATABASE_CONFLICT",
                    exception.getMessage()
            );
        }
    }

    public ExpensePriceSettingAdminResponse create(
            Long expenseTypeId,
            CreateExpensePriceSettingRequest request,
            Long actorId
    ) {
        ExpenseType type = lockExpenseType(expenseTypeId);
        ensurePricedType(type);
        validateDateRange(request.effectiveFrom(), request.effectiveTo());
        String priceCode = normalizePriceCode(request.priceCode());
        rejectOverlap(
                expenseTypeId,
                priceCode,
                request.effectiveFrom(),
                request.effectiveTo(),
                0L,
                false
        );

        ExpensePriceSetting setting = new ExpensePriceSetting();
        setting.setExpenseType(type);
        setting.setPriceCode(priceCode);
        setting.setPriceName(priceCode);
        setting.setUnitPrice(request.amount());
        setting.setEffectiveFrom(request.effectiveFrom());
        setting.setEffectiveTo(request.effectiveTo());
        setting.setActive(false);
        ExpensePriceSetting saved = save(setting);
        recordAudit(saved, actorId, MasterDataAuditAction.EXPENSE_PRICE_CREATE,
                null, null, null);
        return toResponse(saved, today());
    }

    public ExpensePriceSettingAdminResponse update(
            Long id,
            UpdateExpensePriceSettingRequest request,
            Long actorId
    ) {
        ExpensePriceSetting setting = lockPriceSetting(id);
        ExpenseType type = lockExpenseType(setting.getExpenseType().getId());
        if (Boolean.TRUE.equals(setting.getActive())) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_ACTIVE_EDIT_FORBIDDEN",
                    "Active price setting cannot be edited"
            );
        }
        validateVersion(setting.getVersion(), request.version());
        validateDateRange(request.effectiveFrom(), request.effectiveTo());
        if (setting.getUnitPrice().compareTo(request.amount()) == 0
                && setting.getEffectiveFrom().equals(request.effectiveFrom())
                && java.util.Objects.equals(
                        setting.getEffectiveTo(), request.effectiveTo()
                )) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_UNCHANGED",
                    "Price setting is unchanged"
            );
        }
        rejectOverlap(
                type.getId(), setting.getPriceCode(), request.effectiveFrom(),
                request.effectiveTo(), setting.getId(), false
        );
        Map<String, Object> before = snapshot(setting);
        Long beforeVersion = setting.getVersion();
        setting.setUnitPrice(request.amount());
        setting.setEffectiveFrom(request.effectiveFrom());
        setting.setEffectiveTo(request.effectiveTo());
        ExpensePriceSetting saved = save(setting);
        recordAudit(saved, actorId, MasterDataAuditAction.EXPENSE_PRICE_REPLACE,
                before, beforeVersion, null);
        return toResponse(saved, today());
    }

    public ExpensePriceSettingAdminResponse activate(
            Long id,
            ExpensePriceSettingVersionRequest request,
            Long actorId
    ) {
        ExpensePriceSetting setting = lockPriceSetting(id);
        ExpenseType type = lockExpenseType(setting.getExpenseType().getId());
        validateVersion(setting.getVersion(), request.version());
        if (Boolean.TRUE.equals(setting.getActive())) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_ALREADY_ACTIVE",
                    "Price setting is already active"
            );
        }
        ensurePricedType(type);
        rejectOverlap(
                type.getId(), setting.getPriceCode(), setting.getEffectiveFrom(),
                setting.getEffectiveTo(), setting.getId(), true
        );
        Map<String, Object> before = snapshot(setting);
        Long beforeVersion = setting.getVersion();
        setting.setActive(true);
        ExpensePriceSetting saved = save(setting);
        recordAudit(saved, actorId, MasterDataAuditAction.EXPENSE_PRICE_REPLACE,
                before, beforeVersion, null);
        return toResponse(saved, today());
    }

    public ExpensePriceSettingAdminResponse deactivate(
            Long id,
            DeactivateExpensePriceSettingRequest request,
            Long actorId
    ) {
        ExpensePriceSetting setting = lockPriceSetting(id);
        ExpenseType type = lockExpenseType(setting.getExpenseType().getId());
        validateVersion(setting.getVersion(), request.version());
        if (!Boolean.TRUE.equals(setting.getActive())) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_ALREADY_INACTIVE",
                    "Price setting is already inactive"
            );
        }
        ensurePricedType(type);
        LocalDate today = today();
        List<ExpensePriceSetting> current =
                priceSettingRepository.findEffectivePriceSettings(
                        type.getId(), setting.getPriceCode(), today
                );
        if (current.size() == 1 && current.get(0).getId().equals(setting.getId())) {
            throw conflict(
                    "EXPENSE_PRICE_CURRENT_REQUIRED",
                    "A current effective price is required"
            );
        }
        Map<String, Object> before = snapshot(setting);
        Long beforeVersion = setting.getVersion();
        setting.setActive(false);
        ExpensePriceSetting saved = save(setting);
        recordAudit(saved, actorId, MasterDataAuditAction.EXPENSE_PRICE_REPLACE,
                before, beforeVersion, request.reason());
        return toResponse(saved, today);
    }

    private ExpensePriceSetting save(ExpensePriceSetting setting) {
        try {
            return priceSettingRepository.saveAndFlush(setting);
        } catch (OptimisticLockingFailureException exception) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_VERSION_CONFLICT",
                    "Price setting version does not match"
            );
        } catch (DataIntegrityViolationException exception) {
            if (hasSqlState(exception, "23P01")) {
                throw conflict(
                        "EXPENSE_PRICE_PERIOD_CONFLICT",
                        "Price setting effective period overlaps another setting"
                );
            }
            if (hasSqlState(exception, "23505")) {
                throw conflict(
                        "EXPENSE_PRICE_DATABASE_CONFLICT",
                        "Price setting conflicts with existing data"
                );
            }
            throw exception;
        }
    }

    private void rejectOverlap(
            Long expenseTypeId,
            String priceCode,
            LocalDate from,
            LocalDate to,
            Long excludedId,
            boolean activeOnly
    ) {
        List<ExpensePriceSetting> overlaps = activeOnly
                ? priceSettingRepository.findOverlappingActivePeriods(
                        expenseTypeId, priceCode, from, to, excludedId
                )
                : priceSettingRepository.findOverlappingPeriods(
                        expenseTypeId, priceCode, from, to, excludedId
                );
        if (!overlaps.isEmpty()) {
            throw conflict(
                    "EXPENSE_PRICE_PERIOD_CONFLICT",
                    "Price setting effective period overlaps another setting"
            );
        }
    }

    private ExpensePriceSetting lockPriceSetting(Long id) {
        ExpensePriceSetting setting = priceSettingRepository.findById(id == null ? -1L : id)
                .orElseThrow(() -> business(
                        "EXPENSE_PRICE_SETTING_NOT_FOUND",
                        "Price setting not found: " + id
                ));
        return setting;
    }

    private ExpenseType lockExpenseType(Long id) {
        return expenseTypeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> business(
                        "EXPENSE_TYPE_NOT_FOUND",
                        "Expense type not found: " + id
                ));
    }

    private ExpenseType findExpenseType(Long id) {
        return expenseTypeRepository.findById(id == null ? -1L : id)
                .orElseThrow(() -> business(
                        "EXPENSE_TYPE_NOT_FOUND",
                        "Expense type not found: " + id
                ));
    }

    private void ensurePricedType(ExpenseType type) {
        if (!requiresPrice(type.getCalculationType())) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_UNSUPPORTED",
                    "Expense type does not support price settings"
            );
        }
    }

    private boolean requiresPrice(CalculationType calculationType) {
        return calculationType == CalculationType.MEAL
                || calculationType == CalculationType.QUANTITY_PRICE
                || calculationType == CalculationType.CONFIRMATION;
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (to != null && to.isBefore(from)) {
            throw conflict(
                    "EXPENSE_PRICE_PERIOD_INVALID",
                    "effectiveTo must not be before effectiveFrom"
            );
        }
    }

    private void validateVersion(Long current, Long expected) {
        if (!current.equals(expected)) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_VERSION_CONFLICT",
                    "Price setting version does not match"
            );
        }
    }

    private String normalizePriceCode(String value) {
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private void recordAudit(
            ExpensePriceSetting setting,
            Long actorId,
            MasterDataAuditAction action,
            Map<String, Object> before,
            Long beforeVersion,
            String reason
    ) {
        auditService.record(new MasterDataAuditRecordCommand(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_PRICE_SETTING,
                setting.getId(),
                action,
                actorId,
                before,
                snapshot(setting),
                beforeVersion,
                setting.getVersion(),
                reason
        ));
    }

    private Map<String, Object> snapshot(ExpensePriceSetting setting) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", setting.getId());
        snapshot.put("expenseTypeId", setting.getExpenseType().getId());
        snapshot.put("expenseTypeCode", setting.getExpenseType().getCode());
        snapshot.put("priceCode", setting.getPriceCode());
        snapshot.put("priceName", setting.getPriceName());
        snapshot.put("amount", setting.getUnitPrice());
        // Audit JSONB is serialized by Hibernate's JSON mapper. Store dates
        // as ISO strings so this remains independent of mapper JSR-310 setup.
        snapshot.put(
                "effectiveFrom",
                setting.getEffectiveFrom() == null
                        ? null
                        : setting.getEffectiveFrom().toString()
        );
        snapshot.put(
                "effectiveTo",
                setting.getEffectiveTo() == null
                        ? null
                        : setting.getEffectiveTo().toString()
        );
        snapshot.put("active", setting.getActive());
        snapshot.put("version", setting.getVersion());
        return snapshot;
    }

    private ExpensePriceSettingAdminResponse toResponse(
            ExpensePriceSetting setting,
            LocalDate effectiveDate
    ) {
        boolean effective = Boolean.TRUE.equals(setting.getActive())
                && !setting.getEffectiveFrom().isAfter(effectiveDate)
                && (setting.getEffectiveTo() == null
                    || !setting.getEffectiveTo().isBefore(effectiveDate));
        ExpenseType type = setting.getExpenseType();
        return new ExpensePriceSettingAdminResponse(
                setting.getId(),
                type.getId(),
                type.getCode(),
                type.getName(),
                setting.getPriceCode(),
                setting.getPriceName(),
                setting.getUnitPrice(),
                setting.getEffectiveFrom(),
                setting.getEffectiveTo(),
                setting.getActive(),
                setting.getVersion(),
                effective,
                setting.getCreatedAt(),
                setting.getUpdatedAt()
        );
    }

    private ExpenseTypeAdminBusinessException conflict(String code, String message) {
        return new ExpenseTypeAdminBusinessException(code, message);
    }

    private ExpenseTypeAdminBusinessException business(String code, String message) {
        return new ExpenseTypeAdminBusinessException(code, message);
    }

    private boolean hasSqlState(Throwable failure, String expectedSqlState) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && expectedSqlState.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
