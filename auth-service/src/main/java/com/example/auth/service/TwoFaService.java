package com.example.auth.service;

import com.example.auth.dto.TwoFaDisableRequest;
import com.example.auth.dto.TwoFaEnableRequest;
import com.example.auth.dto.TwoFaEnableResponse;
import com.example.auth.dto.TwoFaSetupResponse;
import com.example.auth.entity.AuthUser;
import com.example.auth.exception.TwoFaException;
import com.example.auth.repository.AuthUserRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TwoFaService {

    private static final String ISSUER = "Snapgram";
    private static final int BACKUP_CODE_COUNT = 8;

    private final AuthUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ─── Setup ───────────────────────────────────────────────────────────────

    /**
     * Generate a new TOTP secret, save it (2FA still disabled), return QR data.
     */
    @Transactional
    public TwoFaSetupResponse setup(UUID userId) {
        AuthUser user = getUser(userId);

        SecretGenerator secretGenerator = new DefaultSecretGenerator();
        String secret = secretGenerator.generate();

        // Save secret but keep twoFaEnabled = false until confirmed
        user.setTotpSecret(secret);
        userRepository.save(user);

        QrData qrData = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        String qrCodeBase64 = generateQrBase64(qrData);

        log.info("2FA setup initiated for user {}", userId);
        return TwoFaSetupResponse.builder()
                .secret(secret)
                .otpAuthUrl(qrData.getUri())
                .qrCodeBase64(qrCodeBase64)
                .build();
    }

    // ─── Enable ──────────────────────────────────────────────────────────────

    /**
     * Confirm TOTP code, enable 2FA, generate and return backup codes.
     */
    @Transactional
    public TwoFaEnableResponse enable(UUID userId, TwoFaEnableRequest request) {
        AuthUser user = getUser(userId);

        if (user.isTwoFaEnabled()) {
            throw new TwoFaException("Two-factor authentication is already enabled");
        }
        if (user.getTotpSecret() == null) {
            throw new TwoFaException("2FA setup has not been initiated. Call /2fa/setup first");
        }

        if (!verifyTotpCode(user.getTotpSecret(), request.getTotpCode())) {
            throw new TwoFaException("Invalid TOTP code");
        }

        List<String> rawCodes = generateBackupCodes();
        String hashedCodes = hashBackupCodes(rawCodes);

        user.setTwoFaEnabled(true);
        user.setBackupCodesHash(hashedCodes);
        userRepository.save(user);

        log.info("2FA enabled for user {}", userId);
        return TwoFaEnableResponse.builder()
                .backupCodes(rawCodes)
                .message("Two-factor authentication enabled. Save these backup codes securely — they won't be shown again.")
                .build();
    }

    // ─── Disable ─────────────────────────────────────────────────────────────

    /**
     * Disable 2FA after verifying TOTP code or a backup code.
     */
    @Transactional
    public void disable(UUID userId, TwoFaDisableRequest request) {
        AuthUser user = getUser(userId);

        if (!user.isTwoFaEnabled()) {
            throw new TwoFaException("Two-factor authentication is not enabled");
        }

        boolean verified = false;

        if (request.getTotpCode() != null && !request.getTotpCode().isBlank()) {
            verified = verifyTotpCode(user.getTotpSecret(), request.getTotpCode());
        } else if (request.getBackupCode() != null && !request.getBackupCode().isBlank()) {
            verified = consumeBackupCode(user, request.getBackupCode());
        }

        if (!verified) {
            throw new TwoFaException("Invalid TOTP code or backup code");
        }

        user.setTwoFaEnabled(false);
        user.setTotpSecret(null);
        user.setBackupCodesHash(null);
        userRepository.save(user);

        log.info("2FA disabled for user {}", userId);
    }

    // ─── Verify (used by login flow) ─────────────────────────────────────────

    /**
     * Verify TOTP code during login 2FA step.
     */
    public boolean verifyForLogin(AuthUser user, String totpCode) {
        return verifyTotpCode(user.getTotpSecret(), totpCode);
    }

    /**
     * Verify a backup code during login 2FA step (consumes the code).
     */
    @Transactional
    public boolean verifyBackupCodeForLogin(AuthUser user, String backupCode) {
        return consumeBackupCode(user, backupCode);
    }

    // ─── Internals ───────────────────────────────────────────────────────────

    private boolean verifyTotpCode(String secret, String code) {
        CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
        CodeVerifier verifier = new DefaultCodeVerifier(codeGenerator, new SystemTimeProvider());
        return verifier.isValidCode(secret, code);
    }

    /**
     * Try to consume a backup code. Returns true if found and not yet used.
     * Backup codes are stored as comma-separated BCrypt hashes.
     */
    private boolean consumeBackupCode(AuthUser user, String rawCode) {
        if (user.getBackupCodesHash() == null) return false;

        List<String> hashes = new ArrayList<>(
                Arrays.asList(user.getBackupCodesHash().split(","))
        );

        String matchedHash = hashes.stream()
                .filter(h -> !h.isBlank() && passwordEncoder.matches(rawCode.trim(), h.trim()))
                .findFirst()
                .orElse(null);

        if (matchedHash == null) return false;

        // Consume (remove) the used backup code
        hashes.remove(matchedHash);
        user.setBackupCodesHash(String.join(",", hashes));
        userRepository.save(user);
        return true;
    }

    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            // e.g. "A1B2-C3D4" format
            String raw = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 8).toUpperCase();
            codes.add(raw.substring(0, 4) + "-" + raw.substring(4));
        }
        return codes;
    }

    private String hashBackupCodes(List<String> rawCodes) {
        return rawCodes.stream()
                .map(passwordEncoder::encode)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private String generateQrBase64(QrData qrData) {
        try {
            ZxingPngQrGenerator generator = new ZxingPngQrGenerator();
            byte[] imageBytes = generator.generate(qrData);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (QrGenerationException e) {
            log.warn("QR code generation failed, skipping: {}", e.getMessage());
            return null;
        }
    }

    private AuthUser getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new com.example.auth.exception.UserNotFoundException("User not found"));
    }
}
