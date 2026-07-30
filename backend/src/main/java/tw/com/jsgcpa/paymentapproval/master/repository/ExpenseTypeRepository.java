package tw.com.jsgcpa.paymentapproval.master.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;

public interface ExpenseTypeRepository extends JpaRepository<ExpenseType, Long> {
}
