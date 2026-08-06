package tw.com.jsgcpa.paymentapproval.payment.report;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tw.com.jsgcpa.paymentapproval.payment.exception.PaymentDraftBusinessException;

@Service
public class PaymentResultExportService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final JdbcTemplate jdbcTemplate;

    public PaymentResultExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public byte[] exportExcel(LocalDate paidFrom, LocalDate paidTo) {
        if (paidFrom == null || paidTo == null) {
            throw businessError("INVALID_EXPORT_PERIOD", "匯出期間為必填");
        }
        if (paidTo.isBefore(paidFrom)) {
            throw businessError("INVALID_EXPORT_PERIOD", "結束日期不可早於開始日期");
        }

        ZonedDateTime from = paidFrom.atStartOfDay(BUSINESS_ZONE);
        ZonedDateTime toExclusive = paidTo.plusDays(1).atStartOfDay(BUSINESS_ZONE);

        List<PaymentResultExportRow> rows = jdbcTemplate.query(
                """
                SELECT c.code AS customer_code,
                       c.name AS customer_name,
                       et.name AS expense_name,
                       SUM(pri.amount) AS total_amount
                FROM payment_request_items pri
                INNER JOIN payment_requests pr ON pr.id = pri.payment_request_id
                INNER JOIN customers c ON c.id = pr.customer_id
                INNER JOIN expense_types et ON et.id = pri.expense_type_id
                WHERE pr.approval_status = 'APPROVED'
                  AND pr.payment_status = 'PAID'
                  AND pr.paid_at >= ?
                  AND pr.paid_at < ?
                GROUP BY c.code, c.name, et.name
                ORDER BY c.code, et.name
                """,
                (resultSet, rowNum) -> new PaymentResultExportRow(
                        resultSet.getString("customer_code"),
                        resultSet.getString("customer_name"),
                        resultSet.getString("expense_name"),
                        resultSet.getBigDecimal("total_amount")
                ),
                from.toOffsetDateTime(),
                toExclusive.toOffsetDateTime()
        );

        try (Workbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("結果檔案");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("客戶代號");
            header.createCell(1).setCellValue("客戶名稱");
            header.createCell(2).setCellValue("費用名稱");
            header.createCell(3).setCellValue("總金額");

            int rowIndex = 1;
            for (PaymentResultExportRow row : rows) {
                Row dataRow = sheet.createRow(rowIndex++);
                dataRow.createCell(0).setCellValue(row.customerCode());
                dataRow.createCell(1).setCellValue(row.customerName());
                dataRow.createCell(2).setCellValue(row.expenseName());
                dataRow.createCell(3).setCellValue(
                        row.totalAmount() == null
                                ? 0d
                                : row.totalAmount().doubleValue()
                );
            }

            for (int column = 0; column < 4; column++) {
                sheet.autoSizeColumn(column);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build payment result export", exception);
        }
    }

    private PaymentDraftBusinessException businessError(String code, String message) {
        return new PaymentDraftBusinessException(code, message);
    }

    private record PaymentResultExportRow(
            String customerCode,
            String customerName,
            String expenseName,
            BigDecimal totalAmount
    ) {
    }
}
