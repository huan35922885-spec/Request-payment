package tw.com.jsgcpa.paymentapproval.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {
}
