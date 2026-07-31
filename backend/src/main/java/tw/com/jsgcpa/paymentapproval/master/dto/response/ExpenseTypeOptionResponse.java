package tw.com.jsgcpa.paymentapproval.master.dto.response;

import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;

public record ExpenseTypeOptionResponse(
        Long id,
        String code,
        String name,
        CalculationType calculationType
) {
}
