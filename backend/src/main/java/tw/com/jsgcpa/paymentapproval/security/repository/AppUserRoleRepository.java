package tw.com.jsgcpa.paymentapproval.security.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.com.jsgcpa.paymentapproval.security.entity.AppUserRole;
import tw.com.jsgcpa.paymentapproval.security.enums.SecurityRole;

@Repository
public interface AppUserRoleRepository extends JpaRepository<AppUserRole, Long> {

    List<AppUserRole> findByUser_IdOrderByIdAsc(Long userId);

    boolean existsByUser_IdAndRoleCode(Long userId, SecurityRole roleCode);
}
