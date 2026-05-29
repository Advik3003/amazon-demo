package com.amazondemo.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security Configuration for Auth Service
 * ================================================
 * Auth service has simple security - all /auth/** endpoints are public.
 * The API Gateway handles authentication for other services.
 *
 * STATELESS: We use JWT so NO sessions are created (STATELESS policy).
 * This is crucial for horizontal scaling - requests can go to any instance.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF - not needed for stateless JWT-based APIs
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless session - no HTTP sessions, each request must have JWT
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Request authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Actuator for health checks
                .requestMatchers("/actuator/**").permitAll()
                // Swagger UI
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**").permitAll()
                // All other requests need authentication
                .anyRequest().authenticated()
            );

        return http.build();
    }

    /**
     * BCrypt Password Encoder
     * BCrypt is the industry standard for password hashing because:
     * - It's slow by design (prevents brute force)
     * - It includes a salt (prevents rainbow table attacks)
     * - The strength factor (default: 10) can be increased for more security
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);  // Strength 12 = 2^12 = 4096 iterations
    }
}
