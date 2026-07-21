package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.CustomerResponseDTO;
import com.cernecommerce.core.domain.model.crm.Customer;

import java.math.BigDecimal;
import java.util.List;

public class CustomerDTOConverter {

    // Placeholder até os domínios de pedidos e cashback existirem — ver crm/listagem-clientes-rfm.
    private static final BigDecimal LTV_PLACEHOLDER = BigDecimal.ZERO;
    private static final BigDecimal CASHBACK_PLACEHOLDER = BigDecimal.ZERO;
    private static final String SEGMENTO_PLACEHOLDER = "NOVO";

    /**
     * Converte sem buscar as tags reais — usado para clientes recém-criados (sem tags ainda)
     * e para a listagem paginada (evita N+1: buscar tags por cliente a cada linha da página).
     */
    public CustomerResponseDTO toResponse(Customer customer) {
        return toResponse(customer, List.of());
    }

    /** Converte com a lista real de nomes de tags associadas ao cliente. */
    public CustomerResponseDTO toResponse(Customer customer, List<String> tagNomes) {
        CustomerResponseDTO dto = new CustomerResponseDTO();
        dto.setId(customer.id());
        dto.setNome(customer.nome());
        dto.setContato(customer.contato());
        dto.setEmail(customer.email());
        dto.setCpf(customer.cpf());
        dto.setOrigem(customer.origem());
        dto.setCadastradoEm(customer.cadastradoEm());
        dto.setEstagio(customer.estagio());
        dto.setLtv(LTV_PLACEHOLDER);
        dto.setCashback(CASHBACK_PLACEHOLDER);
        dto.setSegmento(SEGMENTO_PLACEHOLDER);
        dto.setTags(tagNomes);
        return dto;
    }
}
