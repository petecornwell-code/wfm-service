package com.wfm.repository;

import com.wfm.model.JobTitleIncludePattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobTitleIncludePatternRepository extends JpaRepository<JobTitleIncludePattern, UUID> {

    List<JobTitleIncludePattern> findByTenantId(long tenantId);

    Optional<JobTitleIncludePattern> findByIdAndTenantId(UUID id, long tenantId);

    Optional<JobTitleIncludePattern> findByTenantIdAndPattern(long tenantId, String pattern);
}
