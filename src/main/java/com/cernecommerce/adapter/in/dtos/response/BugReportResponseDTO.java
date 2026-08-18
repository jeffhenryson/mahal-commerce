package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.support.BugReport;
import java.time.Instant;

public record BugReportResponseDTO(Long id, Instant createdAt) {
    public static BugReportResponseDTO from(BugReport bugReport) {
        return new BugReportResponseDTO(bugReport.id(), bugReport.createdAt());
    }
}
