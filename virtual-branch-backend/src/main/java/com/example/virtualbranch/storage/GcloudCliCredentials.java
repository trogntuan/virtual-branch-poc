package com.example.virtualbranch.storage;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Local-dev credentials when ADC / SA JSON is missing.
 * Uses the same token as {@code gcloud auth print-access-token}.
 */
final class GcloudCliCredentials extends GoogleCredentials {

    @Override
    public AccessToken refreshAccessToken() throws IOException {
        ProcessBuilder builder = new ProcessBuilder("gcloud", "auth", "print-access-token");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] outputBytes = process.getInputStream().readAllBytes();
        String output = new String(outputBytes, StandardCharsets.UTF_8).trim();
        try {
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("gcloud auth print-access-token timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("gcloud auth print-access-token interrupted", e);
        }
        if (process.exitValue() != 0 || output.isBlank() || output.contains(" ")) {
            throw new IOException("gcloud auth print-access-token failed: " + output);
        }
        return new AccessToken(output, Date.from(Instant.now().plus(Duration.ofMinutes(50))));
    }
}
