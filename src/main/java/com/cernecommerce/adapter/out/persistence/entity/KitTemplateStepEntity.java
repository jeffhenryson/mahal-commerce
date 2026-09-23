package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "kit_template_step")
public class KitTemplateStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kit_template_id", nullable = false)
    @ToString.Exclude
    private KitTemplateEntity template;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // Id solto, não @ManyToOne: o passo só precisa do id para filtrar o catálogo, e a categoria é
    // de outro agregado. A FK existe no Postgres (V126).
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "required_step", nullable = false)
    private boolean required;

    @Column(name = "max_items", nullable = false)
    private int maxItems;
}
