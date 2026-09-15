package com.healthdocs.backend.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import java.util.UUID;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/login", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/dev/uploads/**").permitAll()
                        .requestMatchers("/api/v1/cases/**").hasRole("PACIENTE")
                        .requestMatchers("/api/v1/internal/**").hasAnyRole("ESTUDIANTE", "PROFESOR")
                        .requestMatchers("/api/v1/operations/**").hasRole("PROFESOR")
                        .requestMatchers("/api/v1/files/**", "/api/v1/me").hasAuthority("ACCOUNT_ENABLED")
                        .anyRequest().hasAuthority("ACCOUNT_ENABLED"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, 401, "UNAUTHORIZED", "Authentication required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, 403, "FORBIDDEN", "Access denied")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(
            jakarta.servlet.http.HttpServletResponse response,
            int status,
            String code,
            String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        if (status == 401) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.getWriter().write(
                "{\"code\":\"" + code + "\",\"message\":\"" + message
                        + "\",\"traceId\":\"" + UUID.randomUUID() + "\"}");
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
