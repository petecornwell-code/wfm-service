package com.wfm.repository;

import com.wfm.model.JobTitleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobTitleConfigRepository extends JpaRepository<JobTitleConfig, UUID> {

    List<JobTitleConfig> findByTenantId(long tenantId);

    Optional<JobTitleConfig> findByTenantIdAndJobTitle(long tenantId, String jobTitle);

    Optional<JobTitleConfig> findByIdAndTenantId(UUID id, long tenantId);

    List<JobTitleConfig> findByTenantIdAndNonSchedulableTrue(long tenantId);
}
