package com.example.recommender.exception;

public class JobConflictException extends RuntimeException {
    public JobConflictException(String message) {
        super(message);
    }
}
