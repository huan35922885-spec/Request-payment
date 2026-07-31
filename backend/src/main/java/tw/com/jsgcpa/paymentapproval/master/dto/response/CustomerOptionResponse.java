package tw.com.jsgcpa.paymentapproval.master.dto.response;

import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;

public record CustomerOptionResponse(
        Long id,
        String code,
        String name,
        RequestCategory defaultRequestCategory
) {
}
