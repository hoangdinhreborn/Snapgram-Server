package com.example.auth.exception;

public class OAuthAuthenticationException extends AuthException {
    public OAuthAuthenticationException(String message) {
        super(message);
    }

    public OAuthAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
