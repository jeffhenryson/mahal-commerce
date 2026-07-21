package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerNoteEntity;
import com.cernecommerce.core.domain.model.crm.CustomerNote;
import com.cernecommerce.core.ports.out.crm.CustomerNoteRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public class CustomerNoteRepositoryImpl implements CustomerNoteRepository {

    private final CustomerNoteJpaRepository customerNoteJpaRepository;

    public CustomerNoteRepositoryImpl(CustomerNoteJpaRepository customerNoteJpaRepository) {
        this.customerNoteJpaRepository = customerNoteJpaRepository;
    }

    @Override
    public CustomerNote save(CustomerNote note) {
        CustomerNoteEntity entity = new CustomerNoteEntity();
        entity.setId(note.id());
        entity.setCustomerId(note.customerId());
        entity.setAutor(note.autor());
        entity.setTexto(note.texto());
        entity.setCriadoEm(note.criadoEm());
        CustomerNoteEntity saved = customerNoteJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerNote> findByCustomerId(Long customerId) {
        return customerNoteJpaRepository.findByCustomerIdOrderByCriadoEmDesc(customerId).stream()
                .map(this::toDomain)
                .toList();
    }

    private CustomerNote toDomain(CustomerNoteEntity e) {
        return CustomerNote.of(e.getId(), e.getCustomerId(), e.getAutor(), e.getTexto(), e.getCriadoEm());
    }
}
