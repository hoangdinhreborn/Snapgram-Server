package com.example.media.ai;

public class AiTimeoutException extends RuntimeException {
    public AiTimeoutException(String message) {
        super(message);
    }

    public AiTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
