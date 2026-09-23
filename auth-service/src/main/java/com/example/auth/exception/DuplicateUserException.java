package com.example.auth.exception;

public class DuplicateUserException extends AuthException {
    public DuplicateUserException(String message) {
        super(message);
    }
}
