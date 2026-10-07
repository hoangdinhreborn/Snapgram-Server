package com.example.auth.config;

import com.example.auth.entity.AuthUser;
import com.example.auth.entity.Role;
import com.example.auth.repository.AuthUserRepository;
import com.example.auth.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAccountInitializer implements ApplicationRunner {

    private final AuthUserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.initial-email:${ADMIN_INITIAL_EMAIL:}}")
    private String initialEmail;

    @Value("${app.admin.initial-username:${ADMIN_INITIAL_USERNAME:}}")
    private String initialUsername;

    @Value("${app.admin.initial-password:${ADMIN_INITIAL_PASSWORD:}}")
    private String initialPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (initialEmail == null || initialEmail.isBlank()
                || initialUsername == null || initialUsername.isBlank()
                || initialPassword == null || initialPassword.isBlank()) {
            log.info("Admin seeding skipped: ADMIN_INITIAL_EMAIL, ADMIN_INITIAL_USERNAME, or ADMIN_INITIAL_PASSWORD not configured in .env.");
            return;
        }

        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("An ADMIN account already exists in database; skipping admin seed.");
            return;
        }

        log.info("Seeding initial ADMIN account for email: {}", initialEmail);

        AuthUser user = userRepository.findByEmail(initialEmail)
                .or(() -> userRepository.findByUsername(initialUsername))
                .orElseGet(AuthUser::new);

        user.setUsername(initialUsername);
        user.setEmail(initialEmail);
        user.setPasswordHash(passwordEncoder.encode(initialPassword));
        user.setRole(Role.ADMIN);
        user.setEmailVerified(true);
        if (user.getDisplayName() == null || user.getDisplayName().isBlank()) {
            user.setDisplayName("Snapgram Admin");
        }

        userRepository.save(user);
        roleService.assignRole(user.getId(), "ADMIN");

        log.info("Successfully initialized first ADMIN account: username={}, id={}", user.getUsername(), user.getId());
    }
}
