package tw.com.jsgcpa.paymentapproval.approval.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.approval.entity.ApprovalHistory;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {

    List<ApprovalHistory> findByPaymentRequest_IdOrderByActedAtAscIdAsc(
            Long paymentRequestId
    );
}
