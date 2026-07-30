package tw.com.jsgcpa.paymentapproval.approval.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.approval.entity.ApprovalHistory;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {
}
