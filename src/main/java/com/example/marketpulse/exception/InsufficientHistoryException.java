package com.example.marketpulse.exception;

public class InsufficientHistoryException extends RuntimeException {

    public InsufficientHistoryException(String message) {
        super(message);
    }
}
