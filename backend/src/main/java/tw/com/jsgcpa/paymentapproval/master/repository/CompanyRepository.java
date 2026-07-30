package tw.com.jsgcpa.paymentapproval.master.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.master.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {
}
