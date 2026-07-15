package com.cernecommerce.infra.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@Configuration
@OpenAPIDefinition(
    info = @Info(title = "Security Spring API", version = "v1")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
public class OpenApiConfig {

    @Bean
    OpenAPI openAPI() {
        // Exibe Instant como string ISO-8601 no Swagger UI em vez de objeto com epochSecond/nano.
        SpringDocUtils.getConfig().replaceWithClass(java.time.Instant.class, String.class);
        return new OpenAPI();
    }
}
