package tw.com.jsgcpa.paymentapproval.security.handler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import tools.jackson.databind.json.JsonMapper;

class RestAccessDeniedHandlerTest {

    private final StringWriter responseBody = new StringWriter();
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final RestAccessDeniedHandler handler = new RestAccessDeniedHandler(
            JsonMapper.builder().build()
    );

    @BeforeEach
    void setUp() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/payment-requests/drafts");
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Test
    void rendersSafeJsonForMissingCsrfToken() throws Exception {
        handler.handle(request, response, mock(MissingCsrfTokenException.class));

        verify(response).setStatus(403);
        verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = responseBody.toString();
        assertTrue(body.contains("INVALID_CSRF_TOKEN"));
        assertFalse(body.contains("expected-token"));
        assertFalse(body.contains("MissingCsrfTokenException"));
    }

    @Test
    void rendersGenericAccessDeniedCodeForOtherDeniedException() throws Exception {
        handler.handle(request, response, new AccessDeniedException("internal detail"));

        verify(response).setStatus(403);
        String body = responseBody.toString();
        assertTrue(body.contains("ACCESS_DENIED"));
        assertTrue(body.contains("沒有權限執行此操作"));
        assertFalse(body.contains("internal detail"));
    }
}
