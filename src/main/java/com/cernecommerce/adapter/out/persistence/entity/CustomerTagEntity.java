package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "customer_tags")
@IdClass(CustomerTagEntity.Id.class)
public class CustomerTagEntity {

    @jakarta.persistence.Id
    @Column(name = "customer_id")
    private Long customerId;

    @jakarta.persistence.Id
    @Column(name = "tag_id")
    private Long tagId;

    public static class Id implements Serializable {
        private Long customerId;
        private Long tagId;

        public Id() {}

        public Id(Long customerId, Long tagId) {
            this.customerId = customerId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(customerId, id.customerId) && Objects.equals(tagId, id.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(customerId, tagId);
        }
    }
}
