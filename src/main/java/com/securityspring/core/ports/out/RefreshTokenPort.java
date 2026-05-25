package com.securityspring.core.ports.out;

public interface RefreshTokenPort {

    record RotationResult(String username, String newToken) {}

    String issue(String username);

    RotationResult rotate(String oldToken);

    void revoke(String token);

    void revokeAll(String username);
}
