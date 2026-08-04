package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CartJpaRepository extends JpaRepository<CartEntity, Long> {

    @Query("SELECT DISTINCT c FROM CartEntity c LEFT JOIN FETCH c.items WHERE c.customerId = :customerId")
    Optional<CartEntity> findByCustomerId(Long customerId);
}
