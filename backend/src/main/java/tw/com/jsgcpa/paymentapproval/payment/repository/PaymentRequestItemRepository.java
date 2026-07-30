package tw.com.jsgcpa.paymentapproval.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestItem;

public interface PaymentRequestItemRepository extends JpaRepository<PaymentRequestItem, Long> {
}
