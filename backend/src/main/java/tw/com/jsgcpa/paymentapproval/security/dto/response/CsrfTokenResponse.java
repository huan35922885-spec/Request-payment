package tw.com.jsgcpa.paymentapproval.security.dto.response;

public record CsrfTokenResponse(
        String token,
        String headerName,
        String parameterName
) {
}
