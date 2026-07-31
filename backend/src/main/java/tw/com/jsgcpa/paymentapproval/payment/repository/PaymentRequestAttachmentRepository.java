package tw.com.jsgcpa.paymentapproval.payment.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;

public interface PaymentRequestAttachmentRepository extends JpaRepository<PaymentRequestAttachment, Long> {

    List<PaymentRequestAttachment> findByPaymentRequest_IdOrderByCreatedAtAscIdAsc(
            Long paymentRequestId
    );
}
