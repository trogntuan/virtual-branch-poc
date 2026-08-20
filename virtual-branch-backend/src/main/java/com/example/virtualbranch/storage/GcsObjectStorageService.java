package com.example.virtualbranch.storage;

import com.example.virtualbranch.config.StorageProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "virtual-branch.storage.provider", havingValue = "gcs")
public class GcsObjectStorageService implements ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(GcsObjectStorageService.class);

    private final Storage storage;
    private final StorageProperties storageProperties;
    private final String bucket;

    public GcsObjectStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        this.bucket = storageProperties.bucket();
        this.storage = buildStorage(storageProperties);
    }

    @Override
    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectKey))
                    .setContentType(contentType)
                    .build();
            storage.createFrom(blobInfo, inputStream);
        } catch (Exception e) {
            throw new StorageOperationException("GCS upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String presignGetUrl(String objectKey, int expirySeconds) {
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectKey)).build();
            URL url = storage.signUrl(
                    blobInfo,
                    expirySeconds,
                    TimeUnit.SECONDS,
                    Storage.SignUrlOption.withV4Signature()
            );
            return url.toString();
        } catch (Exception signUrlError) {
            if (hasHmacCredentials()) {
                log.warn(
                        "GCS signUrl failed (user ADC cannot sign); falling back to HMAC presign: {}",
                        signUrlError.getMessage()
                );
                return presignWithHmac(objectKey, expirySeconds);
            }
            throw new StorageOperationException("GCS presign failed: " + signUrlError.getMessage(), signUrlError);
        }
    }

    private String presignWithHmac(String objectKey, int expirySeconds) {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(storageProperties.endpoint())
                    .credentials(storageProperties.accessKey(), storageProperties.secretKey())
                    .build();
            return client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageOperationException("GCS HMAC presign failed: " + e.getMessage(), e);
        }
    }

    private boolean hasHmacCredentials() {
        return storageProperties.accessKey() != null
                && !storageProperties.accessKey().isBlank()
                && storageProperties.secretKey() != null
                && !storageProperties.secretKey().isBlank();
    }

    private static Storage buildStorage(StorageProperties storageProperties) {
        try {
            StorageOptions.Builder builder = StorageOptions.newBuilder();
            String projectHint = System.getenv().getOrDefault("GOOGLE_CLOUD_PROJECT", "");
            if (!projectHint.isBlank()) {
                builder.setProjectId(projectHint);
            }
            GoogleCredentials credentials = resolveCredentials(storageProperties);
            if (credentials != null) {
                builder.setCredentials(credentials);
            }
            return builder.build().getService();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize GCS client: " + e.getMessage(), e);
        }
    }

    private static GoogleCredentials resolveCredentials(StorageProperties storageProperties) throws IOException {
        String inlineJson = storageProperties.gcsCredentialsJson();
        if (inlineJson != null && !inlineJson.isBlank() && inlineJson.contains("service_account")) {
            log.info("GCS client credentials=inline service account JSON");
            return GoogleCredentials.fromStream(
                    new ByteArrayInputStream(inlineJson.getBytes(StandardCharsets.UTF_8))
            );
        }
        String credentialsPath = storageProperties.gcsCredentialsPath();
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            log.info("GCS client credentials=file");
            try (FileInputStream stream = new FileInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(stream);
            }
        }
        try {
            GoogleCredentials adc = GoogleCredentials.getApplicationDefault();
            log.info("GCS client credentials=application-default");
            return adc;
        } catch (IOException adcMissing) {
            log.info(
                    "GCS ADC missing ({}); using gcloud user token. "
                            + "Optional: gcloud auth application-default login",
                    adcMissing.getMessage()
            );
            return new GcloudCliCredentials();
        }
    }
}
