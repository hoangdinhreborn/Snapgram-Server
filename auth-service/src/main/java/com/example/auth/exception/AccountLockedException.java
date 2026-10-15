package com.example.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.LOCKED)
public class AccountLockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public AccountLockedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
