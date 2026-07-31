package tw.com.jsgcpa.paymentapproval.organization.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tw.com.jsgcpa.paymentapproval.organization.entity.DepartmentSupervisor;

public interface DepartmentSupervisorRepository extends JpaRepository<DepartmentSupervisor, Long> {

    @Query("""
            select departmentSupervisor
            from DepartmentSupervisor departmentSupervisor
            where departmentSupervisor.department.id = :departmentId
              and departmentSupervisor.active = true
              and departmentSupervisor.effectiveFrom <= :effectiveDate
              and (
                    departmentSupervisor.effectiveTo is null
                    or departmentSupervisor.effectiveTo >= :effectiveDate
              )
            order by departmentSupervisor.effectiveFrom desc,
                     departmentSupervisor.id desc
            """)
    List<DepartmentSupervisor> findEffectiveSupervisors(
            @Param("departmentId") Long departmentId,
            @Param("effectiveDate") LocalDate effectiveDate
    );
}
