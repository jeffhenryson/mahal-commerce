package com.cernecommerce.infra.config;

import com.cernecommerce.adapter.out.storage.S3AvatarStorageAdapter;
import com.cernecommerce.core.ports.out.storage.AvatarStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Ativado apenas quando avatar.storage.type=s3.
 * Credenciais via env vars (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY),
 * ou explicitamente via avatar.s3.access-key / avatar.s3.secret-key.
 * Em ECS/EC2/Lambda, omitir as chaves e usar IAM role.
 */
@Configuration
@ConditionalOnProperty(name = "avatar.storage.type", havingValue = "s3")
class S3StorageConfig {

    @Bean
    S3Client avatarS3Client(AvatarProperties avatarProps) {
        AvatarProperties.S3 s3 = avatarProps.getS3();
        S3ClientBuilder builder = S3Client.builder().region(Region.of(s3.getRegion()));
        if (s3.getAccessKey() != null && s3.getSecretKey() != null) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())));
        }
        return builder.build();
    }

    @Bean
    AvatarStoragePort avatarStoragePort(S3Client avatarS3Client, AvatarProperties avatarProps) {
        AvatarProperties.S3 s3 = avatarProps.getS3();
        return new S3AvatarStorageAdapter(avatarS3Client, s3.getBucket(), s3.getPublicUrlBase());
    }
}
