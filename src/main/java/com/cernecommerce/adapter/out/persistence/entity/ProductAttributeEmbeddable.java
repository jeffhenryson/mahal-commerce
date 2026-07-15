package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class ProductAttributeEmbeddable {

    @Column(name = "attr_type", nullable = false, length = 50)
    private String type;

    @Column(name = "attr_value", nullable = false, length = 100)
    private String value;
}
