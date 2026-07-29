package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerEntity;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByEmail(String email);

    Optional<CustomerEntity> findByCpf(String cpf);

    Optional<CustomerEntity> findFirstByContato(String contato);

    Page<CustomerEntity> findByNomeContainingIgnoreCaseOrContatoContaining(String nome, String contato,
            Pageable pageable);

    List<CustomerEntity> findAllByNomeContainingIgnoreCaseOrContatoContaining(String nome, String contato);

    long countByEstagioNot(CustomerStage estagio);

    List<CustomerEntity> findByEstagio(CustomerStage estagio);

    @Query("SELECT c.estagio, COUNT(c) FROM CustomerEntity c GROUP BY c.estagio")
    List<Object[]> countGroupedByEstagio();
}
