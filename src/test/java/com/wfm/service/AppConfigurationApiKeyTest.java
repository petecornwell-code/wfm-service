package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.AppConfiguration;
import com.wfm.repository.AppConfigurationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BambooHR API key moved from the database to AWS Secrets Manager (BAMBOOHR_API_KEY ->
 * bamboohr.api-key). It must never be read from or written to app_configuration, and never be
 * returned to the browser -- only whether it is set.
 */
@DataJpaTest
@ActiveProfiles("test")
class AppConfigurationApiKeyTest {

    @Autowired
    private AppConfigurationRepository repository;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        // A copy left in the database from before the move -- must be ignored and never exposed.
        AppConfiguration legacy = new AppConfiguration();
        legacy.setTenantId(1L);
        legacy.setConfigKey(AppConfigurationService.BAMBOOHR_API_KEY);
        legacy.setConfigValue("old-db-key");
        repository.save(legacy);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void theClientGetsTheKeyFromTheEnvironment_notTheDatabase() {
        AppConfigurationService service = new AppConfigurationService(repository, "env-key");

        assertThat(service.getConfigValue(AppConfigurationService.BAMBOOHR_API_KEY)).isEqualTo("env-key");
    }

    @Test
    void theConfigurationResponse_neverContainsTheKey_onlyWhetherItIsSet() {
        Map<String, String> withKey = new AppConfigurationService(repository, "env-key").getAllConfig();
        Map<String, String> withoutKey = new AppConfigurationService(repository, "").getAllConfig();

        assertThat(withKey).doesNotContainKey(AppConfigurationService.BAMBOOHR_API_KEY)
                .containsEntry(AppConfigurationService.BAMBOOHR_API_KEY_CONFIGURED, "true");
        assertThat(withKey.values()).doesNotContain("env-key", "old-db-key");
        assertThat(withoutKey).containsEntry(AppConfigurationService.BAMBOOHR_API_KEY_CONFIGURED, "false");
        // With no environment key the client sees none -- the old database copy is not a fallback.
        assertThat(new AppConfigurationService(repository, "").getConfigValue(AppConfigurationService.BAMBOOHR_API_KEY)).isEmpty();
    }

    @Test
    void savingAKeyFromTheBrowser_isIgnored() {
        AppConfigurationService service = new AppConfigurationService(repository, "env-key");

        service.saveConfig(Map.of(
                AppConfigurationService.BAMBOOHR_API_KEY, "typed-in-key",
                AppConfigurationService.BAMBOOHR_API_KEY_CONFIGURED, "false",
                AppConfigurationService.BAMBOOHR_SERVER, "helpware"));

        assertThat(repository.findByTenantIdAndConfigKey(1L, AppConfigurationService.BAMBOOHR_API_KEY))
                .get().extracting(AppConfiguration::getConfigValue).isEqualTo("old-db-key");   // untouched
        assertThat(repository.findByTenantIdAndConfigKey(1L, AppConfigurationService.BAMBOOHR_API_KEY_CONFIGURED)).isEmpty();
        assertThat(service.getConfigValue(AppConfigurationService.BAMBOOHR_SERVER)).isEqualTo("helpware");
        assertThat(service.getConfigValue(AppConfigurationService.BAMBOOHR_API_KEY)).isEqualTo("env-key");
    }
}
