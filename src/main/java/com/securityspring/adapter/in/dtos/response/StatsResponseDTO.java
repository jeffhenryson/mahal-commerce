package com.securityspring.adapter.in.dtos.response;

public record StatsResponseDTO(
    long totalUsers,
    long activeUsers,
    long totalRoles,
    long totalPermissions
) {}
