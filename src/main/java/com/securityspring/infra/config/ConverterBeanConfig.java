package com.securityspring.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.securityspring.adapter.in.converter.PermissionDTOConverter;
import com.securityspring.adapter.in.converter.RoleDTOConverter;
import com.securityspring.adapter.in.converter.UserDTOConverter;
import com.securityspring.adapter.out.persistence.converter.UserEntityConverter;

@Configuration
class ConverterBeanConfig {

    @Bean
    UserEntityConverter userEntityConverter() {
        return new UserEntityConverter();
    }

    @Bean
    UserDTOConverter userDTOConverter(@Value("${avatar.base-url:http://localhost:8080}") String avatarBaseUrl) {
        return new UserDTOConverter(avatarBaseUrl);
    }

    @Bean
    RoleDTOConverter roleDTOConverter() {
        return new RoleDTOConverter();
    }

    @Bean
    PermissionDTOConverter permissionDTOConverter() {
        return new PermissionDTOConverter();
    }
}
