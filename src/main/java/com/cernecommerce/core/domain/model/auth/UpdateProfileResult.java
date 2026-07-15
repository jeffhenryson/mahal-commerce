package com.cernecommerce.core.domain.model.auth;

public record UpdateProfileResult(User user, boolean emailChangePending) {}
