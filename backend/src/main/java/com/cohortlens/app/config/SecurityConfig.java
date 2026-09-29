package com.cohortlens.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;

/**
 * Three roles, each including the ones below it:
 * VIEWER sees aggregate analytics only (small groups suppressed).
 * RESEARCHER also sees individual level risk lists (pseudonymized), import reports, and can export.
 * ADMIN also imports data and reads the audit log.
 *
 * <p>Authentication is HTTP Basic over a stateless API, so CSRF protection is not needed. Serve the
 * app over HTTPS in any real deployment, because Basic credentials are otherwise sent in the clear.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/imports").hasRole("ADMIN")
                        .requestMatchers("/api/audit").hasRole("ADMIN")
                        .requestMatchers("/api/imports", "/api/imports/**", "/api/export.csv",
                                "/api/analytics/risk/students").hasRole("RESEARCHER")
                        .requestMatchers("/api/**").hasRole("VIEWER")
                        // The built dashboard (static files) holds no data; every data route is under /api.
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Passwords come from configuration and have no defaults, so the app will not start with blank ones. */
    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder encoder,
            @Value("${cohortlens.admin-password}") String admin,
            @Value("${cohortlens.researcher-password}") String researcher,
            @Value("${cohortlens.viewer-password}") String viewer) {
        Assert.hasText(admin, "COHORTLENS_ADMIN_PASSWORD must be set");
        Assert.hasText(researcher, "COHORTLENS_RESEARCHER_PASSWORD must be set");
        Assert.hasText(viewer, "COHORTLENS_VIEWER_PASSWORD must be set");
        return new InMemoryUserDetailsManager(
                User.withUsername("admin").password(encoder.encode(admin))
                        .roles("ADMIN", "RESEARCHER", "VIEWER").build(),
                User.withUsername("researcher").password(encoder.encode(researcher))
                        .roles("RESEARCHER", "VIEWER").build(),
                User.withUsername("viewer").password(encoder.encode(viewer))
                        .roles("VIEWER").build());
    }
}
