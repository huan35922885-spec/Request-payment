package tw.com.jsgcpa.paymentapproval.master.admin.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tw.com.jsgcpa.paymentapproval.common.exception.GlobalExceptionHandler;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.CreateExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.DeactivateExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.ExpenseTypeVersionRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.request.RenameExpenseTypeRequest;
import tw.com.jsgcpa.paymentapproval.master.admin.dto.response.ExpenseTypeAdminResponse;
import tw.com.jsgcpa.paymentapproval.master.admin.exception.ExpenseTypeAdminBusinessException;
import tw.com.jsgcpa.paymentapproval.master.admin.service.ExpenseTypeAdminService;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;
import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;

@WebMvcTest(ExpenseTypeAdminController.class)
@Import({
        GlobalExceptionHandler.class,
        ExpenseTypeAdminControllerTest.MvcTestConfiguration.class
})
class ExpenseTypeAdminControllerTest {

    @TestConfiguration
    static class MvcTestConfiguration implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(
                List<HandlerMethodArgumentResolver> resolvers
        ) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.hasParameterAnnotation(
                            AuthenticationPrincipal.class
                    );
                }

                @Override
                public Object resolveArgument(
                        MethodParameter parameter,
                        ModelAndViewContainer container,
                        NativeWebRequest request,
                        WebDataBinderFactory binderFactory
                ) {
                    return new AuthenticatedUserPrincipal(
                            7L,
                            "master.admin",
                            "{bcrypt}hash",
                            "Master Admin",
                            true,
                            List.of()
                    );
                }
            });
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseTypeAdminService service;

    @Test
    void listReturnsAllExpenseTypes() throws Exception {
        when(service.list()).thenReturn(List.of(response(true), response(false)));

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/admin/master/expense-types")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code").value("MEAL"))
                .andExpect(jsonPath("$[1].active").value(false));

        verify(service).list();
    }

    @Test
    void createNormalizesCodeAndReturnsCreated() throws Exception {
        when(service.create(any(CreateExpenseTypeRequest.class), eq(7L)))
                .thenReturn(response(false));

        mockMvc.perform(post("/api/admin/master/expense-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":" meal ","name":" Lunch ","calculationType":"MEAL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MEAL"));

        var captor = org.mockito.ArgumentCaptor.forClass(
                CreateExpenseTypeRequest.class
        );
        verify(service).create(captor.capture(), eq(7L));
        org.junit.jupiter.api.Assertions.assertEquals("MEAL", captor.getValue().code());
        org.junit.jupiter.api.Assertions.assertEquals("Lunch", captor.getValue().name());
    }

    @Test
    void invalidCreateRequestReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/admin/master/expense-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"bad-code\",\"name\":\" \",\"calculationType\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(service, never()).create(any(), any());
    }

    @Test
    void renameActivateAndDeactivateDelegatePrincipalAndBody() throws Exception {
        when(service.rename(eq(3L), any(RenameExpenseTypeRequest.class), eq(7L)))
                .thenReturn(response(true));
        when(service.activate(eq(3L), any(ExpenseTypeVersionRequest.class), eq(7L)))
                .thenReturn(response(true));
        when(service.deactivate(eq(3L), any(DeactivateExpenseTypeRequest.class), eq(7L)))
                .thenReturn(response(false));

        mockMvc.perform(patch("/api/admin/master/expense-types/3/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\",\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/master/expense-types/3/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/master/expense-types/3/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" Retired \",\"version\":2}"))
                .andExpect(status().isOk());

        verify(service).rename(eq(3L), any(RenameExpenseTypeRequest.class), eq(7L));
        verify(service).activate(eq(3L), any(ExpenseTypeVersionRequest.class), eq(7L));
        verify(service).deactivate(eq(3L), any(DeactivateExpenseTypeRequest.class), eq(7L));
    }

    @Test
    void businessErrorUsesConflictStatus() throws Exception {
        when(service.activate(eq(3L), any(ExpenseTypeVersionRequest.class), eq(7L)))
                .thenThrow(new ExpenseTypeAdminBusinessException(
                        "EXPENSE_TYPE_CURRENT_PRICE_REQUIRED",
                        "price required"
                ));

        mockMvc.perform(post("/api/admin/master/expense-types/3/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("EXPENSE_TYPE_CURRENT_PRICE_REQUIRED"));
    }

    private ExpenseTypeAdminResponse response(boolean active) {
        return new ExpenseTypeAdminResponse(
                3L,
                "MEAL",
                "Lunch",
                CalculationType.MEAL,
                active,
                active ? 1L : 0L,
                OffsetDateTime.parse("2026-08-05T00:00:00+08:00"),
                OffsetDateTime.parse("2026-08-05T00:00:00+08:00")
        );
    }
}
