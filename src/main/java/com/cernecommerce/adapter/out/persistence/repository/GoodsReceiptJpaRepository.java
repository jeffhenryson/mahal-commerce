package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.GoodsReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptJpaRepository extends JpaRepository<GoodsReceiptEntity, Long> {
}
