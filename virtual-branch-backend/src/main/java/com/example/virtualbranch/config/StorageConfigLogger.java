package com.example.virtualbranch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StorageConfigLogger {

    private static final Logger log = LoggerFactory.getLogger(StorageConfigLogger.class);

    private final StorageProperties storageProperties;
    private final EgressStorageProperties egressStorageProperties;

    public StorageConfigLogger(
            StorageProperties storageProperties,
            EgressStorageProperties egressStorageProperties
    ) {
        this.storageProperties = storageProperties;
        this.egressStorageProperties = egressStorageProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStorageConfig() {
        log.info(
                "Storage config provider={} bucket={} endpoint={} gcsCredentialsPath={}",
                storageProperties.provider(),
                storageProperties.bucket(),
                storageProperties.endpoint(),
                storageProperties.gcsCredentialsPath() == null || storageProperties.gcsCredentialsPath().isBlank()
                        ? "(ADC/default)"
                        : "set"
        );
        if (egressStorageProperties.isConfigured()) {
            log.info(
                    "Egress storage (recordings) bucket={} endpoint={} pathStyle={}",
                    egressStorageProperties.bucket(),
                    egressStorageProperties.endpoint(),
                    egressStorageProperties.forcePathStyle()
            );
        } else {
            log.info("Egress storage (recordings) not configured — set VB_EGRESS_* for LiveKit Cloud recording");
        }
    }
}
