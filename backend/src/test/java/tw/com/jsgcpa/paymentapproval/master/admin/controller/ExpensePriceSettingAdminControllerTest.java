package tw.com.jsgcpa.paymentapproval.master.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tw.com.jsgcpa.paymentapproval.common.exception.GlobalExceptionHandler;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpensePriceSettingRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.service.ExpensePriceSettingAdminService;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.response.ExpensePriceSettingAdminResponse;
import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;

@WebMvcTest(ExpensePriceSettingAdminController.class)
@Import({
        GlobalExceptionHandler.class,
        ExpensePriceSettingAdminControllerTest.MvcTestConfiguration.class
})
class ExpensePriceSettingAdminControllerTest {

    @TestConfiguration
    static class MvcTestConfiguration implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                }

                @Override
                public Object resolveArgument(
                        MethodParameter parameter,
                        ModelAndViewContainer container,
                        NativeWebRequest request,
                        WebDataBinderFactory binderFactory
                ) {
                    return new AuthenticatedUserPrincipal(
                            7L, "master.admin", "{bcrypt}hash", "Master Admin",
                            true, List.of()
                    );
                }
            });
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpensePriceSettingAdminService service;

    @Test
    void listPassesOptionalFiltersAndReturnsPlainResponse() throws Exception {
        when(service.list(3L, true, LocalDate.of(2026, 8, 5)))
                .thenReturn(List.of(response(true)));

        mockMvc.perform(get("/api/admin/master/expense-price-settings")
                        .param("expenseTypeId", "3")
                        .param("active", "true")
                        .param("effectiveOn", "2026-08-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].priceCode").value("DEFAULT"))
                .andExpect(jsonPath("$[0].amount").value(100.0))
                .andExpect(jsonPath("$[0].effective").value(true));

        verify(service).list(3L, true, LocalDate.of(2026, 8, 5));
    }

    @Test
    void createUsesAuthenticatedActorAndReturnsCreated() throws Exception {
        when(service.create(eq(3L), any(CreateExpensePriceSettingRequest.class), eq(7L)))
                .thenReturn(response(false));

        mockMvc.perform(post("/api/admin/master/expense-types/3/price-settings")
                        .contentType("application/json")
                        .content("""
                                {"priceCode":" standard ","amount":100.00,
                                 "effectiveFrom":"2026-08-10","effectiveTo":null}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(false));

        verify(service).create(eq(3L), any(CreateExpensePriceSettingRequest.class), eq(7L));
    }

    @Test
    void invalidCreateIsRejectedBeforeService() throws Exception {
        mockMvc.perform(post("/api/admin/master/expense-types/3/price-settings")
                        .contentType("application/json")
                        .content("{\"priceCode\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(service, never()).create(any(), any(), any());
    }

    private ExpensePriceSettingAdminResponse response(boolean active) {
        return new ExpensePriceSettingAdminResponse(
                10L, 3L, "MEAL", "Meal", "DEFAULT", "DEFAULT",
                new BigDecimal("100.00"), LocalDate.of(2026, 8, 1), null,
                active, active ? 1L : 0L, active,
                OffsetDateTime.parse("2026-08-05T00:00:00+08:00"),
                OffsetDateTime.parse("2026-08-05T00:00:00+08:00")
        );
    }
}
