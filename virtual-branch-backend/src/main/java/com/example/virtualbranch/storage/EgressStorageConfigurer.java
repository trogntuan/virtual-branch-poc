package com.example.virtualbranch.storage;

import com.example.virtualbranch.config.EgressStorageProperties;
import com.example.virtualbranch.config.StorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import livekit.LivekitEgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EgressStorageConfigurer {

    private static final Logger log = LoggerFactory.getLogger(EgressStorageConfigurer.class);

    private final EgressStorageProperties egressStorageProperties;
    private final StorageProperties storageProperties;

    public EgressStorageConfigurer(
            EgressStorageProperties egressStorageProperties,
            StorageProperties storageProperties
    ) {
        this.egressStorageProperties = egressStorageProperties;
        this.storageProperties = storageProperties;
    }

    public void attach(LivekitEgress.EncodedFileOutput.Builder outputBuilder) {
        if (egressStorageProperties.isConfigured()) {
            attachDedicatedEgressS3(outputBuilder);
            return;
        }
        if (storageProperties.isGcs()) {
            attachGcs(outputBuilder);
            return;
        }
        attachLegacyS3(outputBuilder);
    }

    public boolean hasEgressCredentials() {
        if (egressStorageProperties.isConfigured()) {
            return true;
        }
        if (storageProperties.isGcs()) {
            return hasGcsServiceAccountCredentials();
        }
        return hasLegacyCloudS3Credentials();
    }

    public boolean isLocalEndpoint() {
        if (egressStorageProperties.isConfigured()) {
            return isLocalEndpoint(egressStorageProperties.endpoint());
        }
        return isLocalEndpoint(storageProperties.endpoint());
    }

    private void attachDedicatedEgressS3(LivekitEgress.EncodedFileOutput.Builder outputBuilder) {
        LivekitEgress.S3Upload s3Upload = buildS3Upload(
                egressStorageProperties.bucket(),
                egressStorageProperties.endpoint(),
                egressStorageProperties.accessKey(),
                egressStorageProperties.secretKey(),
                egressStorageProperties.region(),
                egressStorageProperties.forcePathStyle()
        );
        outputBuilder.setS3(s3Upload);
        log.info(
                "Egress R2/S3 upload configured bucket={} endpoint={} pathStyle={}",
                egressStorageProperties.bucket(),
                egressStorageProperties.endpoint(),
                egressStorageProperties.forcePathStyle()
        );
    }

    private void attachGcs(LivekitEgress.EncodedFileOutput.Builder outputBuilder) {
        String credentialsJson = readGcsCredentialsJson();
        if (credentialsJson != null) {
            outputBuilder.setGcp(
                    LivekitEgress.GCPUpload.newBuilder()
                            .setCredentials(credentialsJson)
                            .setBucket(storageProperties.bucket())
                            .build()
            );
            log.info("Egress GCP upload configured bucket={}", storageProperties.bucket());
            return;
        }
        if (hasLegacyS3Credentials() && isGcsS3Endpoint(storageProperties.endpoint())) {
            log.warn(
                    "GCS HMAC keys are set but LiveKit egress cannot upload to GCS via S3 (AWS SDK signature mismatch). "
                            + "Configure Cloudflare R2 via VB_EGRESS_* or add GOOGLE_APPLICATION_CREDENTIALS for GCPUpload."
            );
        }
    }

    private void attachLegacyS3(LivekitEgress.EncodedFileOutput.Builder outputBuilder) {
        if (!hasLegacyCloudS3Credentials()) {
            if (hasLegacyS3Credentials() && (isDefaultMinioCredentials() || isLocalEndpoint(storageProperties.endpoint()))) {
                log.warn(
                        "Skipping local/default MinIO egress upload (endpoint={}, provider={})",
                        storageProperties.endpoint(),
                        storageProperties.provider()
                );
            }
            return;
        }
        LivekitEgress.S3Upload s3Upload = buildS3Upload(
                storageProperties.bucket(),
                storageProperties.endpoint(),
                storageProperties.accessKey(),
                storageProperties.secretKey(),
                storageProperties.region(),
                storageProperties.forcePathStyle()
        );
        outputBuilder.setS3(s3Upload);
        log.info(
                "Egress S3 upload configured bucket={} endpoint={} pathStyle={}",
                storageProperties.bucket(),
                storageProperties.endpoint(),
                storageProperties.forcePathStyle()
        );
    }

    private LivekitEgress.S3Upload buildS3Upload(
            String bucket,
            String endpoint,
            String accessKey,
            String secretKey,
            String region,
            boolean forcePathStyle
    ) {
        LivekitEgress.S3Upload.Builder s3Builder = LivekitEgress.S3Upload.newBuilder()
                .setAccessKey(accessKey)
                .setSecret(secretKey)
                .setBucket(bucket)
                .setForcePathStyle(forcePathStyle);

        if (endpoint != null && !endpoint.isBlank()) {
            s3Builder.setEndpoint(endpoint);
        }
        String effectiveRegion = region;
        if (effectiveRegion == null || effectiveRegion.isBlank()) {
            if (isGcsS3Endpoint(endpoint)) {
                effectiveRegion = "auto";
            }
        }
        if (effectiveRegion != null && !effectiveRegion.isBlank()) {
            s3Builder.setRegion(effectiveRegion);
        }
        return s3Builder.build();
    }

    /** S3 credentials on main storage suitable for cloud egress (not local MinIO defaults). */
    private boolean hasLegacyCloudS3Credentials() {
        if (!hasLegacyS3Credentials() || isDefaultMinioCredentials() || isLocalEndpoint(storageProperties.endpoint())) {
            return false;
        }
        return !storageProperties.isGcs() || isGcsS3Endpoint(storageProperties.endpoint());
    }

    private boolean hasLegacyS3Credentials() {
        return storageProperties.accessKey() != null
                && !storageProperties.accessKey().isBlank()
                && storageProperties.secretKey() != null
                && !storageProperties.secretKey().isBlank();
    }

    private boolean isDefaultMinioCredentials() {
        return "minioadmin".equals(storageProperties.accessKey())
                && "minioadmin123".equals(storageProperties.secretKey());
    }

    private static boolean isGcsS3Endpoint(String endpoint) {
        return endpoint != null && endpoint.toLowerCase().contains("storage.googleapis.com");
    }

    private static boolean isLocalEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        String lower = endpoint.toLowerCase();
        return lower.contains("localhost")
                || lower.contains("127.0.0.1")
                || lower.contains("[::1]")
                || lower.contains("host.docker.internal")
                || lower.startsWith("http://minio:")
                || lower.startsWith("http://minio/");
    }

    private boolean hasGcsServiceAccountCredentials() {
        String credentialsJson = readGcsCredentialsJson();
        return credentialsJson != null && credentialsJson.contains("service_account");
    }

    private String readGcsCredentialsJson() {
        String inline = storageProperties.gcsCredentialsJson();
        if (inline != null && !inline.isBlank() && inline.contains("service_account")) {
            return inline.trim();
        }
        String credentialsPath = storageProperties.gcsCredentialsPath();
        if (credentialsPath == null || credentialsPath.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(credentialsPath);
            if (!Files.isRegularFile(path)) {
                return null;
            }
            String json = Files.readString(path);
            if (!json.contains("service_account") && !json.contains("\"type\"")) {
                return null;
            }
            return json;
        } catch (Exception e) {
            log.warn("Failed to read GCS credentials from {}: {}", credentialsPath, e.getMessage());
            return null;
        }
    }
}
