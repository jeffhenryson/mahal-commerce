package com.cernecommerce.core.ports.out.support;

import com.cernecommerce.core.domain.model.support.BugReport;

public interface BugReportRepository {

    BugReport save(BugReport bugReport);
}
