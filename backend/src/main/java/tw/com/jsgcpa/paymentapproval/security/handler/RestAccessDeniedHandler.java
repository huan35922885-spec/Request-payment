package tw.com.jsgcpa.paymentapproval.security.handler;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import tools.jackson.databind.ObjectMapper;

import tw.com.jsgcpa.paymentapproval.common.api.ApiErrorResponse;

public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        boolean invalidCsrf = accessDeniedException instanceof MissingCsrfTokenException
                || accessDeniedException instanceof InvalidCsrfTokenException;
        String code = invalidCsrf ? "INVALID_CSRF_TOKEN" : "ACCESS_DENIED";
        String message = invalidCsrf
                ? "CSRF 驗證失敗，請重新取得頁面後再試"
                : "沒有權限執行此操作";

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(
                OffsetDateTime.now(BUSINESS_ZONE),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                List.of()
        ));
    }
}
