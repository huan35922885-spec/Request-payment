package tw.com.jsgcpa.paymentapproval.master.audit.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tw.com.jsgcpa.paymentapproval.master.audit.entity.MasterDataAuditLog;
import tw.com.jsgcpa.paymentapproval.master.audit.repository.MasterDataAuditLogRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.repository.AppUserRepository;

@Service
public class MasterDataAuditService {

    private final MasterDataAuditLogRepository auditLogRepository;
    private final AppUserRepository appUserRepository;

    public MasterDataAuditService(
            MasterDataAuditLogRepository auditLogRepository,
            AppUserRepository appUserRepository
    ) {
        this.auditLogRepository = auditLogRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MasterDataAuditLog record(MasterDataAuditRecordCommand command) {
        AppUser actor = appUserRepository.findById(command.actorId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Audit actor not found: " + command.actorId()
                ));

        return auditLogRepository.save(
                MasterDataAuditLog.create(
                        command.operationId(),
                        command.targetType(),
                        command.targetId(),
                        command.action(),
                        actor,
                        actor.getUsername(),
                        actor.getDisplayName(),
                        command.beforeData(),
                        command.afterData(),
                        command.beforeVersion(),
                        command.afterVersion(),
                        command.reason()
                )
        );
    }

    @Transactional(readOnly = true)
    public Optional<MasterDataAuditLog> findById(Long id) {
        return auditLogRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<MasterDataAuditLog> findByOperationId(UUID operationId) {
        return auditLogRepository.findByOperationIdOrderByIdAsc(operationId);
    }

    @Transactional(readOnly = true)
    public List<MasterDataAuditLog> findByTarget(
            tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditTargetType targetType,
            Long targetId
    ) {
        return auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
                targetType,
                targetId
        );
    }
}
