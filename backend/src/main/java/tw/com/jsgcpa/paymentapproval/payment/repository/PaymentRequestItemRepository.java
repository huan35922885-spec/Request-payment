package tw.com.jsgcpa.paymentapproval.payment.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestItem;

public interface PaymentRequestItemRepository extends JpaRepository<PaymentRequestItem, Long> {

    List<PaymentRequestItem> findByPaymentRequest_IdOrderBySortOrderAscIdAsc(
            Long paymentRequestId
    );
}
