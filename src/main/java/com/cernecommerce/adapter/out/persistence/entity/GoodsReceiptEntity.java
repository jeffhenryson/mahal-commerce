package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "goods_receipt")
public class GoodsReceiptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<GoodsReceiptItemEntity> items = new ArrayList<>();
}
