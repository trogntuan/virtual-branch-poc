package com.example.virtualbranch.collab;

public enum ConsentDecision {
    ACCEPT,
    REJECT;

    public static ConsentDecision from(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ConsentDecision.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
