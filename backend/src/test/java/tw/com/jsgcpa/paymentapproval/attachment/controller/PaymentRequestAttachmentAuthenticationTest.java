package tw.com.jsgcpa.paymentapproval.attachment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tw.com.jsgcpa.paymentapproval.attachment.dto.response.PaymentRequestAttachmentResponse;
import tw.com.jsgcpa.paymentapproval.attachment.dto.response.DownloadPaymentRequestAttachmentResult;
import tw.com.jsgcpa.paymentapproval.attachment.service.DownloadPaymentRequestAttachmentService;
import tw.com.jsgcpa.paymentapproval.attachment.service.DeletePaymentRequestAttachmentService;
import tw.com.jsgcpa.paymentapproval.attachment.service.UploadPaymentRequestAttachmentService;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;
import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentRequestAttachmentAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadPaymentRequestAttachmentService uploadService;

    @MockitoBean
    private DownloadPaymentRequestAttachmentService downloadService;

    @MockitoBean
    private DeletePaymentRequestAttachmentService deleteService;

    @Test
    void anonymousDownloadReturns401WithoutCallingService() throws Exception {
        mockMvc.perform(get(
                "/api/payment-requests/14/attachments/90/download"
        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(downloadService, never()).download(
                any(), any(), any(), anyBoolean(), anyBoolean()
        );
    }

    @Test
    void authenticatedDownloadDoesNotRequireCsrf() throws Exception {
        byte[] bytes = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D};
        when(downloadService.download(14L, 90L, 1L, false, false))
                .thenReturn(new DownloadPaymentRequestAttachmentResult(
                        new InputStreamResource(new ByteArrayInputStream(bytes)),
                        "invoice.pdf",
                        MediaType.APPLICATION_PDF,
                        bytes.length
                ));

        mockMvc.perform(get(
                "/api/payment-requests/14/attachments/90/download"
        ).with(user(principal(1L))))
                .andExpect(status().isOk());

        verify(downloadService).download(14L, 90L, 1L, false, false);
    }

    @Test
    void anonymousUploadReturns401WithoutCallingService() throws Exception {
        mockMvc.perform(uploadRequest().with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(uploadService, never()).upload(any(), any(), any(), any());
    }

    @Test
    void authenticatedUploadWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(uploadRequest()
                        .with(user(principal(1L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CSRF_TOKEN"));

        verify(uploadService, never()).upload(any(), any(), any(), any());
    }

    @Test
    void authenticatedUploadWithCsrfReachesService() throws Exception {
        when(uploadService.upload(
                eq(14L),
                eq(1L),
                eq(AttachmentType.INVOICE),
                any()
        )).thenReturn(new PaymentRequestAttachmentResponse(
                90L,
                AttachmentType.INVOICE,
                "invoice.pdf",
                "application/pdf",
                5L,
                1L,
                "Applicant",
                null
        ));

        mockMvc.perform(uploadRequest()
                        .with(user(principal(1L)))
                        .with(csrf()))
                .andExpect(status().isCreated());

        verify(uploadService).upload(
                eq(14L),
                eq(1L),
                eq(AttachmentType.INVOICE),
                any()
        );
    }

    @Test
    void anonymousDeleteReturns401WithoutCallingService() throws Exception {
        mockMvc.perform(delete(
                "/api/payment-requests/14/attachments/90"
        ).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(deleteService, never()).delete(any(), any(), any());
    }

    @Test
    void authenticatedDeleteWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(delete(
                "/api/payment-requests/14/attachments/90"
        ).with(user(principal(1L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CSRF_TOKEN"));

        verify(deleteService, never()).delete(any(), any(), any());
    }

    @Test
    void authenticatedDeleteWithCsrfReachesService() throws Exception {
        mockMvc.perform(delete(
                "/api/payment-requests/14/attachments/90"
        ).with(user(principal(1L))).with(csrf()))
                .andExpect(status().isNoContent());

        verify(deleteService).delete(14L, 90L, 1L);
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder uploadRequest() {
        return multipart("/api/payment-requests/14/attachments")
                .file(pdfPart())
                .param("attachmentType", "INVOICE")
                .contentType(MediaType.MULTIPART_FORM_DATA);
    }

    private MockMultipartFile pdfPart() {
        return new MockMultipartFile(
                "file",
                "invoice.pdf",
                "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}
        );
    }

    private AuthenticatedUserPrincipal principal(Long userId) {
        return new AuthenticatedUserPrincipal(
                userId,
                "e2e.applicant",
                "{bcrypt}hash",
                "Applicant",
                true,
                List.of(new SimpleGrantedAuthority("APPLICANT"))
        );
    }
}
