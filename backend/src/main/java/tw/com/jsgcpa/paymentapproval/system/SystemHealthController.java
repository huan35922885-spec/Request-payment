package tw.com.jsgcpa.paymentapproval.system;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemHealthController {

    private final JdbcTemplate jdbcTemplate;

    public SystemHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        String databaseName = jdbcTemplate.queryForObject(
                "SELECT current_database()",
                String.class
        );

        String databaseUser = jdbcTemplate.queryForObject(
                "SELECT current_user",
                String.class
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("application", "payment-approval-backend");
        response.put("database", databaseName);
        response.put("databaseUser", databaseUser);

        return response;
    }
}