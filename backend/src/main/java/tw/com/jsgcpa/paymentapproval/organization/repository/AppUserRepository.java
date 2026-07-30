package tw.com.jsgcpa.paymentapproval.organization.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
}
