package tw.com.jsgcpa.paymentapproval.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;

public interface PaymentRequestAttachmentRepository extends JpaRepository<PaymentRequestAttachment, Long> {
}
