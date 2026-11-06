package com.example.auth.exception;

public class InvalidTurnstileException extends RuntimeException {

    public InvalidTurnstileException(String message) {
        super(message);
    }

    public InvalidTurnstileException(String message, Throwable cause) {
        super(message, cause);
    }
}
