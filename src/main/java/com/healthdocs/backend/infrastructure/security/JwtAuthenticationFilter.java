package com.healthdocs.backend.infrastructure.security;

import java.io.IOException;
import com.healthdocs.backend.application.port.out.AuthProvider;
import com.healthdocs.backend.application.port.out.InvalidAuthenticationTokenException;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final AuthProvider authProvider;

    public JwtAuthenticationFilter(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Registration verifies the bearer token before a business account exists,
        // so the normal filter cannot resolve it through AuthProvider yet.
        return "/api/v1/auth/register".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var identity = authProvider.authenticateToken(header.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(
                        identity,
                        null,
                        identity.enabled()
                                ? java.util.List.of(
                                        new SimpleGrantedAuthority("ROLE_" + identity.role().name()),
                                        new SimpleGrantedAuthority("ACCOUNT_ENABLED"))
                                : java.util.List.of(new SimpleGrantedAuthority("ACCOUNT_DISABLED")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException | InvalidAuthenticationTokenException exception) {
                log.warn("JWT rejected for {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
