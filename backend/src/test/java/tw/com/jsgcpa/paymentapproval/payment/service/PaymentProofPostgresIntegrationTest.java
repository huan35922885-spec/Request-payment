package tw.com.jsgcpa.paymentapproval.payment.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalAction;
import tw.com.jsgcpa.paymentapproval.attachment.dto.response.DownloadPaymentRequestAttachmentResult;
import tw.com.jsgcpa.paymentapproval.attachment.service.DownloadPaymentRequestAttachmentService;
import tw.com.jsgcpa.paymentapproval.payment.dto.request.RecordPaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.PaymentRequestDetailResponse;
import tw.com.jsgcpa.paymentapproval.payment.dto.response.RecordPaymentResponse;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;
import tw.com.jsgcpa.paymentapproval.payment.repository.PaymentRequestRepository;

@SpringBootTest
class PaymentProofPostgresIntegrationTest {

    private static final byte[] PROOF = new byte[]{
            0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37
    };
    private static final Path STORAGE_ROOT = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "payment-proof-e2e-" + UUID.randomUUID()
    );

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RecordPaymentService recordPaymentService;
    @Autowired private DownloadPaymentRequestAttachmentService downloadService;
    @Autowired private GetPaymentRequestDetailService detailService;
    @Autowired private PaymentRequestRepository paymentRequestRepository;
    @Autowired private PlatformTransactionManager transactionManager;

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
    void postgresRecordPaymentPersistsProofPaymentHistoryAndDownload() throws Exception {
        createFixture();

        OffsetDateTime paidAt = OffsetDateTime.parse(
                "2026-08-06T10:30:00+08:00"
        );
        RecordPaymentResponse response = recordPaymentService.recordPayment(
                paymentRequestId,
                new RecordPaymentRequest(
                        0L,
                        paidAt,
                        PaymentMethod.BANK_TRANSFER,
                        "  PG-E2E-001  ",
                        "  PostgreSQL payment proof  "
                ),
                new MockMultipartFile(
                        "file", "payment-proof.pdf", "application/pdf", PROOF
                ),
                operatorId
        );

        assertEquals(ApprovalAction.PAYMENT_RECORDED, response.action());
        assertEquals(1L, response.version());
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payment_request_attachments "
                                + "WHERE payment_request_id = ? AND attachment_type = 'PAYMENT_PROOF'",
                        Integer.class,
                        paymentRequestId
                )
        );
        assertEquals(
                "PAID",
                jdbcTemplate.queryForObject(
                        "SELECT payment_status FROM payment_requests WHERE id = ?",
                        String.class,
                        paymentRequestId
                )
        );
        assertEquals(
                operatorId,
                jdbcTemplate.queryForObject(
                        "SELECT paid_by_id FROM payment_requests WHERE id = ?",
                        Long.class,
                        paymentRequestId
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM approval_histories "
                                + "WHERE payment_request_id = ? AND action = 'PAYMENT_RECORDED'",
                        Integer.class,
                        paymentRequestId
                )
        );

        String storagePath = jdbcTemplate.queryForObject(
                "SELECT storage_path FROM payment_request_attachments "
                        + "WHERE payment_request_id = ? AND attachment_type = 'PAYMENT_PROOF'",
                String.class,
                paymentRequestId
        );
        Path storedPath = STORAGE_ROOT.resolve(storagePath).normalize();
        assertTrue(storedPath.startsWith(STORAGE_ROOT.toAbsolutePath().normalize()));
        assertArrayEquals(PROOF, Files.readAllBytes(storedPath));

        PaymentRequestDetailResponse detail = detailService.getDetail(
                paymentRequestId,
                operatorId,
                false,
                true
        );
        assertEquals("PAID", detail.paymentStatus().name());

        Long attachmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM payment_request_attachments WHERE payment_request_id = ?",
                Long.class,
                paymentRequestId
        );
        DownloadPaymentRequestAttachmentResult download = downloadService.download(
                paymentRequestId,
                attachmentId,
                operatorId,
                false,
                true
        );
        try (var input = download.resource().getInputStream()) {
            assertArrayEquals(PROOF, input.readAllBytes());
        }
    }

    @Test
    void concurrentRecordPaymentHasOneWinnerAndOneCleanRollback() throws Exception {
        createFixture();
        Files.createDirectories(
                STORAGE_ROOT.resolve("payment-requests").resolve(paymentRequestId.toString())
        );
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Outcome> first = executor.submit(() -> concurrentCall(
                    transactionTemplate,
                    barrier,
                    "proof-a.pdf",
                    new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x41}
            ));
            Future<Outcome> second = executor.submit(() -> concurrentCall(
                    transactionTemplate,
                    barrier,
                    "proof-b.pdf",
                    new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x42}
            ));

            Outcome firstOutcome = first.get(20, TimeUnit.SECONDS);
            Outcome secondOutcome = second.get(20, TimeUnit.SECONDS);

            assertEquals(1, (firstOutcome.success ? 1 : 0) + (secondOutcome.success ? 1 : 0));
            assertEquals(1, countCode(firstOutcome, "PAYMENT_REQUEST_VERSION_CONFLICT")
                    + countCode(secondOutcome, "PAYMENT_REQUEST_VERSION_CONFLICT"),
                    firstOutcome.errorCode + "/" + secondOutcome.errorCode
                            + " (" + firstOutcome.errorType + ": " + firstOutcome.errorMessage + ")"
                            + " (" + secondOutcome.errorType + ": " + secondOutcome.errorMessage + ")");
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payment_request_attachments WHERE payment_request_id = ?",
                    Integer.class,
                    paymentRequestId
            ));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM approval_histories "
                            + "WHERE payment_request_id = ? AND action = 'PAYMENT_RECORDED'",
                    Integer.class,
                    paymentRequestId
            ));
            assertEquals("PAID", jdbcTemplate.queryForObject(
                    "SELECT payment_status FROM payment_requests WHERE id = ?",
                    String.class,
                    paymentRequestId
            ));
            assertEquals(1, regularFileCount());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    private Outcome concurrentCall(
            TransactionTemplate transactionTemplate,
            CyclicBarrier barrier,
            String filename,
            byte[] content
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                paymentRequestRepository.findById(paymentRequestId).orElseThrow();
                try {
                    barrier.await(20, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                recordPaymentService.recordPayment(
                        paymentRequestId,
                        new RecordPaymentRequest(
                                0L,
                                OffsetDateTime.parse("2026-08-06T11:00:00+08:00"),
                                PaymentMethod.BANK_TRANSFER,
                                filename,
                                filename
                        ),
                        new MockMultipartFile(
                                "file", filename, "application/pdf", content
                        ),
                        operatorId
                );
            });
            return new Outcome(true, null, null, null);
        } catch (Throwable exception) {
            return new Outcome(
                    false,
                    findBusinessCode(exception),
                    exception.getClass().getName(),
                    exception.getMessage()
            );
        }
    }

    private String findBusinessCode(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof PaymentDraftBusinessException businessException) {
                return businessException.getCode();
            }
            if (current instanceof OptimisticLockingFailureException
                    || current instanceof UnexpectedRollbackException) {
                return "PAYMENT_REQUEST_VERSION_CONFLICT";
            }
            current = current.getCause();
        }
        return null;
    }

    private int countCode(Outcome outcome, String code) {
        return code.equals(outcome.errorCode) ? 1 : 0;
    }

    private int regularFileCount() throws Exception {
        if (!Files.exists(STORAGE_ROOT)) {
            return 0;
        }
        try (var stream = Files.walk(STORAGE_ROOT)) {
            return (int) stream.filter(Files::isRegularFile).count();
        }
    }

    private record Outcome(
            boolean success,
            String errorCode,
            String errorType,
            String errorMessage
    ) {
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

    private void createFixture() {
        departmentId = jdbcTemplate.queryForObject(
                "INSERT INTO departments(code, name, active) VALUES (?, ?, TRUE) RETURNING id",
                Long.class,
                "PG_E2E_" + UUID.randomUUID(),
                "Payment proof E2E department"
        );
        applicantId = jdbcTemplate.queryForObject(
                "INSERT INTO app_users(username, display_name, department_id, active) "
                        + "VALUES (?, ?, ?, TRUE) RETURNING id",
                Long.class,
                "pg.e2e.applicant." + UUID.randomUUID(),
                "PG E2E Applicant",
                departmentId
        );
        operatorId = jdbcTemplate.queryForObject(
                "INSERT INTO app_users(username, display_name, department_id, active) "
                        + "VALUES (?, ?, ?, TRUE) RETURNING id",
                Long.class,
                "pg.e2e.operator." + UUID.randomUUID(),
                "PG E2E Payment Operator",
                departmentId
        );
        companyId = jdbcTemplate.queryForObject(
                "INSERT INTO companies(code, name, active) VALUES (?, ?, TRUE) RETURNING id",
                Long.class,
                "PG_E2E_CO_" + UUID.randomUUID(),
                "PG E2E Company"
        );
        customerId = jdbcTemplate.queryForObject(
                "INSERT INTO customers(code, name, default_request_category, active) "
                        + "VALUES (?, ?, 'EXPENSE', TRUE) RETURNING id",
                Long.class,
                "PG_E2E_CU_" + UUID.randomUUID(),
                "PG E2E Customer"
        );
        paymentRequestId = jdbcTemplate.queryForObject(
                "INSERT INTO payment_requests("
                        + "request_no, applicant_id, department_id, company_id, customer_id, "
                        + "request_category, reason, approval_status, payment_status, total_amount, version"
                        + ") VALUES (?, ?, ?, ?, ?, 'EXPENSE', ?, 'APPROVED', 'UNPAID', 100.00, 0) RETURNING id",
                Long.class,
                "PG-E2E-" + UUID.randomUUID(),
                applicantId,
                departmentId,
                companyId,
                customerId,
                "PG payment proof integration test"
        );
    }
}
