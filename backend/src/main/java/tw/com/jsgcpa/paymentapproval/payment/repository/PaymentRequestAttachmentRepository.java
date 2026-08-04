package tw.com.jsgcpa.paymentapproval.payment.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;

public interface PaymentRequestAttachmentRepository extends JpaRepository<PaymentRequestAttachment, Long> {

    List<PaymentRequestAttachment> findByPaymentRequest_IdOrderByCreatedAtAscIdAsc(
            Long paymentRequestId
    );

    boolean existsByPaymentRequest_IdAndAttachmentType(
            Long paymentRequestId,
            AttachmentType attachmentType
    );
}
