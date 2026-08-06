package tw.com.jsgcpa.paymentapproval.master.admin.service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CloseExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ExpensePriceSettingVersionRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ReplaceExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.response.ExpensePriceSettingAdminResponse;
import tw.com.jsgcpa.paymentapproval.master.admin.exception.ExpensePriceSettingAdminBusinessException;
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
    private static final String PRICE_CODE_PATTERN = "^[A-Z][A-Z0-9_]*$";

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
            String requestedPriceCode,
            LocalDate date
    ) {
        ExpenseType type = findExpenseType(expenseTypeId);
        ensurePricedType(type);
        String priceCode = normalizePriceCode(requestedPriceCode);
        try {
            return toResponse(
                    priceResolver.resolve(expenseTypeId, priceCode, date),
                    date
            );
        } catch (PaymentDraftBusinessException exception) {
            if ("PRICE_SETTING_NOT_FOUND".equals(exception.getCode())) {
                throw notFound(
                        "EXPENSE_PRICE_SETTING_NOT_FOUND",
                        "No effective price setting found for expense type "
                                + expenseTypeId + " and price code " + priceCode
                );
            }
            throw conflict(
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
        rejectBackdate(request.effectiveFrom());
        String priceCode = normalizePriceCode(request.priceCode());
        rejectActiveOverlap(
                expenseTypeId,
                priceCode,
                request.effectiveFrom(),
                null,
                0L
        );

        ExpensePriceSetting setting = new ExpensePriceSetting();
        setting.setExpenseType(type);
        setting.setPriceCode(priceCode);
        setting.setPriceName(request.priceName());
        setting.setUnitPrice(request.amount());
        setting.setEffectiveFrom(request.effectiveFrom());
        setting.setEffectiveTo(null);
        setting.setActive(true);
        ExpensePriceSetting saved = save(setting);
        recordAudit(
                UUID.randomUUID(), saved, actorId,
                MasterDataAuditAction.EXPENSE_PRICE_CREATE,
                null, null, null
        );
        return toResponse(saved, today());
    }

    public ExpensePriceSettingAdminResponse replace(
            Long id,
            ReplaceExpensePriceSettingRequest request,
            Long actorId
    ) {
        ExpensePriceSetting setting = findPriceSetting(id);
        ExpenseType type = lockExpenseType(setting.getExpenseType().getId());
        setting = lockPriceSetting(id);
        ensurePricedType(type);
        validateVersion(setting.getVersion(), request.version());
        if (!Boolean.TRUE.equals(setting.getActive())) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_INACTIVE_REPLACE_FORBIDDEN",
                    "Only an active price setting can be replaced"
            );
        }
        rejectBackdate(request.effectiveFrom());
        if (!request.effectiveFrom().isAfter(setting.getEffectiveFrom())
                || (setting.getEffectiveTo() != null
                    && request.effectiveFrom().isAfter(setting.getEffectiveTo()))) {
            throw conflict(
                    "EXPENSE_PRICE_PERIOD_INVALID",
                    "Replacement effectiveFrom must be inside the current price period"
            );
        }

        LocalDate newEffectiveTo = request.effectiveFrom().minusDays(1);
        Map<String, Object> before = snapshot(setting);
        Long beforeVersion = setting.getVersion();
        UUID operationId = UUID.randomUUID();

        setting.setEffectiveTo(newEffectiveTo);
        ExpensePriceSetting closed = save(setting);
        recordAudit(
                operationId, closed, actorId,
                MasterDataAuditAction.EXPENSE_PRICE_REPLACE,
                before, beforeVersion, request.reason()
        );

        ExpensePriceSetting replacement = new ExpensePriceSetting();
        replacement.setExpenseType(type);
        replacement.setPriceCode(setting.getPriceCode());
        replacement.setPriceName(request.priceName());
        replacement.setUnitPrice(request.amount());
        replacement.setEffectiveFrom(request.effectiveFrom());
        replacement.setEffectiveTo(null);
        replacement.setActive(true);
        ExpensePriceSetting savedReplacement = save(replacement);
        recordAudit(
                operationId, savedReplacement, actorId,
                MasterDataAuditAction.EXPENSE_PRICE_REPLACE,
                null, null, request.reason()
        );
        return toResponse(savedReplacement, today());
    }

    public ExpensePriceSettingAdminResponse close(
            Long id,
            CloseExpensePriceSettingRequest request,
            Long actorId
    ) {
        ExpensePriceSetting setting = findPriceSetting(id);
        ExpenseType type = lockExpenseType(setting.getExpenseType().getId());
        setting = lockPriceSetting(id);
        ensurePricedType(type);
        validateVersion(setting.getVersion(), request.version());
        if (!Boolean.TRUE.equals(setting.getActive())) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_ALREADY_INACTIVE",
                    "Inactive price setting cannot be closed"
            );
        }
        if (request.effectiveTo().isBefore(setting.getEffectiveFrom())
                || request.effectiveTo().isBefore(today())) {
            throw conflict(
                    "EXPENSE_PRICE_PERIOD_INVALID",
                    "effectiveTo must not be before effectiveFrom or today"
            );
        }
        Map<String, Object> before = snapshot(setting);
        Long beforeVersion = setting.getVersion();
        setting.setEffectiveTo(request.effectiveTo());
        ExpensePriceSetting saved = save(setting);
        recordAudit(
                UUID.randomUUID(), saved, actorId,
                MasterDataAuditAction.EXPENSE_PRICE_REPLACE,
                before, beforeVersion, request.reason()
        );
        return toResponse(saved, today());
    }

    public ExpensePriceSettingAdminResponse activate(
            Long id,
            ExpensePriceSettingVersionRequest request,
            Long actorId
    ) {
        ExpensePriceSetting setting = findPriceSetting(id);
        ExpenseType type = lockExpenseType(setting.getExpenseType().getId());
        setting = lockPriceSetting(id);
        validateVersion(setting.getVersion(), request.version());
        if (Boolean.TRUE.equals(setting.getActive())) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_ALREADY_ACTIVE",
                    "Price setting is already active"
            );
        }
        ensurePricedType(type);
        rejectActiveOverlap(
                type.getId(), setting.getPriceCode(), setting.getEffectiveFrom(),
                setting.getEffectiveTo(), setting.getId()
        );
        Map<String, Object> before = snapshot(setting);
        Long beforeVersion = setting.getVersion();
        setting.setActive(true);
        ExpensePriceSetting saved = save(setting);
        recordAudit(
                UUID.randomUUID(), saved, actorId,
                MasterDataAuditAction.EXPENSE_PRICE_REPLACE,
                before, beforeVersion, null
        );
        return toResponse(saved, today());
    }

    public ExpensePriceSettingAdminResponse deactivate(
            Long id,
            DeactivateExpensePriceSettingRequest request,
            Long actorId
    ) {
        ExpensePriceSetting setting = findPriceSetting(id);
        ExpenseType type = lockExpenseType(setting.getExpenseType().getId());
        setting = lockPriceSetting(id);
        validateVersion(setting.getVersion(), request.version());
        if (!Boolean.TRUE.equals(setting.getActive())) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_ALREADY_INACTIVE",
                    "Price setting is already inactive"
            );
        }
        ensurePricedType(type);
        LocalDate today = today();
        Long targetId = setting.getId();
        if (Boolean.TRUE.equals(type.getActive()) && isEffectiveOn(setting, today)) {
            boolean hasOtherCurrentPrice = priceSettingRepository.findEffectivePrices(
                            type.getId(), today
                    ).stream()
                    .anyMatch(current -> !current.getId().equals(targetId));
            if (!hasOtherCurrentPrice) {
                throw conflict(
                        "EXPENSE_PRICE_CURRENT_REQUIRED",
                        "An active expense type must keep one current price"
                );
            }
        }
        Map<String, Object> before = snapshot(setting);
        Long beforeVersion = setting.getVersion();
        setting.setActive(false);
        ExpensePriceSetting saved = save(setting);
        recordAudit(
                UUID.randomUUID(), saved, actorId,
                MasterDataAuditAction.EXPENSE_PRICE_REPLACE,
                before, beforeVersion, request.reason()
        );
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

    private void rejectActiveOverlap(
            Long expenseTypeId,
            String priceCode,
            LocalDate from,
            LocalDate to,
            Long excludedId
    ) {
        List<ExpensePriceSetting> overlaps = priceSettingRepository
                .findOverlappingActivePeriods(
                        expenseTypeId, priceCode, from, to, excludedId
                );
        if (!overlaps.isEmpty()) {
            throw conflict(
                    "EXPENSE_PRICE_PERIOD_CONFLICT",
                    "Price setting effective period overlaps another setting"
            );
        }
    }

    private ExpensePriceSetting findPriceSetting(Long id) {
        return priceSettingRepository.findById(id == null ? -1L : id)
                .orElseThrow(() -> notFound(
                        "EXPENSE_PRICE_SETTING_NOT_FOUND",
                        "Price setting not found: " + id
                ));
    }

    private ExpensePriceSetting lockPriceSetting(Long id) {
        try {
            return priceSettingRepository.findByIdForUpdate(id == null ? -1L : id)
                    .orElseThrow(() -> notFound(
                            "EXPENSE_PRICE_SETTING_NOT_FOUND",
                            "Price setting not found: " + id
                    ));
        } catch (OptimisticLockingFailureException exception) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_VERSION_CONFLICT",
                    "Price setting version does not match"
            );
        }
    }

    private ExpenseType lockExpenseType(Long id) {
        return expenseTypeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> notFound(
                        "EXPENSE_TYPE_NOT_FOUND",
                        "Expense type not found: " + id
                ));
    }

    private ExpenseType findExpenseType(Long id) {
        return expenseTypeRepository.findById(id == null ? -1L : id)
                .orElseThrow(() -> notFound(
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

    private void rejectBackdate(LocalDate date) {
        if (date.isBefore(today())) {
            throw badRequest(
                    "EXPENSE_PRICE_BACKDATE_FORBIDDEN",
                    "effectiveFrom must not be before today"
            );
        }
    }

    private String normalizePriceCode(String value) {
        if (value == null) {
            throw badRequest(
                    "EXPENSE_PRICE_PRICE_CODE_INVALID",
                    "priceCode is required"
            );
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches(PRICE_CODE_PATTERN)) {
            throw badRequest(
                    "EXPENSE_PRICE_PRICE_CODE_INVALID",
                    "priceCode must contain only uppercase letters, digits, and underscores"
            );
        }
        return normalized;
    }

    private void validateVersion(Long current, Long expected) {
        if (!Objects.equals(current, expected)) {
            throw conflict(
                    "EXPENSE_PRICE_SETTING_VERSION_CONFLICT",
                    "Price setting version does not match"
            );
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private boolean isEffectiveOn(ExpensePriceSetting setting, LocalDate date) {
        return Boolean.TRUE.equals(setting.getActive())
                && !setting.getEffectiveFrom().isAfter(date)
                && (setting.getEffectiveTo() == null
                    || !setting.getEffectiveTo().isBefore(date));
    }

    private void recordAudit(
            UUID operationId,
            ExpensePriceSetting setting,
            Long actorId,
            MasterDataAuditAction action,
            Map<String, Object> before,
            Long beforeVersion,
            String reason
    ) {
        auditService.record(new MasterDataAuditRecordCommand(
                operationId,
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
        snapshot.put(
                "effectiveFrom",
                setting.getEffectiveFrom() == null
                        ? null : setting.getEffectiveFrom().toString()
        );
        snapshot.put(
                "effectiveTo",
                setting.getEffectiveTo() == null
                        ? null : setting.getEffectiveTo().toString()
        );
        snapshot.put("active", setting.getActive());
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
                setting.getId(), type.getId(), type.getCode(), type.getName(),
                setting.getPriceCode(), setting.getPriceName(), setting.getUnitPrice(),
                setting.getEffectiveFrom(), setting.getEffectiveTo(), setting.getActive(),
                setting.getVersion(), effective, setting.getCreatedAt(), setting.getUpdatedAt()
        );
    }

    private ExpensePriceSettingAdminBusinessException badRequest(
            String code, String message
    ) {
        return new ExpensePriceSettingAdminBusinessException(code, message);
    }

    private ExpensePriceSettingAdminBusinessException conflict(
            String code, String message
    ) {
        return new ExpensePriceSettingAdminBusinessException(code, message);
    }

    private ExpensePriceSettingAdminBusinessException notFound(
            String code, String message
    ) {
        return new ExpensePriceSettingAdminBusinessException(code, message);
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
