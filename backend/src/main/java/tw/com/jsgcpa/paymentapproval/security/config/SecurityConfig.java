package tw.com.jsgcpa.paymentapproval.security.config;

import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

import tw.com.jsgcpa.paymentapproval.security.authentication.DatabaseUserDetailsService;
import tw.com.jsgcpa.paymentapproval.security.handler.RestAuthenticationEntryPoint;
import tw.com.jsgcpa.paymentapproval.security.handler.RestAccessDeniedHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            DatabaseUserDetailsService databaseUserDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(
                databaseUserDetailsService
        );
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(
            ObjectMapper objectMapper
    ) {
        return new RestAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return new RestAccessDeniedHandler(objectMapper);
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setParameterName("_csrf");
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            CsrfTokenRepository csrfTokenRepository
    )
            throws Exception {
        http
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/csrf").permitAll()
                        .requestMatchers("/api/admin/master/**")
                        .hasAuthority("MASTER_DATA_ADMIN")
                        .requestMatchers(
                                "/api/auth/me",
                                "/api/auth/logout"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/payment-requests"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/master/companies",
                                "/api/master/customers",
                                "/api/master/expense-types",
                                "/api/master/expense-types/*/prices"
                        ).authenticated()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/payment-requests/*/attachments/*/download"
                        )
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/payment-requests/*"
                        ).authenticated()
                        .requestMatchers("/api/payment-requests/drafts")
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/payment-requests/*/attachments"
                        )
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/payment-requests/*/attachments/*"
                        )
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/payment-requests/*/submit"
                        )
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/payment-requests/*/manager-approve",
                                "/api/payment-requests/*/manager-reject"
                        )
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/payment-requests/*/cashier-approve",
                                "/api/payment-requests/*/cashier-reject"
                        )
                        .hasAuthority("CASHIER")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/payment-requests/*/record-payment"
                        )
                        .hasAuthority("CASHIER")
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/payment-requests/*/payment"
                        )
                        .hasAuthority("CASHIER")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/payment-requests/*/payment-proofs"
                        )
                        .hasAuthority("CASHIER")
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/payment-requests/*/payment-proofs/*"
                        )
                        .hasAuthority("CASHIER")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/payment-reports/result-export"
                        )
                        .hasAuthority("CASHIER")
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository));

        return http.build();
    }
}
