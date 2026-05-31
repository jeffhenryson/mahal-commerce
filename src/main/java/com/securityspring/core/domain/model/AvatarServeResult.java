package com.securityspring.core.domain.model;

public sealed interface AvatarServeResult {
    record Redirect(String url) implements AvatarServeResult {}
    record LocalFile(byte[] bytes, String extension) implements AvatarServeResult {}
    record NotFound() implements AvatarServeResult {}
}
