package com.example.auth.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private MailServiceImpl mailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mailService, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(mailService, "fromEmail", "sender@example.com");
        ReflectionTestUtils.setField(mailService, "fromName", "Snapgram");
        ReflectionTestUtils.setField(mailService, "mailUsername", "sender@example.com");
        ReflectionTestUtils.setField(mailService, "mailPassword", "sample-app-password");
    }

    @Test
    @DisplayName("sendPasswordResetEmail should prepare MimeMessage and send via JavaMailSender")
    void sendPasswordResetEmail_ShouldPrepareAndSend() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailService.sendPasswordResetEmail("user@example.com", "sample-reset-token-xyz");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendVerificationEmail should prepare MimeMessage and send via JavaMailSender")
    void sendVerificationEmail_ShouldPrepareAndSend() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        mailService.sendVerificationEmail("user@example.com", "sample-verify-token-xyz");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("ensureSenderConfigured with JavaMailSenderImpl should sanitize whitespace in password")
    void ensureSenderConfigured_WithJavaMailSenderImpl_ShouldStripWhitespace() {
        org.springframework.mail.javamail.JavaMailSenderImpl impl = new org.springframework.mail.javamail.JavaMailSenderImpl();
        MailServiceImpl serviceWithImpl = new MailServiceImpl(impl);
        ReflectionTestUtils.setField(serviceWithImpl, "mailUsername", "testuser@gmail.com");
        ReflectionTestUtils.setField(serviceWithImpl, "mailPassword", "abcd efgh ijkl mnop");

        serviceWithImpl.sendPasswordResetEmail("user@example.com", "token123");

        org.assertj.core.api.Assertions.assertThat(impl.getPassword()).isEqualTo("abcdefghijklmnop");
        org.assertj.core.api.Assertions.assertThat(impl.getUsername()).isEqualTo("testuser@gmail.com");
    }

    @Test
    @DisplayName("ensureSenderConfigured with missing credentials should abort without sending")
    void ensureSenderConfigured_WithMissingCredentials_ShouldAbort() {
        org.springframework.mail.javamail.JavaMailSenderImpl impl = mock(org.springframework.mail.javamail.JavaMailSenderImpl.class);
        MailServiceImpl serviceWithImpl = new MailServiceImpl(impl);
        ReflectionTestUtils.setField(serviceWithImpl, "mailUsername", "");
        ReflectionTestUtils.setField(serviceWithImpl, "mailPassword", "");

        serviceWithImpl.sendPasswordResetEmail("user@example.com", "token123");

        verify(impl, never()).send(any(MimeMessage.class));
    }
}
