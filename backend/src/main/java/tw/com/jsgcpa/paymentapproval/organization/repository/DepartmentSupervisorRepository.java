package tw.com.jsgcpa.paymentapproval.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.DepartmentSupervisor;

public interface DepartmentSupervisorRepository extends JpaRepository<DepartmentSupervisor, Long> {
}
