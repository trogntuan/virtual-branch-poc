package com.example.virtualbranch.session;

public enum MobileOrientation {
    PORTRAIT,
    LANDSCAPE;

    public static MobileOrientation from(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MobileOrientation.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
