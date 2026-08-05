package tw.com.jsgcpa.paymentapproval.master.admin.dto.response;

import java.time.OffsetDateTime;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;

public record ExpenseTypeAdminResponse(
        Long id,
        String code,
        String name,
        CalculationType calculationType,
        Boolean active,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
