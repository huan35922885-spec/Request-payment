package tw.com.jsgcpa.paymentapproval.payment.dto.response;

import java.util.List;

public record PaymentRequestPageResponse(
        List<PaymentRequestListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
