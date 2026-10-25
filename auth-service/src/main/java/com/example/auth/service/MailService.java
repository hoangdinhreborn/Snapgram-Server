package com.example.auth.service;

public interface MailService {

    /**
     * Gửi email đặt lại mật khẩu chứa token xác thực và liên kết dẫn đến trang reset.
     *
     * @param toEmail   Email người nhận (Gmail)
     * @param resetToken Token bí mật dùng một lần (TTL 15 phút)
     */
    void sendPasswordResetEmail(String toEmail, String resetToken);

    /**
     * Gửi email xác nhận địa chỉ email tài khoản.
     *
     * @param toEmail           Email người nhận
     * @param verificationToken Token xác nhận (TTL 24 giờ)
     */
    void sendVerificationEmail(String toEmail, String verificationToken);
}
