package com.cernecommerce.core.ports.out.crm;

import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.crm.TagSummary;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de tags do CRM.
 */
public interface TagRepository {

    Optional<Tag> findById(Long id);

    Optional<Tag> findByNome(String nome);

    Tag save(Tag tag);

    void deleteById(Long id);

    /** Lista todas as tags com a contagem de clientes associados a cada uma. */
    List<TagSummary> findAllWithCustomerCount();
}
