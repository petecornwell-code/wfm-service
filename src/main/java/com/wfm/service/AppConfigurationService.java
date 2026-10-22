package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.AppConfiguration;
import com.wfm.repository.AppConfigurationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class AppConfigurationService {

    public static final String BAMBOOHR_SERVER = "bamboohr.server";
    /**
     * The BambooHR API key. Not stored in the database and never returned to the browser: it comes
     * from the {@code bamboohr.api-key} property, which the ECS task fills from the
     * {@code BAMBOOHR_API_KEY} environment variable, itself injected from AWS Secrets Manager
     * (infra/bamboohr.tf). {@link #getConfigValue} still answers this key so the BambooHR clients
     * read it the way they always have.
     */
    public static final String BAMBOOHR_API_KEY = "bamboohr.apiKey";
    /** Read-only flag in {@link #getAllConfig}: whether the deployment has an API key at all. */
    public static final String BAMBOOHR_API_KEY_CONFIGURED = "bamboohr.apiKeyConfigured";
    public static final String BAMBOOHR_CACHE_MAX_SIZE = "bamboohr.cache.maxSize";

    private final AppConfigurationRepository repository;
    private final String apiKey;

    public AppConfigurationService(AppConfigurationRepository repository,
                                   @Value("${bamboohr.api-key:}") String apiKey) {
        this.repository = repository;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Transactional(readOnly = true)
    public Map<String, String> getAllConfig() {
        long tenantId = TenantContext.getTenantId();
        Map<String, String> config = new HashMap<>();
        repository.findByTenantId(tenantId).forEach(c -> config.put(c.getConfigKey(), c.getConfigValue()));
        // Never expose the key; say only whether one is set.
        config.remove(BAMBOOHR_API_KEY);
        config.put(BAMBOOHR_API_KEY_CONFIGURED, String.valueOf(!apiKey.isEmpty()));
        return config;
    }

    @Transactional(readOnly = true)
    public String getConfigValue(String key) {
        if (BAMBOOHR_API_KEY.equals(key)) {
            return apiKey;
        }
        long tenantId = TenantContext.getTenantId();
        return repository.findByTenantIdAndConfigKey(tenantId, key)
                .map(AppConfiguration::getConfigValue)
                .orElse("");
    }

    @Transactional
    public Map<String, String> saveConfig(Map<String, String> entries) {
        long tenantId = TenantContext.getTenantId();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            // The API key lives in AWS Secrets Manager and the flag is derived: neither is saved here.
            if (BAMBOOHR_API_KEY.equals(entry.getKey()) || BAMBOOHR_API_KEY_CONFIGURED.equals(entry.getKey())) {
                continue;
            }
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
