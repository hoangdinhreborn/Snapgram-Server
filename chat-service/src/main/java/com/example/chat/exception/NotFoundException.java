package com.example.chat.exception;

/** Không tìm thấy tài nguyên (HTTP 404). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
