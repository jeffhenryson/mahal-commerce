package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.BugReportEntity;
import com.cernecommerce.core.domain.model.support.BugReport;
import com.cernecommerce.core.ports.out.support.BugReportRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class BugReportRepositoryImpl implements BugReportRepository {

    private final BugReportJpaRepository jpaRepo;

    public BugReportRepositoryImpl(BugReportJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    @Transactional
    public BugReport save(BugReport bugReport) {
        BugReportEntity entity = toEntity(bugReport);
        return toDomain(jpaRepo.save(entity));
    }

    private BugReportEntity toEntity(BugReport b) {
        BugReportEntity e = new BugReportEntity();
        e.setId(b.id());
        e.setReportedBy(b.reportedBy());
        e.setTitle(b.title());
        e.setDescription(b.description());
        e.setPageUrl(b.pageUrl());
        e.setUserAgent(b.userAgent());
        e.setCreatedAt(b.createdAt());
        return e;
    }

    private BugReport toDomain(BugReportEntity e) {
        return BugReport.of(e.getId(), e.getReportedBy(), e.getTitle(), e.getDescription(), e.getPageUrl(),
                e.getUserAgent(), e.getCreatedAt());
    }
}
