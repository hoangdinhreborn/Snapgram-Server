package com.example.chat.exception;

/** Không phải thành viên hội thoại hoặc không có quyền với tài nguyên này (HTTP 403). */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
