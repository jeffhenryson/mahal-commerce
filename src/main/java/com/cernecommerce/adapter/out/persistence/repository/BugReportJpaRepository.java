package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.BugReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BugReportJpaRepository extends JpaRepository<BugReportEntity, Long> {
}
