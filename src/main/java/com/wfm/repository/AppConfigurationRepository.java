package com.wfm.repository;

import com.wfm.model.AppConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppConfigurationRepository extends JpaRepository<AppConfiguration, UUID> {

    Optional<AppConfiguration> findByTenantIdAndConfigKey(long tenantId, String configKey);

    List<AppConfiguration> findByTenantId(long tenantId);
}
