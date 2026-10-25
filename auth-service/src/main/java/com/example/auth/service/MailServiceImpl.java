package com.example.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.mail.from:${GOOGLE_MAIL_USERNAME:}}")
    private String fromEmail;

    @Value("${app.mail.from-name:Snapgram}")
    private String fromName;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Override
    @Async
    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        if (!ensureSenderConfigured()) {
            return;
        }

        String baseUrl = frontendUrl != null ? frontendUrl.replaceAll("/+$", "") : "http://localhost:3000";
        String resetUrl = baseUrl + "/reset-password?token=" + resetToken;
        String subject = "[Snapgram] Yêu cầu đặt lại mật khẩu của bạn";

        String senderAddress = resolveSenderAddress();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(senderAddress, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);

            String htmlBody = buildPasswordResetHtml(resetUrl, resetToken);
            String textBody = "Xin chào,\n\n"
                    + "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản Snapgram của bạn.\n\n"
                    + "Vui lòng truy cập liên kết sau để đặt lại mật khẩu (hết hạn sau 15 phút):\n"
                    + resetUrl + "\n\n"
                    + "Mã xác nhận (Token): " + resetToken + "\n\n"
                    + "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email này.\n\n"
                    + "Trân trọng,\nĐội ngũ Snapgram";

            helper.setText(textBody, htmlBody);

            log.info("Sending password reset email to {} via Google SMTP ({})", toEmail, senderAddress);
            mailSender.send(message);
            log.info("Successfully sent password reset email to {}", toEmail);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending password reset email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    @Override
    @Async
    public void sendVerificationEmail(String toEmail, String verificationToken) {
        if (!ensureSenderConfigured()) {
            return;
        }

        String baseUrl = frontendUrl != null ? frontendUrl.replaceAll("/+$", "") : "http://localhost:3000";
        String verifyUrl = baseUrl + "/verify-email?token=" + verificationToken;
        String subject = "[Snapgram] Xác minh địa chỉ email của bạn";

        String senderAddress = resolveSenderAddress();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(senderAddress, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);

            String htmlBody = buildVerificationHtml(verifyUrl, verificationToken);
            String textBody = "Xin chào,\n\n"
                    + "Cảm ơn bạn đã đăng ký tài khoản Snapgram.\n\n"
                    + "Vui lòng truy cập liên kết sau để xác nhận địa chỉ email của bạn (hết hạn sau 24 giờ):\n"
                    + verifyUrl + "\n\n"
                    + "Trân trọng,\nĐội ngũ Snapgram";

            helper.setText(textBody, htmlBody);

            log.info("Sending email verification to {} via Google SMTP ({})", toEmail, senderAddress);
            mailSender.send(message);
            log.info("Successfully sent verification email to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    private boolean ensureSenderConfigured() {
        if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl impl) {
            if (mailUsername != null && !mailUsername.isBlank() && (impl.getUsername() == null || impl.getUsername().isBlank())) {
                impl.setUsername(resolveSenderAddress());
            }
            if (mailPassword != null && !mailPassword.isBlank() && (impl.getPassword() == null || impl.getPassword().isBlank())) {
                impl.setPassword(mailPassword.trim().replaceAll("\\s+", ""));
            }

            if (impl.getUsername() == null || impl.getUsername().isBlank()
                    || impl.getPassword() == null || impl.getPassword().isBlank()) {
                log.error("Google SMTP credentials not configured. Please ensure GOOGLE_MAIL_USERNAME and GOOGLE_MAIL_PASSWORD are set in .env.");
                return false;
            }
        }
        return true;
    }

    private String resolveSenderAddress() {
        String candidate = fromEmail != null && !fromEmail.isBlank() ? fromEmail.trim() : mailUsername;
        if (candidate == null || candidate.isBlank()) {
            return "noreply@snapgram.com";
        }
        // Tự động bổ sung .com nếu cấu hình ghi thiếu (ví dụ: name@gmail)
        if (candidate.endsWith("@gmail")) {
            candidate = candidate + ".com";
        } else if (!candidate.contains("@")) {
            candidate = candidate + "@gmail.com";
        }
        return candidate;
    }

    private String buildPasswordResetHtml(String resetUrl, String resetToken) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Đặt lại mật khẩu Snapgram</title>
              <style>
                body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0b0e14; color: #f3f4f6; }
                .wrapper { max-width: 560px; margin: 30px auto; background-color: #121824; border-radius: 16px; border: 1px solid #1f2937; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                .header { background: linear-gradient(135deg, #f58529, #dd2a7b, #8134af); padding: 32px 24px; text-align: center; }
                .logo-title { font-size: 28px; font-weight: 800; letter-spacing: -0.5px; color: #ffffff; margin: 0; text-shadow: 0 2px 4px rgba(0,0,0,0.3); }
                .content { padding: 36px 32px; font-size: 14px; line-height: 1.6; color: #e5e7eb; }
                .greeting { font-size: 16px; font-weight: 600; margin-bottom: 12px; color: #ffffff; }
                .btn-container { text-align: center; margin: 32px 0; }
                .btn { display: inline-block; background: linear-gradient(135deg, #ec4899, #8b5cf6); color: #ffffff !important; font-size: 14px; font-weight: 600; text-decoration: none; padding: 14px 32px; border-radius: 12px; box-shadow: 0 4px 15px rgba(236,72,153,0.35); }
                .token-box { background-color: #1a2234; border: 1px dashed #374151; border-radius: 8px; padding: 12px 16px; font-family: monospace; font-size: 12px; word-break: break-all; color: #9ca3af; margin: 16px 0; }
                .note { font-size: 12px; color: #9ca3af; line-height: 1.5; margin-top: 24px; border-top: 1px solid #1f2937; padding-top: 20px; }
                .footer { background-color: #0d121c; padding: 20px 24px; text-align: center; font-size: 11px; color: #6b7280; border-top: 1px solid #1a2234; }
              </style>
            </head>
            <body>
              <div class="wrapper">
                <div class="header">
                  <h1 class="logo-title">Snapgram</h1>
                </div>
                <div class="content">
                  <div class="greeting">Xin chào,</div>
                  <p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản Snapgram của bạn.</p>
                  <p>Bấm vào nút bên dưới để tiến hành tạo mật khẩu mới. Liên kết bảo mật này có hiệu lực trong <strong>15 phút</strong>:</p>
                  
                  <div class="btn-container">
                    <a href="%s" class="btn" target="_blank">Đặt lại mật khẩu</a>
                  </div>

                  <p style="font-size: 12px; color: #9ca3af; margin-bottom: 6px;">Nếu nút bấm trên không hoạt động, bạn có thể sao chép liên kết này vào trình duyệt:</p>
                  <div class="token-box">%s</div>

                  <div class="note">
                    <p style="margin: 0 0 6px 0;"><strong>Bảo mật:</strong> Nếu bạn không yêu cầu đặt lại mật khẩu, bạn có thể yên tâm bỏ qua email này. Mật khẩu hiện tại của bạn vẫn an toàn.</p>
                  </div>
                </div>
                <div class="footer">
                  © 2026 Snapgram. Mọi quyền được bảo lưu. Email tự động từ Google Mail Server.
                </div>
              </div>
            </body>
            </html>
            """.formatted(resetUrl, resetUrl);
    }

    private String buildVerificationHtml(String verifyUrl, String verificationToken) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Xác minh email Snapgram</title>
              <style>
                body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0b0e14; color: #f3f4f6; }
                .wrapper { max-width: 560px; margin: 30px auto; background-color: #121824; border-radius: 16px; border: 1px solid #1f2937; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                .header { background: linear-gradient(135deg, #f58529, #dd2a7b, #8134af); padding: 32px 24px; text-align: center; }
                .logo-title { font-size: 28px; font-weight: 800; letter-spacing: -0.5px; color: #ffffff; margin: 0; }
                .content { padding: 36px 32px; font-size: 14px; line-height: 1.6; color: #e5e7eb; }
                .greeting { font-size: 16px; font-weight: 600; margin-bottom: 12px; color: #ffffff; }
                .btn-container { text-align: center; margin: 32px 0; }
                .btn { display: inline-block; background: linear-gradient(135deg, #ec4899, #8b5cf6); color: #ffffff !important; font-size: 14px; font-weight: 600; text-decoration: none; padding: 14px 32px; border-radius: 12px; }
                .footer { background-color: #0d121c; padding: 20px 24px; text-align: center; font-size: 11px; color: #6b7280; border-top: 1px solid #1a2234; }
              </style>
            </head>
            <body>
              <div class="wrapper">
                <div class="header">
                  <h1 class="logo-title">Snapgram</h1>
                </div>
                <div class="content">
                  <div class="greeting">Chào mừng bạn đến với Snapgram!</div>
                  <p>Vui lòng xác nhận địa chỉ email của bạn để kích hoạt đầy đủ các tính năng bảo mật của tài khoản:</p>
                  
                  <div class="btn-container">
                    <a href="%s" class="btn" target="_blank">Xác nhận Email</a>
                  </div>

                  <p style="font-size: 12px; color: #9ca3af;">Liên kết này có hiệu lực trong 24 giờ.</p>
                </div>
                <div class="footer">
                  © 2026 Snapgram. Mọi quyền được bảo lưu.
                </div>
              </div>
            </body>
            </html>
            """.formatted(verifyUrl);
    }
}
