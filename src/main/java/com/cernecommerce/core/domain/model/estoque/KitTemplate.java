package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.model.Money;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Modelo de kit montável (EST-F031) — o "Kit Mahal": o cliente passa pelos passos (bag, seda,
 * piteira, tubeck, tesoura, cuia, isqueiro), escolhe um produto em cada, e paga a soma dos itens
 * menos {@code discountPercent}.
 *
 * <p><b>Não é o kit de EST-F015.</b> Aquele é um produto {@link ProductType#KIT} com receita
 * fixa, preço próprio e SKU vendável. Este não tem SKU nem saldo: a receita muda a cada venda, então
 * cada item escolhido vira uma linha comum do carrinho/comanda, agrupada por um id de pacote, e o
 * estoque baixa item a item pelo caminho de sempre. Modelar isto como {@code KIT} exigiria um SKU
 * por combinação possível.</p>
 */
public record KitTemplate(
        Long id,
        String name,
        String description,
        String imageUrl,
        BigDecimal discountPercent,
        boolean active,
        boolean visibleInPos,
        boolean visibleInMarketplace,
        List<KitTemplateStep> steps) {

    public static final int NAME_MAX_LENGTH = 100;

    public KitTemplate {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório");
        }
        name = name.trim();
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("name excede " + NAME_MAX_LENGTH + " caracteres");
        }
        discountPercent = discountPercent == null ? BigDecimal.ZERO
                : discountPercent.setScale(Money.PERCENT_SCALE, Money.ROUNDING);
        if (discountPercent.signum() < 0 || discountPercent.compareTo(Money.HUNDRED) >= 0) {
            throw new IllegalArgumentException("discountPercent deve estar entre 0 e 100 (exclusive): "
                    + discountPercent);
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("o kit precisa de pelo menos um passo");
        }
        Set<String> names = new HashSet<>();
        for (KitTemplateStep step : steps) {
            if (!names.add(step.name().toLowerCase())) {
                throw new IllegalArgumentException("passo repetido no kit: " + step.name());
            }
        }
        steps = steps.stream().sorted(Comparator.comparingInt(KitTemplateStep::displayOrder)).toList();
    }

    public Optional<KitTemplateStep> step(Long stepId) {
        return steps.stream().filter(s -> s.id() != null && s.id().equals(stepId)).findFirst();
    }

    /** O kit pode ser vendido no canal pedido? Inativo não é vendido em canal nenhum. */
    public boolean sellableIn(KitChannel channel) {
        if (!active) {
            return false;
        }
        return channel == KitChannel.PDV ? visibleInPos : visibleInMarketplace;
    }

    public KitTemplate withId(Long newId) {
        return new KitTemplate(newId, name, description, imageUrl, discountPercent, active, visibleInPos,
                visibleInMarketplace, steps);
    }
}
