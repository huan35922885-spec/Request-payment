package tw.com.jsgcpa.paymentapproval.security.authentication;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.security.entity.AppUserCredential;
import tw.com.jsgcpa.paymentapproval.security.entity.AppUserRole;
import tw.com.jsgcpa.paymentapproval.security.repository.AppUserCredentialRepository;
import tw.com.jsgcpa.paymentapproval.security.repository.AppUserRoleRepository;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AppUserCredentialRepository credentialRepository;
    private final AppUserRoleRepository roleRepository;

    public DatabaseUserDetailsService(
            AppUserCredentialRepository credentialRepository,
            AppUserRoleRepository roleRepository
    ) {
        this.credentialRepository = credentialRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        AppUserCredential credential = credentialRepository
                .findByUser_Username(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Authentication user not found"
                ));

        AppUser user = credential.getUser();
        List<GrantedAuthority> authorities = roleRepository
                .findByUser_IdOrderByIdAsc(user.getId())
                .stream()
                .map(AppUserRole::getRoleCode)
                .map(role -> new SimpleGrantedAuthority(role.name()))
                .map(GrantedAuthority.class::cast)
                .toList();

        return new AuthenticatedUserPrincipal(
                user.getId(),
                user.getUsername(),
                credential.getPasswordHash(),
                user.getDisplayName(),
                Boolean.TRUE.equals(user.getActive()),
                authorities
        );
    }
}
