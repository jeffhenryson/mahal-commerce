package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

/**
 * Resposta mínima e deliberadamente pública: nada de estágio de CRM, tags ou saldo de cashback
 * aqui — o cliente recém-cadastrado ainda não está autenticado. Login é em {@code POST /auth/login}
 * com o mesmo email/senha (autenticação única, plano-pdv-marketplace.md §2.11.1).
 */
@Data
public class ShopRegisterResponseDTO {
    private Long customerId;
    private String nome;
    private String email;
}
