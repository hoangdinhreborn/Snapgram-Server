 package com.example.auth.config;

import com.example.auth.security.JwtAuthenticationFilter;
import com.example.auth.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties({JwtProperties.class, FacebookOAuthProperties.class})
@EnableScheduling
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {

        http
                .cors(AbstractHttpConfigurer::disable)
                
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/facebook",
                                "/api/auth/admin/login",
                                "/api/auth/login/2fa",
                                "/api/auth/refresh",
                                "/api/auth/verify",
                                "/api/auth/logout",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/verify-email",
                                "/api/auth/admin/bootstrap",
                                "/api/users/search",
                                "/api/users/batch",
                                "/api/users/internal/**",
                                "/actuator/health",
                                // Swagger UI
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // RBAC & Roles/Permissions management: strictly ADMIN only
                        .requestMatchers(
                                "/api/auth/admin/roles/**",
                                "/api/auth/admin/permissions/**"
                        ).hasAnyAuthority("ROLE_ADMIN", "ADMIN")

                        // User Role Assignment: ADMIN or USER:ROLE_ASSIGN permission
                        .requestMatchers(
                                "/api/auth/admin/users/*/roles/**"
                        ).hasAnyAuthority("ROLE_ADMIN", "ADMIN", "USER:ROLE_ASSIGN")

                        // User Ban/Unban: ADMIN or USER:BAN / USER:UNBAN permission
                        .requestMatchers(
                                "/api/auth/admin/users/*/ban",
                                "/api/auth/admin/users/*/unban"
                        ).hasAnyAuthority("ROLE_ADMIN", "ADMIN", "USER:BAN", "USER:UNBAN")

                        // User Create / Delete / Update: strictly ADMIN
                        .requestMatchers(
                                org.springframework.http.HttpMethod.POST, "/api/auth/admin/users"
                        ).hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(
                                org.springframework.http.HttpMethod.PUT, "/api/auth/admin/users/*"
                        ).hasAnyAuthority("ROLE_ADMIN", "ADMIN")
                        .requestMatchers(
                                org.springframework.http.HttpMethod.DELETE, "/api/auth/admin/users/*"
                        ).hasAnyAuthority("ROLE_ADMIN", "ADMIN")

                        // General Admin & Staff endpoints (e.g. view users list/detail): accessible to any staff role
                        .requestMatchers("/api/auth/admin/**", "/api/admin/**")
                        .access((authentication, context) -> {
                            boolean isStaff = authentication.get().getAuthorities().stream()
                                    .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                                    .anyMatch(a -> !a.equalsIgnoreCase("ROLE_USER") && !a.equalsIgnoreCase("USER"));
                            return new org.springframework.security.authorization.AuthorizationDecision(isStaff);
                        })
                        // Users /me endpoints require authentication
                        .requestMatchers("/api/users/me", "/api/users/me/**")
                        .authenticated()
                        // Public user profile viewing
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/users/*")
                        .permitAll()
                        // Authenticated users
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
