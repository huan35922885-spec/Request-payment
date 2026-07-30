package tw.com.jsgcpa.paymentapproval.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
}
