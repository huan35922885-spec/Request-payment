package tw.com.jsgcpa.paymentapproval.master.repository;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;

public interface ExpenseTypeRepository extends JpaRepository<ExpenseType, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select expenseType from ExpenseType expenseType where expenseType.id = :id")
    java.util.Optional<ExpenseType> findByIdForUpdate(@Param("id") Long id);

    List<ExpenseType> findAllByOrderByCodeAscIdAsc();

    boolean existsByCode(String code);

    List<ExpenseType> findByActiveTrueOrderByCodeAscIdAsc();
}
