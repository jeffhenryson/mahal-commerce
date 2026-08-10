package com.cernecommerce.infra.config;

import com.cernecommerce.adapter.out.storage.S3ProductImageStorageAdapter;
import com.cernecommerce.core.ports.out.storage.ProductImageStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Ativado apenas quando {@code product.image.storage.type=s3}. Gêmeo de {@code S3StorageConfig},
 * separado porque as duas configurações são independentes — avatar pode estar em disco e imagem
 * de produto em S3, ou o contrário.
 *
 * <p>Quando os dois estão em S3 existem dois beans {@code S3Client} no contexto. Não há
 * {@code @Qualifier} no projeto; a desambiguação é por <b>nome</b> — {@code avatarS3Client} e
 * {@code productImageS3Client} —, e é por isso que o parâmetro do bean abaixo tem exatamente o
 * nome do método que o produz. Renomear um sem o outro quebra o boot.</p>
 */
@Configuration
@ConditionalOnProperty(name = "product.image.storage.type", havingValue = "s3")
class ProductImageS3StorageConfig {

    @Bean
    S3Client productImageS3Client(ProductImageProperties productImageProps) {
        ProductImageProperties.S3 s3 = productImageProps.getS3();
        S3ClientBuilder builder = S3Client.builder().region(Region.of(s3.getRegion()));
        if (s3.getAccessKey() != null && s3.getSecretKey() != null) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())));
        }
        return builder.build();
    }

    @Bean
    ProductImageStoragePort productImageStoragePort(S3Client productImageS3Client,
            ProductImageProperties productImageProps) {
        ProductImageProperties.S3 s3 = productImageProps.getS3();
        return new S3ProductImageStorageAdapter(productImageS3Client, s3.getBucket(), s3.getPublicUrlBase());
    }
}
