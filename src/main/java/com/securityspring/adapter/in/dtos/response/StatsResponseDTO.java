package com.securityspring.adapter.in.dtos.response;

public record StatsResponseDTO(
    long totalUsers,
    long activeUsers,
    long disabledUsers,
    long totalRoles,
    long totalPermissions
) {}
