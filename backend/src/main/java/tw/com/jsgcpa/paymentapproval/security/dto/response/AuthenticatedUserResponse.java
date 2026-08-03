package tw.com.jsgcpa.paymentapproval.security.dto.response;

import java.util.List;

import tw.com.jsgcpa.paymentapproval.security.enums.SecurityRole;

public record AuthenticatedUserResponse(
        Long userId,
        String username,
        String displayName,
        List<SecurityRole> roles
) {
}
