package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.support.BugReport;

/**
 * Port de entrada do domínio <b>support</b> — relato de bugs enviado pelo admin.
 */
public interface BugReportUseCase {

    /** Registra um novo relato de bug, associado ao usuário autenticado que o enviou. */
    BugReport createBugReport(String reportedBy, String title, String description, String pageUrl,
            String userAgent);
}
