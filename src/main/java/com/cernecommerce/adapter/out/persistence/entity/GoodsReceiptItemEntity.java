package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "goods_receipt_item")
public class GoodsReceiptItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_receipt_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_goods_receipt_item_receipt"))
    @ToString.Exclude
    private GoodsReceiptEntity goodsReceipt;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;
}
