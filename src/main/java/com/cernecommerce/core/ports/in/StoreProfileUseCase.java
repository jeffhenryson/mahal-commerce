package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.config.StoreProfile;

/** Perfil da loja impresso no cupom de venda. */
public interface StoreProfileUseCase {

    StoreProfile get();

    StoreProfile update(StoreProfile profile, String updatedBy);
}
