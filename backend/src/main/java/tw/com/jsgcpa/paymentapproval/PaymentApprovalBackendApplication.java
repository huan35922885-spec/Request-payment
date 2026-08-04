package tw.com.jsgcpa.paymentapproval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PaymentApprovalBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentApprovalBackendApplication.class, args);
	}

}
