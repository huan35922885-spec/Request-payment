package tw.com.jsgcpa.paymentapproval.payment.report;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment-reports")
public class PaymentResultExportController {

    private final PaymentResultExportService exportService;

    public PaymentResultExportController(PaymentResultExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping(
            value = "/result-export",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    public ResponseEntity<byte[]> exportResult(
            @RequestParam("paidFrom")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate paidFrom,
            @RequestParam("paidTo")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate paidTo
    ) {
        byte[] content = exportService.exportExcel(paidFrom, paidTo);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"payment-result.xlsx\""
                )
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .body(content);
    }
}
