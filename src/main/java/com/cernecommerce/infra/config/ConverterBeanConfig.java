package com.cernecommerce.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cernecommerce.adapter.in.converter.CampaignDTOConverter;
import com.cernecommerce.adapter.in.converter.CustomerCsvConverter;
import com.cernecommerce.adapter.in.converter.CustomerDTOConverter;
import com.cernecommerce.adapter.in.converter.CustomerNoteDTOConverter;
import com.cernecommerce.adapter.in.converter.PermissionDTOConverter;
import com.cernecommerce.adapter.in.converter.StageTransitionDTOConverter;
import com.cernecommerce.adapter.in.converter.TagDTOConverter;
import com.cernecommerce.adapter.in.converter.ProductDTOConverter;
import com.cernecommerce.adapter.in.converter.RoleDTOConverter;
import com.cernecommerce.adapter.in.converter.UserDTOConverter;
import com.cernecommerce.adapter.in.converter.WarehouseDTOConverter;
import com.cernecommerce.adapter.out.persistence.converter.UserEntityConverter;

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

    @Bean
    ProductDTOConverter productDTOConverter() {
        return new ProductDTOConverter();
    }

    @Bean
    WarehouseDTOConverter warehouseDTOConverter() {
        return new WarehouseDTOConverter();
    }

    @Bean
    CustomerDTOConverter customerDTOConverter() {
        return new CustomerDTOConverter();
    }

    @Bean
    CustomerNoteDTOConverter customerNoteDTOConverter() {
        return new CustomerNoteDTOConverter();
    }

    @Bean
    StageTransitionDTOConverter stageTransitionDTOConverter() {
        return new StageTransitionDTOConverter();
    }

    @Bean
    TagDTOConverter tagDTOConverter() {
        return new TagDTOConverter();
    }

    @Bean
    CustomerCsvConverter customerCsvConverter() {
        return new CustomerCsvConverter();
    }

    @Bean
    CampaignDTOConverter campaignDTOConverter() {
        return new CampaignDTOConverter();
    }
}
