package com.cernecommerce.core.domain.model.support;

import java.time.Instant;

/** Relato de bug enviado pelo botão "Reportar bug" do admin. */
public record BugReport(
    Long id,
    String reportedBy,
    String title,
    String description,
    String pageUrl,
    String userAgent,
    Instant createdAt
) {

    public BugReport {
        if (reportedBy == null || reportedBy.isBlank()) {
            throw new IllegalArgumentException("reportedBy é obrigatório");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title é obrigatório");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description é obrigatório");
        }
    }

    /** Cria um novo relato (sem id, createdAt no momento atual). */
    public static BugReport create(String reportedBy, String title, String description, String pageUrl,
            String userAgent) {
        return new BugReport(null, reportedBy, title, description, pageUrl, userAgent, Instant.now());
    }

    /** Reconstitui um relato a partir de persistência. */
    public static BugReport of(Long id, String reportedBy, String title, String description, String pageUrl,
            String userAgent, Instant createdAt) {
        return new BugReport(id, reportedBy, title, description, pageUrl, userAgent, createdAt);
    }
}
