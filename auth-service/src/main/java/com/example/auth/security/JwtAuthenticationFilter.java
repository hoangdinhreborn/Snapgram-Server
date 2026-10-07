package com.example.auth.security;

import com.example.auth.service.TokenBlacklistService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final CookieUtils cookieUtils;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            TokenBlacklistService tokenBlacklistService,
            CookieUtils cookieUtils
    ) {
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.cookieUtils = cookieUtils;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = resolveToken(request);

        // No token → let Spring Security decide (public routes pass, protected routes → 401)
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Reject non-access tokens (refresh / temp tokens) early
            if (!jwtService.isAccessToken(token)) {
                rejectUnauthorized(response, "Invalid token type");
                return;
            }

            // Reject blacklisted tokens (user has logged out)
            String jti = jwtService.getJti(token);
            if (tokenBlacklistService.isBlacklisted(jti)) {
                rejectUnauthorized(response, "Token has been revoked");
                return;
            }

            UUID userId = jwtService.getUserId(token);

            // Skip if already authenticated (e.g. nested filters)
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            for (String role : jwtService.getRoles(token)) {
                String clean = role.startsWith("ROLE_") ? role.substring(5) : role;
                authorities.add(new SimpleGrantedAuthority("ROLE_" + clean));
                authorities.add(new SimpleGrantedAuthority(clean));
            }
            for (String perm : jwtService.getPermissions(token)) {
                authorities.add(new SimpleGrantedAuthority(perm));
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            rejectUnauthorized(response, "Invalid or expired token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth/admin") || uri.startsWith("/api/admin")) {
            return cookieUtils.extractCookieValue(request, CookieUtils.ADMIN_ACCESS_TOKEN_COOKIE)
                    .or(() -> cookieUtils.extractCookieValue(request, CookieUtils.ACCESS_TOKEN_COOKIE))
                    .orElse(null);
        }

        return cookieUtils.extractCookieValue(request, CookieUtils.ACCESS_TOKEN_COOKIE)
                .orElse(null);
    }

    /** Write a compact 401 JSON response without going through @ControllerAdvice. */
    private void rejectUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"status":401,"error":"Unauthorized","message":"%s"}
                """.formatted(message));
    }
}
