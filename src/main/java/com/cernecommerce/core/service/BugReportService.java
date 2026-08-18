package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.support.BugReport;
import com.cernecommerce.core.ports.in.BugReportUseCase;
import com.cernecommerce.core.ports.out.support.BugReportRepository;
import org.springframework.transaction.annotation.Transactional;

public class BugReportService implements BugReportUseCase {

    private final BugReportRepository bugReportRepository;

    public BugReportService(BugReportRepository bugReportRepository) {
        this.bugReportRepository = bugReportRepository;
    }

    @Override
    @Transactional
    public BugReport createBugReport(String reportedBy, String title, String description, String pageUrl,
            String userAgent) {
        return bugReportRepository.save(BugReport.create(reportedBy, title, description, pageUrl, userAgent));
    }
}
