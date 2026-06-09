package com.securityspring.adapter.in.dtos.request;

public record UpdateNotificationPreferenceRequest(boolean inAppEnabled, boolean emailEnabled) {
}
