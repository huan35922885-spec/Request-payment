package tw.com.jsgcpa.paymentapproval.master.audit.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import tw.com.jsgcpa.paymentapproval.master.audit.entity.MasterDataAuditLog;
import tw.com.jsgcpa.paymentapproval.master.audit.enums.MasterDataAuditTargetType;

public interface MasterDataAuditLogRepository extends Repository<MasterDataAuditLog, Long> {

    MasterDataAuditLog save(MasterDataAuditLog auditLog);

    Optional<MasterDataAuditLog> findById(Long id);

    List<MasterDataAuditLog> findByOperationIdOrderByIdAsc(UUID operationId);

    List<MasterDataAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(
            MasterDataAuditTargetType targetType,
            Long targetId
    );
}
