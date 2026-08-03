package tw.com.jsgcpa.paymentapproval.security.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.com.jsgcpa.paymentapproval.security.entity.AppUserCredential;

@Repository
public interface AppUserCredentialRepository extends JpaRepository<AppUserCredential, Long> {

    Optional<AppUserCredential> findByUser_Username(String username);
}
