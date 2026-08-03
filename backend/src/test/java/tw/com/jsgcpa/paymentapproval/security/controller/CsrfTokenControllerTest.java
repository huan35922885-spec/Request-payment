package tw.com.jsgcpa.paymentapproval.security.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;

import tw.com.jsgcpa.paymentapproval.security.dto.response.CsrfTokenResponse;

class CsrfTokenControllerTest {

    private final CsrfTokenController controller = new CsrfTokenController();

    @Test
    void mapsSpringCsrfTokenToPublicResponse() {
        CsrfToken csrfToken = mock(CsrfToken.class);
        when(csrfToken.getToken()).thenReturn("token-value");
        when(csrfToken.getHeaderName()).thenReturn("X-CSRF-TOKEN");
        when(csrfToken.getParameterName()).thenReturn("_csrf");

        CsrfTokenResponse response = controller.getCsrfToken(csrfToken);

        assertEquals("token-value", response.token());
        assertEquals("X-CSRF-TOKEN", response.headerName());
        assertEquals("_csrf", response.parameterName());
    }
}
