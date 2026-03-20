package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.AppConfiguration;
import com.wfm.repository.AppConfigurationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class AppConfigurationService {

    public static final String BAMBOOHR_SERVER = "bamboohr.server";
    public static final String BAMBOOHR_API_KEY = "bamboohr.apiKey";

    private final AppConfigurationRepository repository;

    public AppConfigurationService(AppConfigurationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, String> getAllConfig() {
        long tenantId = TenantContext.getTenantId();
        Map<String, String> config = new HashMap<>();
        repository.findByTenantId(tenantId).forEach(c -> config.put(c.getConfigKey(), c.getConfigValue()));
        return config;
    }

    @Transactional(readOnly = true)
    public String getConfigValue(String key) {
        long tenantId = TenantContext.getTenantId();
        return repository.findByTenantIdAndConfigKey(tenantId, key)
                .map(AppConfiguration::getConfigValue)
                .orElse("");
    }

    @Transactional
    public Map<String, String> saveConfig(Map<String, String> entries) {
        long tenantId = TenantContext.getTenantId();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            AppConfiguration config = repository.findByTenantIdAndConfigKey(tenantId, entry.getKey())
                    .orElseGet(() -> {
                        AppConfiguration c = new AppConfiguration();
                        c.setTenantId(tenantId);
                        c.setConfigKey(entry.getKey());
                        return c;
                    });
            config.setConfigValue(entry.getValue() != null ? entry.getValue() : "");
            repository.save(config);
        }
        return getAllConfig();
    }
}
