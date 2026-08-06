package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tw.com.jsgcpa.paymentapproval.approval.repository.ApprovalHistoryRepository;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.RecordPaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequestAttachment;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestAttachmentRepository;

@SpringBootTest
class PaymentProofRollbackPostgresIntegrationTest {

    private static final byte[] PROOF = new byte[]{
            0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37
    };
    private static final Path STORAGE_ROOT = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "payment-proof-rollback-e2e-" + UUID.randomUUID()
    );

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RecordPaymentService recordPaymentService;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private PaymentRequestAttachmentRepository attachmentRepository;
    @MockitoSpyBean private ApprovalHistoryRepository approvalHistoryRepository;

    private Long paymentRequestId;
    private Long operatorId;
    private Long applicantId;
    private Long departmentId;
    private Long companyId;
    private Long customerId;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "payment-approval.attachment.storage-root",
                () -> STORAGE_ROOT.toString()
        );
    }

    @Test
    void postgresAttachmentFailureRollsBackPaymentAndStorage() {
        createFixture();
        doThrow(new RuntimeException("attachment metadata failure"))
                .when(attachmentRepository)
                .saveAndFlush(any(PaymentRequestAttachment.class));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                recordPaymentService.recordPayment(
                        paymentRequestId,
                        paymentRequest(),
                        proofFile("attachment-failure.pdf"),
                        operatorId
                )
        );

        assertEquals("attachment metadata failure", exception.getMessage());
        assertUnpaidApprovedRequest();
        assertEquals(0, proofCount());
        assertEquals(0, paymentHistoryCount());
        assertEquals(0, regularFileCount());
    }

    @Test
    void postgresHistoryFailureRollsBackPaymentAttachmentAndStorage() {
        createFixture();
        doThrow(new RuntimeException("history failure"))
                .when(approvalHistoryRepository)
                .saveAndFlush(any());

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                recordPaymentService.recordPayment(
                        paymentRequestId,
                        paymentRequest(),
                        proofFile("history-failure.pdf"),
                        operatorId
                )
        );

        assertEquals("history failure", exception.getMessage());
        assertUnpaidApprovedRequest();
        assertEquals(0, proofCount());
        assertEquals(0, paymentHistoryCount());
        assertEquals(0, regularFileCount());
    }

    @Test
    void postgresOuterRollbackOnlyCleansStorageAfterSuccessfulServiceCall() {
        createFixture();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            recordPaymentService.recordPayment(
                    paymentRequestId,
                    paymentRequest(),
                    proofFile("outer-rollback.pdf"),
                    operatorId
            );
            status.setRollbackOnly();
        });

        assertUnpaidApprovedRequest();
        assertEquals(0, proofCount());
        assertEquals(0, paymentHistoryCount());
        assertEquals(0, regularFileCount());
    }

    private RecordPaymentRequest paymentRequest() {
        return new RecordPaymentRequest(
                0L,
                OffsetDateTime.parse("2026-08-06T12:00:00+08:00"),
                PaymentMethod.BANK_TRANSFER,
                "ROLLBACK-001",
                "rollback test"
        );
    }

    private MockMultipartFile proofFile(String filename) {
        return new MockMultipartFile(
                "file", filename, "application/pdf", PROOF
        );
    }

    private void assertUnpaidApprovedRequest() {
        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT approval_status FROM payment_requests WHERE id = ?",
                String.class,
                paymentRequestId
        ));
        assertEquals("UNPAID", jdbcTemplate.queryForObject(
                "SELECT payment_status FROM payment_requests WHERE id = ?",
                String.class,
                paymentRequestId
        ));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT version FROM payment_requests WHERE id = ?",
                Long.class,
                paymentRequestId
        ));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT paid_by_id FROM payment_requests WHERE id = ?",
                Long.class,
                paymentRequestId
        ));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT paid_at FROM payment_requests WHERE id = ?",
                OffsetDateTime.class,
                paymentRequestId
        ));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT payment_method FROM payment_requests WHERE id = ?",
                String.class,
                paymentRequestId
        ));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT payment_reference FROM payment_requests WHERE id = ?",
                String.class,
                paymentRequestId
        ));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT payment_note FROM payment_requests WHERE id = ?",
                String.class,
                paymentRequestId
        ));
    }

    private int proofCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_request_attachments "
                        + "WHERE payment_request_id = ? AND attachment_type = 'PAYMENT_PROOF'",
                Integer.class,
                paymentRequestId
        );
    }

    private int paymentHistoryCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_histories "
                        + "WHERE payment_request_id = ? AND action = 'PAYMENT_RECORDED'",
                Integer.class,
                paymentRequestId
        );
    }

    private int regularFileCount() {
        if (!Files.exists(STORAGE_ROOT)) {
            return 0;
        }
        try (var stream = Files.walk(STORAGE_ROOT)) {
            return (int) stream.filter(Files::isRegularFile).count();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void createFixture() {
        departmentId = jdbcTemplate.queryForObject(
                "INSERT INTO departments(code, name, active) VALUES (?, ?, TRUE) RETURNING id",
                Long.class,
                "PG_ROLLBACK_" + UUID.randomUUID(),
                "Payment proof rollback department"
        );
        applicantId = jdbcTemplate.queryForObject(
                "INSERT INTO app_users(username, display_name, department_id, active) "
                        + "VALUES (?, ?, ?, TRUE) RETURNING id",
                Long.class,
                "pg.rollback.applicant." + UUID.randomUUID(),
                "PG Rollback Applicant",
                departmentId
        );
        operatorId = jdbcTemplate.queryForObject(
                "INSERT INTO app_users(username, display_name, department_id, active) "
                        + "VALUES (?, ?, ?, TRUE) RETURNING id",
                Long.class,
                "pg.rollback.operator." + UUID.randomUUID(),
                "PG Rollback Payment Operator",
                departmentId
        );
        companyId = jdbcTemplate.queryForObject(
                "INSERT INTO companies(code, name, active) VALUES (?, ?, TRUE) RETURNING id",
                Long.class,
                "PG_RB_CO_" + UUID.randomUUID(),
                "PG Rollback Company"
        );
        customerId = jdbcTemplate.queryForObject(
                "INSERT INTO customers(code, name, default_request_category, active) "
                        + "VALUES (?, ?, 'EXPENSE', TRUE) RETURNING id",
                Long.class,
                "PG_RB_CU_" + UUID.randomUUID(),
                "PG Rollback Customer"
        );
        paymentRequestId = jdbcTemplate.queryForObject(
                "INSERT INTO payment_requests("
                        + "request_no, applicant_id, department_id, company_id, customer_id, "
                        + "request_category, reason, approval_status, payment_status, total_amount, version"
                        + ") VALUES (?, ?, ?, ?, ?, 'EXPENSE', ?, 'APPROVED', 'UNPAID', 100.00, 0) RETURNING id",
                Long.class,
                "PG-ROLLBACK-" + UUID.randomUUID(),
                applicantId,
                departmentId,
                companyId,
                customerId,
                "PG payment proof rollback test"
        );
    }

    @AfterEach
    void cleanFixture() throws Exception {
        if (paymentRequestId != null) {
            jdbcTemplate.update(
                    "DELETE FROM payment_request_attachments WHERE payment_request_id = ?",
                    paymentRequestId
            );
            jdbcTemplate.update(
                    "DELETE FROM approval_histories WHERE payment_request_id = ?",
                    paymentRequestId
            );
            jdbcTemplate.update(
                    "DELETE FROM payment_requests WHERE id = ?",
                    paymentRequestId
            );
        }
        if (operatorId != null) {
            jdbcTemplate.update("DELETE FROM app_users WHERE id IN (?, ?)", operatorId, applicantId);
        }
        if (customerId != null) {
            jdbcTemplate.update("DELETE FROM customers WHERE id = ?", customerId);
        }
        if (companyId != null) {
            jdbcTemplate.update("DELETE FROM companies WHERE id = ?", companyId);
        }
        if (departmentId != null) {
            jdbcTemplate.update("DELETE FROM departments WHERE id = ?", departmentId);
        }
        if (Files.exists(STORAGE_ROOT)) {
            try (var stream = Files.walk(STORAGE_ROOT)) {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            }
        }
    }
}
