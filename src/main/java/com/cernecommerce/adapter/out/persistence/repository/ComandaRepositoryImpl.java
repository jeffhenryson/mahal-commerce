package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ComandaEntity;
import com.cernecommerce.adapter.out.persistence.entity.ComandaItemEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@Transactional
public class ComandaRepositoryImpl implements ComandaRepository {

    private final ComandaJpaRepository comandaJpaRepository;

    public ComandaRepositoryImpl(ComandaJpaRepository comandaJpaRepository) {
        this.comandaJpaRepository = comandaJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Comanda> findById(Long id) {
        return comandaJpaRepository.findById(id).map(this::toDomain);
    }

    /**
     * PDV-C008 — leitura travada, obrigatória em todo caminho que muda a comanda. Sem
     * {@code readOnly}: um SELECT FOR UPDATE dentro de transação marcada como somente-leitura é
     * contraditório, e alguns drivers a rejeitam.
     */
    @Override
    public Optional<Comanda> findByIdForUpdate(Long id) {
        return comandaJpaRepository.findByIdForUpdate(id).map(this::toDomain);
    }

    /**
     * PDV-C009 — ID-first + JOIN FETCH, o padrão de {@code docs/persistence.md}: a primeira consulta
     * pagina os ids, a segunda traz as comandas da página com os itens de uma vez. Antes eram
     * {@code 1 + N} consultas, uma por mesa aberta, porque {@code toDomain} toca a coleção
     * {@code LAZY}.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<Comanda> findOpen(Long sessionId, String warehouseCode, int page, int size) {
        Page<Long> idPage = comandaJpaRepository.findOpenIds(ComandaStatus.ABERTA.name(), sessionId,
                warehouseCode, PageRequest.of(page, size));
        // Sem esta guarda o `IN :ids` sairia vazio — desnecessário, e nem todo banco o aceita.
        if (idPage.isEmpty()) {
            return new PageResult<>(List.of(), page, size, idPage.getTotalElements(), idPage.getTotalPages());
        }
        List<Comanda> content = comandaJpaRepository.findAllByIdsWithItems(idPage.getContent())
                .stream().map(this::toDomain).toList();
        return new PageResult<>(content, page, size, idPage.getTotalElements(), idPage.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findOpenIdsBySessionId(Long sessionId) {
        return comandaJpaRepository.findOpenIdsBySessionId(sessionId, ComandaStatus.ABERTA.name());
    }

    @Override
    public int moveOpenItems(Long fromComandaId, Long toComandaId) {
        return comandaJpaRepository.moveOpenItems(fromComandaId, toComandaId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findOpenIdsOlderThan(Instant cutoff, int limit) {
        return comandaJpaRepository.findOpenIdsOlderThan(ComandaStatus.ABERTA.name(), cutoff,
                PageRequest.of(0, limit));
    }

    @Override
    public Comanda save(Comanda comanda) {
        ComandaEntity entity = comanda.id() == null
                ? new ComandaEntity()
                : comandaJpaRepository.findById(comanda.id()).orElseGet(ComandaEntity::new);
        entity.setId(comanda.id());
        entity.setSessionId(comanda.sessionId());
        entity.setWarehouseCode(comanda.warehouseCode());
        entity.setTableOrCustomerLabel(comanda.tableOrCustomerLabel());
        entity.setStatus(comanda.status().name());
        entity.setCustomerId(comanda.customerId());
        entity.setOrderId(comanda.orderId());
        entity.setOpenedBy(comanda.openedBy());
        entity.setOpenedAt(comanda.openedAt());
        entity.setClosedAt(comanda.closedAt());

        // Linha já persistida é ATUALIZADA no lugar, nunca recriada. O apaga-e-reinsere que estava
        // aqui era inofensivo em PDV-F009 e deixou de ser na V114: linked_item_id aponta para o id
        // de outra linha desta mesma comanda, então trocar os ids a cada addItem faria a primeira
        // TROCA de um open rosh apontar para uma linha recém-deletada — violação de FK. E o
        // linkedItemId que o cliente recebeu tem que continuar valendo no lançamento seguinte.
        Map<Long, ComandaItem> incoming = comanda.items().stream()
                .filter(item -> item.id() != null)
                .collect(Collectors.toMap(ComandaItem::id, Function.identity()));
        // PDV-F012 — a remoção de linha existe desde `DELETE /pdv/comandas/{id}/items/{itemId}`, e
        // a ORDEM em que as órfãs saem importa: `linked_item_id` é FK auto-referente (V114), e
        // apagar a linha PAI antes da TROCA que aponta para ela viola a constraint. Removendo em
        // ordem DECRESCENTE de id, a filha sempre sai primeiro — o domínio garante `id do pai < id
        // da filha`, porque `resolveLinkedItem` exige que a linha de origem já exista na comanda no
        // momento do lançamento.
        //
        // ⚠️ Nenhum teste pega isto: `linkedItemId` é mapeado como coluna Long simples, não
        // @ManyToOne, então o schema de `ddl-auto` (H2, perfil dev das ITs) NÃO tem a FK — só o
        // Postgres real, via migration. A correção é por construção, não por cobertura.
        List<ComandaItemEntity> orfas = entity.getItems().stream()
                .filter(e -> e.getId() != null && !incoming.containsKey(e.getId()))
                .sorted(Comparator.comparing(ComandaItemEntity::getId).reversed())
                .toList();
        orfas.forEach(entity.getItems()::remove);

        Map<Long, ComandaItemEntity> persisted = entity.getItems().stream()
                .filter(e -> e.getId() != null)
                .collect(Collectors.toMap(ComandaItemEntity::getId, Function.identity()));
        for (ComandaItem item : comanda.items()) {
            ComandaItemEntity itemEntity = item.id() == null ? null : persisted.get(item.id());
            if (itemEntity == null) {
                itemEntity = new ComandaItemEntity();
                itemEntity.setComanda(entity);
                entity.getItems().add(itemEntity);
            }
            itemEntity.setSku(item.sku());
            itemEntity.setQuantity(item.quantity());
            itemEntity.setUnitPrice(item.unitPrice());
            itemEntity.setCostPrice(item.costPrice());
            itemEntity.setProductName(item.productName());
            itemEntity.setAddedAt(item.addedAt());
            itemEntity.setMode(item.mode().name());
            itemEntity.setCourtesy(item.courtesy());
            itemEntity.setLinkedItemId(item.linkedItemId());
            itemEntity.setNotes(item.notes());
            itemEntity.setSurchargeAmount(item.surchargeAmount());
            itemEntity.setClosedInOrderId(item.closedInOrderId());
            itemEntity.setPackageUses(item.packageUses());
            itemEntity.setPackageSessionsPerUnit(item.packageSessionsPerUnit());
            itemEntity.setKitBundleId(item.kitBundleId());
            itemEntity.setKitTemplateId(item.kitTemplateId());
            itemEntity.setKitDiscountAmount(item.kitDiscountAmount());
        }
        return toDomain(comandaJpaRepository.save(entity));
    }

    private Comanda toDomain(ComandaEntity e) {
        return Comanda.of(e.getId(), e.getSessionId(), e.getWarehouseCode(), e.getTableOrCustomerLabel(),
                e.getCustomerId(), ComandaStatus.valueOf(e.getStatus()),
                e.getItems().stream().map(this::toDomain).toList(),
                e.getOrderId(), e.getOpenedBy(), e.getOpenedAt(), e.getClosedAt());
    }

    private ComandaItem toDomain(ComandaItemEntity e) {
        return ComandaItem.of(e.getId(), e.getSku(), e.getQuantity(), e.getUnitPrice(), e.getCostPrice(),
                e.getProductName(), e.getAddedAt(),
                // Dado legado (linha anterior a PDV-F010) lê como NORMAL — o DEFAULT da migration
                // cobre as linhas já gravadas, e este null-check cobre carga direta.
                e.getMode() == null ? ConsumptionMode.NORMAL : ConsumptionMode.valueOf(e.getMode()),
                e.isCourtesy(), e.getLinkedItemId(), e.getNotes(), e.getSurchargeAmount(),
                e.getClosedInOrderId(), e.getPackageUses(), e.getPackageSessionsPerUnit(), e.getKitBundleId(),
                e.getKitTemplateId(), e.getKitDiscountAmount());
    }
}
