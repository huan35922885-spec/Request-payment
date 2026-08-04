package tw.com.jsgcpa.paymentapproval.attachment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import tw.com.jsgcpa.paymentapproval.attachment.dto.response.PaymentRequestAttachmentResponse;
import tw.com.jsgcpa.paymentapproval.attachment.dto.response.DownloadPaymentRequestAttachmentResult;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentBusinessException;
import tw.com.jsgcpa.paymentapproval.attachment.exception.PaymentRequestAttachmentDeleteException;
import tw.com.jsgcpa.paymentapproval.attachment.service.DeletePaymentRequestAttachmentService;
import tw.com.jsgcpa.paymentapproval.attachment.service.UploadPaymentRequestAttachmentService;
import tw.com.jsgcpa.paymentapproval.attachment.service.DownloadPaymentRequestAttachmentService;
import tw.com.jsgcpa.paymentapproval.common.exception.GlobalExceptionHandler;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;
import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;

@WebMvcTest(PaymentRequestAttachmentController.class)
@Import({
        GlobalExceptionHandler.class,
        PaymentRequestAttachmentControllerTest.MvcTestConfiguration.class
})
class PaymentRequestAttachmentControllerTest {

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
                            1L,
                            "e2e.applicant",
                            "{bcrypt}hash",
                            "E2E Applicant",
                            true,
                            List.of(new SimpleGrantedAuthority("APPLICANT"))
                    );
                }
            });
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadPaymentRequestAttachmentService uploadService;

    @MockitoBean
    private DownloadPaymentRequestAttachmentService downloadService;

    @MockitoBean
    private DeletePaymentRequestAttachmentService deleteService;

    @Test
    void downloadsBinaryWithSafeHeadersAndUtf8Filename() throws Exception {
        byte[] bytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D};
        when(downloadService.download(14L, 90L, 1L, false, false))
                .thenReturn(new DownloadPaymentRequestAttachmentResult(
                        new InputStreamResource(new ByteArrayInputStream(bytes)),
                        "下載測試發票.pdf",
                        MediaType.APPLICATION_PDF,
                        bytes.length
                ));

        mockMvc.perform(get(
                "/api/payment-requests/14/attachments/90/download"
        ))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_LENGTH,
                        String.valueOf(bytes.length)
                ))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("attachment"),
                                org.hamcrest.Matchers.containsString("filename*")
                        )
                ))
                .andExpect(header().string(
                        "X-Content-Type-Options",
                        "nosniff"
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ))
                .andExpect(content().bytes(bytes));

        verify(downloadService).download(14L, 90L, 1L, false, false);
    }

    @Test
    void mapsDownloadNotFoundTo404() throws Exception {
        when(downloadService.download(14L, 90L, 1L, false, false))
                .thenThrow(new tw.com.jsgcpa.paymentapproval.attachment.exception
                        .PaymentRequestAttachmentNotFoundException());

        mockMvc.perform(get(
                "/api/payment-requests/14/attachments/90/download"
        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_ATTACHMENT_NOT_FOUND"));
    }

    @Test
    void returns201AndPassesMultipartPartsToService() throws Exception {
        PaymentRequestAttachmentResponse response = response();
        when(uploadService.upload(
                eq(14L),
                eq(1L),
                eq(AttachmentType.INVOICE),
                any(MultipartFile.class)
        )).thenReturn(response);

        mockMvc.perform(attachmentRequest("INVOICE", pdfPart()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(90))
                .andExpect(jsonPath("$.attachmentType").value("INVOICE"))
                .andExpect(jsonPath("$.originalFilename")
                        .value("invoice.pdf"))
                .andExpect(jsonPath("$.uploadedById").value(1))
                .andExpect(jsonPath("$.storedFilename").doesNotExist())
                .andExpect(jsonPath("$.storagePath").doesNotExist());

        verify(uploadService).upload(
                eq(14L),
                eq(1L),
                eq(AttachmentType.INVOICE),
                any(MultipartFile.class)
        );
    }

    @Test
    void mapsUploadForbiddenTo403() throws Exception {
        when(uploadService.upload(
                eq(14L),
                eq(1L),
                eq(AttachmentType.INVOICE),
                any(MultipartFile.class)
        )).thenThrow(new PaymentRequestAttachmentBusinessException(
                "PAYMENT_REQUEST_ATTACHMENT_UPLOAD_FORBIDDEN",
                "forbidden"
        ));

        mockMvc.perform(attachmentRequest("INVOICE", pdfPart()).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_ATTACHMENT_UPLOAD_FORBIDDEN"));
    }

    @Test
    void mapsInvalidStatusTo409() throws Exception {
        when(uploadService.upload(
                eq(14L),
                eq(1L),
                eq(AttachmentType.INVOICE),
                any(MultipartFile.class)
        )).thenThrow(new PaymentRequestAttachmentBusinessException(
                "PAYMENT_REQUEST_ATTACHMENT_UPLOAD_STATUS_INVALID",
                "invalid status"
        ));

        mockMvc.perform(attachmentRequest("INVOICE", pdfPart()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_ATTACHMENT_UPLOAD_STATUS_INVALID"));
    }

    @Test
    void deletesAttachmentAndReturns204WithoutBody() throws Exception {
        mockMvc.perform(delete(
                "/api/payment-requests/14/attachments/90"
        ).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(deleteService).delete(14L, 90L, 1L);
    }

    @Test
    void mapsDeleteForbiddenTo403() throws Exception {
        doThrow(new PaymentRequestAttachmentDeleteException(
                        "PAYMENT_REQUEST_ATTACHMENT_DELETE_FORBIDDEN",
                        "forbidden"
                )).when(deleteService).delete(14L, 90L, 1L);

        mockMvc.perform(delete(
                "/api/payment-requests/14/attachments/90"
        ).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_ATTACHMENT_DELETE_FORBIDDEN"));
    }

    @Test
    void mapsDeleteStatusInvalidTo409() throws Exception {
        doThrow(new PaymentRequestAttachmentDeleteException(
                        "PAYMENT_REQUEST_ATTACHMENT_DELETE_STATUS_INVALID",
                        "invalid status"
                )).when(deleteService).delete(14L, 90L, 1L);

        mockMvc.perform(delete(
                "/api/payment-requests/14/attachments/90"
        ).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_ATTACHMENT_DELETE_STATUS_INVALID"));
    }

    @Test
    void mapsPaymentProofTypeInvalidTo400() throws Exception {
        doThrow(new PaymentRequestAttachmentDeleteException(
                        "PAYMENT_REQUEST_ATTACHMENT_TYPE_INVALID",
                        "invalid type"
                )).when(deleteService).delete(14L, 90L, 1L);

        mockMvc.perform(delete(
                "/api/payment-requests/14/attachments/90"
        ).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_REQUEST_ATTACHMENT_TYPE_INVALID"));
    }

    private MockMultipartHttpServletRequestBuilder attachmentRequest(
            String attachmentType,
            org.springframework.mock.web.MockMultipartFile file
    ) {
        return multipart("/api/payment-requests/14/attachments")
                .file(file)
                .param("attachmentType", attachmentType)
                .contentType(MediaType.MULTIPART_FORM_DATA);
    }

    private org.springframework.mock.web.MockMultipartFile pdfPart() {
        return new org.springframework.mock.web.MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}
        );
    }

    private PaymentRequestAttachmentResponse response() {
        return new PaymentRequestAttachmentResponse(
                90L,
                AttachmentType.INVOICE,
                "invoice.pdf",
                "application/pdf",
                5L,
                1L,
                "E2E Applicant",
                OffsetDateTime.parse("2026-08-03T18:00:00+08:00")
        );
    }
}
