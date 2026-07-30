package tw.com.jsgcpa.paymentapproval.master.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.master.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
