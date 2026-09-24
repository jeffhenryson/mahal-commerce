package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerEntity;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long> {

    Optional<CustomerEntity> findByEmail(String email);

    Optional<CustomerEntity> findByCpf(String cpf);

    /**
     * Compara só os dígitos do contato (CRM-C006): "(83) 99999-0000" e "83999990000" são o mesmo
     * telefone. Cadeia de {@code replace} em vez de {@code regexp_replace} para rodar igual no
     * Postgres e no H2 dos testes.
     */
    @Query("""
            SELECT c FROM CustomerEntity c
            WHERE replace(replace(replace(replace(replace(replace(c.contato,
                  ' ', ''), '(', ''), ')', ''), '-', ''), '+', ''), '.', '') = :digits
            ORDER BY c.id
            """)
    List<CustomerEntity> findByContatoDigits(@Param("digits") String digits, Pageable pageable);

    /** Busca livre por nome, contato, email ou CPF (CRM-C006 incluiu email e CPF). */
    @Query("""
            SELECT c FROM CustomerEntity c
            WHERE lower(c.nome) LIKE lower(concat('%', :search, '%'))
               OR c.contato LIKE concat('%', :search, '%')
               OR lower(c.email) LIKE lower(concat('%', :search, '%'))
               OR (:cpfDigits IS NOT NULL AND c.cpf LIKE concat('%', :cpfDigits, '%'))
            """)
    Page<CustomerEntity> search(@Param("search") String search, @Param("cpfDigits") String cpfDigits,
            Pageable pageable);

    @Query("""
            SELECT c FROM CustomerEntity c
            WHERE lower(c.nome) LIKE lower(concat('%', :search, '%'))
               OR c.contato LIKE concat('%', :search, '%')
               OR lower(c.email) LIKE lower(concat('%', :search, '%'))
               OR (:cpfDigits IS NOT NULL AND c.cpf LIKE concat('%', :cpfDigits, '%'))
            ORDER BY c.cadastradoEm DESC
            """)
    List<CustomerEntity> searchAll(@Param("search") String search, @Param("cpfDigits") String cpfDigits);

    long countByEstagioNot(CustomerStage estagio);

    List<CustomerEntity> findByEstagio(CustomerStage estagio);

    @Query("SELECT c.estagio, COUNT(c) FROM CustomerEntity c GROUP BY c.estagio")
    List<Object[]> countGroupedByEstagio();
}
