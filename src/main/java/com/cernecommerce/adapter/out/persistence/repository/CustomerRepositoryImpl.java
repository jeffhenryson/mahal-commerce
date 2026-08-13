package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional
public class CustomerRepositoryImpl implements CustomerRepository {

    private final CustomerJpaRepository customerJpaRepository;

    public CustomerRepositoryImpl(CustomerJpaRepository customerJpaRepository) {
        this.customerJpaRepository = customerJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findById(Long id) {
        return customerJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findByIds(Collection<Long> ids) {
        return customerJpaRepository.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findByEmail(String email) {
        return customerJpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findByCpf(String cpf) {
        return customerJpaRepository.findByCpf(cpf).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findByContato(String contato) {
        return customerJpaRepository.findFirstByContato(contato).map(this::toDomain);
    }

    @Override
    public Customer save(Customer customer) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(customer.id());
        entity.setNome(customer.nome());
        entity.setContato(customer.contato());
        entity.setEmail(customer.email());
        entity.setCpf(customer.cpf());
        entity.setOrigem(customer.origem());
        entity.setCadastradoEm(customer.cadastradoEm());
        entity.setEstagio(customer.estagio());
        CustomerEntity saved = customerJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Customer> findAll(String search, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<CustomerEntity> result = (search == null || search.isBlank())
                ? customerJpaRepository.findAll(pageRequest)
                : customerJpaRepository.findByNomeContainingIgnoreCaseOrContatoContaining(search, search, pageRequest);
        List<Customer> content = result.getContent().stream().map(this::toDomain).toList();
        return new PageResult<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findAllForExport(String search) {
        List<CustomerEntity> entities = (search == null || search.isBlank())
                ? customerJpaRepository.findAll()
                : customerJpaRepository.findAllByNomeContainingIgnoreCaseOrContatoContaining(search, search);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return customerJpaRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countActive() {
        return customerJpaRepository.countByEstagioNot(CustomerStage.INATIVO);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<CustomerStage, Long> countByStage() {
        Map<CustomerStage, Long> result = new HashMap<>();
        for (Object[] row : customerJpaRepository.countGroupedByEstagio()) {
            result.put((CustomerStage) row[0], (Long) row[1]);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findByEstagio(CustomerStage estagio) {
        return customerJpaRepository.findByEstagio(estagio).stream().map(this::toDomain).toList();
    }

    private Customer toDomain(CustomerEntity e) {
        return Customer.of(e.getId(), e.getNome(), e.getContato(), e.getEmail(), e.getCpf(), e.getOrigem(),
                e.getCadastradoEm(), e.getEstagio());
    }
}
