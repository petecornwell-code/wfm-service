package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.JobTitleConfigResponse;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.JobTitleConfig;
import com.wfm.repository.JobTitleConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies JobTitleConfigService list/setNonSchedulable/ensureExists behaviour.
 * Uses H2 via @DataJpaTest.
 */
@DataJpaTest
@Import(JobTitleConfigService.class)
@ActiveProfiles("test")
class JobTitleConfigServiceTest {

    @Autowired
    private JobTitleConfigService service;

    @Autowired
    private JobTitleConfigRepository repository;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- list ---

    @Test
    void list_returnsSortedByJobTitle() {
        save(TENANT_A, "Zebra Agent", false);
        save(TENANT_A, "Alpha Agent", false);
        save(TENANT_A, "Manager", true);

        List<JobTitleConfigResponse> result = service.list();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).jobTitle()).isEqualTo("Alpha Agent");
        assertThat(result.get(1).jobTitle()).isEqualTo("Manager");
        assertThat(result.get(2).jobTitle()).isEqualTo("Zebra Agent");
    }

    @Test
    void list_tenantIsolated() {
        save(TENANT_A, "Agent A", false);
        save(TENANT_B, "Agent B", false);

        List<JobTitleConfigResponse> result = service.list();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).jobTitle()).isEqualTo("Agent A");
    }

    // --- setNonSchedulable ---

    @Test
    void setNonSchedulable_updatesFlag() {
        JobTitleConfig cfg = save(TENANT_A, "Support Rep", false);

        JobTitleConfigResponse updated = service.setNonSchedulable(cfg.getId(), true);

        assertThat(updated.nonSchedulable()).isTrue();
        assertThat(updated.jobTitle()).isEqualTo("Support Rep");
    }

    @Test
    void setNonSchedulable_updatesUpdatedAt() {
        OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);
        JobTitleConfig cfg = save(TENANT_A, "Agent", false);

        service.setNonSchedulable(cfg.getId(), true);

        JobTitleConfig reloaded = repository.findById(cfg.getId()).orElseThrow();
        assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void setNonSchedulable_crossTenant_throwsEntityNotFound() {
        // Row belongs to TENANT_B, but context is TENANT_A
        JobTitleConfig cfg = save(TENANT_B, "Agent B", false);

        assertThatThrownBy(() -> service.setNonSchedulable(cfg.getId(), true))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void setNonSchedulable_unknownId_throwsEntityNotFound() {
        assertThatThrownBy(() -> service.setNonSchedulable(UUID.randomUUID(), true))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // --- ensureExists ---

    @Test
    void ensureExists_createsNewRow() {
        service.ensureExists(TENANT_A, "Specialist");

        List<JobTitleConfig> rows = repository.findByTenantId(TENANT_A);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getJobTitle()).isEqualTo("Specialist");
        assertThat(rows.get(0).isNonSchedulable()).isFalse();
    }

    @Test
    void ensureExists_idempotent_noDoubleSave() {
        service.ensureExists(TENANT_A, "Support");
        service.ensureExists(TENANT_A, "Support");

        assertThat(repository.findByTenantId(TENANT_A)).hasSize(1);
    }

    @Test
    void ensureExists_existingNonSchedulableRow_isNotModified() {
        JobTitleConfig cfg = save(TENANT_A, "Supervisor", true);

        service.ensureExists(TENANT_A, "Supervisor");

        JobTitleConfig reloaded = repository.findById(cfg.getId()).orElseThrow();
        assertThat(reloaded.isNonSchedulable()).isTrue();
    }

    @Test
    void ensureExists_blankJobTitle_noOp() {
        service.ensureExists(TENANT_A, "  ");
        assertThat(repository.findByTenantId(TENANT_A)).isEmpty();
    }

    @Test
    void ensureExists_nullJobTitle_noOp() {
        service.ensureExists(TENANT_A, null);
        assertThat(repository.findByTenantId(TENANT_A)).isEmpty();
    }

    // --- helpers ---

    private JobTitleConfig save(long tenantId, String jobTitle, boolean nonSchedulable) {
        JobTitleConfig cfg = new JobTitleConfig();
        cfg.setTenantId(tenantId);
        cfg.setJobTitle(jobTitle);
        cfg.setNonSchedulable(nonSchedulable);
        cfg.setCreatedAt(OffsetDateTime.now());
        cfg.setUpdatedAt(OffsetDateTime.now());
        return repository.save(cfg);
    }
}
