package tw.com.jsgcpa.paymentapproval.master.admin.service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ExpenseTypeVersionRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.RenameExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.response.ExpenseTypeAdminResponse;
import tw.com.jsgcpa.paymentapproval.master.admin.exception.ExpenseTypeAdminBusinessException;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditAction;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditTargetType;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditRecordCommand;
import tw.com.jsgcpa.paymentapproval.master.audit.service.MasterDataAuditService;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpensePriceSettingRepository;
import tw.com.jsgcpa.paymentapproval.master.repository.ExpenseTypeRepository;

@Service
@Transactional
public class ExpenseTypeAdminService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final ExpenseTypeRepository expenseTypeRepository;
    private final ExpensePriceSettingRepository expensePriceSettingRepository;
    private final MasterDataAuditService auditService;
    private final Clock clock;

    @Autowired
    public ExpenseTypeAdminService(
            ExpenseTypeRepository expenseTypeRepository,
            ExpensePriceSettingRepository expensePriceSettingRepository,
            MasterDataAuditService auditService
    ) {
        this(
                expenseTypeRepository,
                expensePriceSettingRepository,
                auditService,
                Clock.system(BUSINESS_ZONE)
        );
    }

    ExpenseTypeAdminService(
            ExpenseTypeRepository expenseTypeRepository,
            ExpensePriceSettingRepository expensePriceSettingRepository,
            MasterDataAuditService auditService,
            Clock clock
    ) {
        this.expenseTypeRepository = expenseTypeRepository;
        this.expensePriceSettingRepository = expensePriceSettingRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ExpenseTypeAdminResponse> list() {
        return expenseTypeRepository.findAllByOrderByCodeAscIdAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ExpenseTypeAdminResponse create(
            CreateExpenseTypeRequest request,
            Long actorId
    ) {
        if (expenseTypeRepository.existsByCode(request.code())) {
            throw conflict(
                    "EXPENSE_TYPE_CODE_CONFLICT",
                    "Expense type code already exists"
            );
        }

        ExpenseType expenseType = new ExpenseType();
        expenseType.setCode(request.code());
        expenseType.setName(request.name());
        expenseType.setCalculationType(request.calculationType());
        expenseType.setActive(false);

        ExpenseType saved;
        try {
            saved = expenseTypeRepository.saveAndFlush(expenseType);
        } catch (DataIntegrityViolationException exception) {
            if (hasSqlState(exception, "23505")) {
                throw conflict(
                        "EXPENSE_TYPE_CODE_CONFLICT",
                        "Expense type code already exists"
                );
            }
            throw exception;
        }

        recordAudit(
                saved,
                actorId,
                MasterDataAuditAction.EXPENSE_TYPE_CREATE,
                null,
                null,
                null
        );
        return toResponse(saved);
    }

    public ExpenseTypeAdminResponse rename(
            Long id,
            RenameExpenseTypeRequest request,
            Long actorId
    ) {
        ExpenseType expenseType = findExpenseTypeForWrite(id);
        validateVersion(expenseType, request.version());
        if (expenseType.getName().equals(request.name())) {
            throw conflict(
                    "EXPENSE_TYPE_NAME_UNCHANGED",
                    "Expense type name is unchanged"
            );
        }

        Map<String, Object> before = snapshot(expenseType);
        Long beforeVersion = expenseType.getVersion();
        expenseType.setName(request.name());
        saveWithOptimisticLock(expenseType);
        recordAudit(
                expenseType,
                actorId,
                MasterDataAuditAction.EXPENSE_TYPE_RENAME,
                before,
                beforeVersion,
                null
        );
        return toResponse(expenseType);
    }

    public ExpenseTypeAdminResponse activate(
            Long id,
            ExpenseTypeVersionRequest request,
            Long actorId
    ) {
        ExpenseType expenseType = findExpenseTypeForWrite(id);
        validateVersion(expenseType, request.version());
        if (Boolean.TRUE.equals(expenseType.getActive())) {
            throw conflict(
                    "EXPENSE_TYPE_ALREADY_ACTIVE",
                    "Expense type is already active"
            );
        }

        if (requiresCurrentPrice(expenseType.getCalculationType())
                && expensePriceSettingRepository.findEffectivePrices(
                        id,
                        LocalDate.now(clock)
                ).isEmpty()) {
            throw conflict(
                    "EXPENSE_TYPE_CURRENT_PRICE_REQUIRED",
                    "A current effective price is required before activation"
            );
        }

        Map<String, Object> before = snapshot(expenseType);
        Long beforeVersion = expenseType.getVersion();
        expenseType.setActive(true);
        saveWithOptimisticLock(expenseType);
        recordAudit(
                expenseType,
                actorId,
                MasterDataAuditAction.EXPENSE_TYPE_ACTIVATE,
                before,
                beforeVersion,
                null
        );
        return toResponse(expenseType);
    }

    public ExpenseTypeAdminResponse deactivate(
            Long id,
            DeactivateExpenseTypeRequest request,
            Long actorId
    ) {
        ExpenseType expenseType = findExpenseTypeForWrite(id);
        validateVersion(expenseType, request.version());
        if (!Boolean.TRUE.equals(expenseType.getActive())) {
            throw conflict(
                    "EXPENSE_TYPE_ALREADY_INACTIVE",
                    "Expense type is already inactive"
            );
        }

        Map<String, Object> before = snapshot(expenseType);
        Long beforeVersion = expenseType.getVersion();
        expenseType.setActive(false);
        saveWithOptimisticLock(expenseType);
        recordAudit(
                expenseType,
                actorId,
                MasterDataAuditAction.EXPENSE_TYPE_DEACTIVATE,
                before,
                beforeVersion,
                request.reason()
        );
        return toResponse(expenseType);
    }

    private ExpenseType findExpenseType(Long id) {
        return expenseTypeRepository.findById(id == null ? -1L : id)
                .orElseThrow(() -> new ExpenseTypeAdminBusinessException(
                        "EXPENSE_TYPE_NOT_FOUND",
                        "Expense type not found: " + id
                ));
    }

    private ExpenseType findExpenseTypeForWrite(Long id) {
        return expenseTypeRepository.findByIdForUpdate(id == null ? -1L : id)
                .orElseThrow(() -> conflict(
                        "EXPENSE_TYPE_NOT_FOUND",
                        "Expense type not found: " + id
                ));
    }

    private void validateVersion(ExpenseType expenseType, Long requestVersion) {
        if (!expenseType.getVersion().equals(requestVersion)) {
            throw conflict(
                    "EXPENSE_TYPE_VERSION_CONFLICT",
                    "Expense type version does not match"
            );
        }
    }

    private void saveWithOptimisticLock(ExpenseType expenseType) {
        try {
            expenseTypeRepository.saveAndFlush(expenseType);
        } catch (OptimisticLockingFailureException exception) {
            throw conflict(
                    "EXPENSE_TYPE_VERSION_CONFLICT",
                    "Expense type version does not match"
            );
        }
    }

    private void recordAudit(
            ExpenseType expenseType,
            Long actorId,
            MasterDataAuditAction action,
            Map<String, Object> before,
            Long beforeVersion,
            String reason
    ) {
        auditService.record(new MasterDataAuditRecordCommand(
                UUID.randomUUID(),
                MasterDataAuditTargetType.EXPENSE_TYPE,
                expenseType.getId(),
                action,
                actorId,
                before,
                snapshot(expenseType),
                beforeVersion,
                expenseType.getVersion(),
                reason
        ));
    }

    private Map<String, Object> snapshot(ExpenseType expenseType) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", expenseType.getId());
        snapshot.put("code", expenseType.getCode());
        snapshot.put("name", expenseType.getName());
        snapshot.put("calculationType", expenseType.getCalculationType().name());
        snapshot.put("active", expenseType.getActive());
        return snapshot;
    }

    private boolean requiresCurrentPrice(CalculationType calculationType) {
        return calculationType == CalculationType.MEAL
                || calculationType == CalculationType.QUANTITY_PRICE
                || calculationType == CalculationType.CONFIRMATION;
    }

    private ExpenseTypeAdminBusinessException conflict(String code, String message) {
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

    private ExpenseTypeAdminResponse toResponse(ExpenseType expenseType) {
        return new ExpenseTypeAdminResponse(
                expenseType.getId(),
                expenseType.getCode(),
                expenseType.getName(),
                expenseType.getCalculationType(),
                expenseType.getActive(),
                expenseType.getVersion(),
                expenseType.getCreatedAt(),
                expenseType.getUpdatedAt()
        );
    }
}
