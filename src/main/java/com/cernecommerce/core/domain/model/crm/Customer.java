package com.cernecommerce.core.domain.model.crm;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Cliente do módulo CRM. Fundação sobre a qual as demais features de CRM
 * (listagem/RFM, perfil 360, kanban de estágio) são construídas.
 */
public record Customer(
    Long id,
    String nome,
    String contato,
    String email,
    String cpf,
    String origem,
    Instant cadastradoEm,
    CustomerStage estagio
) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Customer {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome é obrigatório");
        }
        if (contato == null || contato.isBlank()) {
            throw new IllegalArgumentException("contato é obrigatório");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email é obrigatório");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("email possui formato inválido");
        }
        if (estagio == null) {
            throw new IllegalArgumentException("estagio é obrigatório");
        }
    }

    /** Cria um novo cliente (sem id, cadastradoEm no momento atual, estágio inicial NOVO_LEAD). */
    public static Customer create(String nome, String contato, String email, String cpf, String origem) {
        return new Customer(null, nome, contato, email, cpf, origem, Instant.now(), CustomerStage.NOVO_LEAD);
    }

    /** Reconstitui um cliente a partir de persistência. */
    public static Customer of(Long id, String nome, String contato, String email, String cpf, String origem,
            Instant cadastradoEm, CustomerStage estagio) {
        return new Customer(id, nome, contato, email, cpf, origem, cadastradoEm, estagio);
    }

    /** Retorna uma cópia deste cliente com o estágio atualizado. */
    public Customer withEstagio(CustomerStage novoEstagio) {
        return new Customer(id, nome, contato, email, cpf, origem, cadastradoEm, novoEstagio);
    }
}
