package com.securityspring.infra.config;

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
    UserDTOConverter userDTOConverter() {
        return new UserDTOConverter();
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
