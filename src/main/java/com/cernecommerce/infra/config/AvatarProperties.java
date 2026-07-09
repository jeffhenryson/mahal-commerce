package com.cernecommerce.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "avatar")
public class AvatarProperties {

    private String storageType = "local";
    private String storageDir = "./uploads/avatars";
    private String baseUrl = "http://localhost:8080";
    private long maxSizeBytes = 2_097_152L;
    private final S3 s3 = new S3();

    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }

    public String getStorageDir() { return storageDir; }
    public void setStorageDir(String storageDir) { this.storageDir = storageDir; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public long getMaxSizeBytes() { return maxSizeBytes; }
    public void setMaxSizeBytes(long maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }

    public S3 getS3() { return s3; }

    public static class S3 {
        private String bucket = "";
        private String region = "us-east-1";
        private String publicUrlBase = "";
        private String accessKey;
        private String secretKey;

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }

        public String getPublicUrlBase() { return publicUrlBase; }
        public void setPublicUrlBase(String publicUrlBase) { this.publicUrlBase = publicUrlBase; }

        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    }
}
