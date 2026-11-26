package com.digitalwill.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@ConfigurationProperties(prefix = "digitalwill.crypto")
public class CryptoProperties {

    /**
     * Base64-encoded 256-bit (32 bytes) master key used for envelope encryption key wrapping.
     * The server requires an explicit key supplied by the operator.
     */
    private String masterKey;

    /**
     * Local storage path for encrypted document payloads.
     */
    private String storageDirectory = "./storage/documents";

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public String getStorageDirectory() {
        return storageDirectory;
    }

    public void setStorageDirectory(String storageDirectory) {
        this.storageDirectory = storageDirectory;
    }

    public Path getResolvedStoragePath() {
        return Paths.get(storageDirectory).toAbsolutePath().normalize();
    }
}
