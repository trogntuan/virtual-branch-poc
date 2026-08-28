package com.example.virtualbranch.chat;

public class ChatValidationException extends RuntimeException {

    private final String errorCode;

    public ChatValidationException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
