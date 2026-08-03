package tw.com.jsgcpa.paymentapproval.security.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.com.jsgcpa.paymentapproval.security.authentication.AuthenticatedUserPrincipal;
import tw.com.jsgcpa.paymentapproval.security.dto.response.AuthenticatedUserResponse;
import tw.com.jsgcpa.paymentapproval.security.entity.AppUserCredential;
import tw.com.jsgcpa.paymentapproval.security.enums.SecurityRole;
import tw.com.jsgcpa.paymentapproval.security.exception.AuthenticationBusinessException;
import tw.com.jsgcpa.paymentapproval.security.repository.AppUserCredentialRepository;

@Service
public class AuthenticationService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Taipei");

    private final AuthenticationManager authenticationManager;
    private final AppUserCredentialRepository credentialRepository;
    private final Clock clock;

    @Autowired
    public AuthenticationService(
            AuthenticationManager authenticationManager,
            AppUserCredentialRepository credentialRepository
    ) {
        this(
                authenticationManager,
                credentialRepository,
                Clock.system(BUSINESS_ZONE)
        );
    }

    AuthenticationService(
            AuthenticationManager authenticationManager,
            AppUserCredentialRepository credentialRepository,
            Clock clock
    ) {
        this.authenticationManager = authenticationManager;
        this.credentialRepository = credentialRepository;
        this.clock = clock;
    }

    @Transactional
    public Authentication authenticate(String username, String password) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            username,
                            password
                    )
            );
        } catch (AuthenticationException exception) {
            throw invalidCredentials();
        }

        if (!(authentication.getPrincipal()
                instanceof AuthenticatedUserPrincipal principal)) {
            throw consistencyError("Authenticated principal is invalid");
        }

        AppUserCredential credential = credentialRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> consistencyError(
                        "Authenticated credential is missing"
                ));
        credential.setLastLoginAt(OffsetDateTime.now(clock));

        return authentication;
    }

    public AuthenticatedUserResponse toResponse(Authentication authentication) {
        if (!(authentication.getPrincipal()
                instanceof AuthenticatedUserPrincipal principal)) {
            throw consistencyError("Authenticated principal is invalid");
        }
        return toResponse(principal);
    }

    public AuthenticatedUserResponse toResponse(
            AuthenticatedUserPrincipal principal
    ) {
        List<SecurityRole> roles = principal.getAuthorities()
                .stream()
                .map(authority -> {
                    try {
                        return SecurityRole.valueOf(authority.getAuthority());
                    } catch (IllegalArgumentException exception) {
                        throw consistencyError(
                                "Authenticated authority is invalid"
                        );
                    }
                })
                .toList();

        return new AuthenticatedUserResponse(
                principal.getUserId(),
                principal.getUsername(),
                principal.getDisplayName(),
                roles
        );
    }

    private AuthenticationBusinessException invalidCredentials() {
        return new AuthenticationBusinessException(
                "INVALID_CREDENTIALS",
                "帳號或密碼錯誤"
        );
    }

    private AuthenticationBusinessException consistencyError(String message) {
        return new AuthenticationBusinessException(
                "AUTHENTICATION_CONSISTENCY_ERROR",
                message
        );
    }
}
