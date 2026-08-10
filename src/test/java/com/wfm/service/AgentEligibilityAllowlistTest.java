package com.wfm.service;

import com.wfm.model.JobTitleIncludePattern;
import com.wfm.repository.JobTitleConfigRepository;
import com.wfm.repository.JobTitleIncludePatternRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Matching semantics of the job-title allowlist
 * ({@link AgentEligibilityService#isIncludedByTitleAllowlist}).
 */
class AgentEligibilityAllowlistTest {

    private static final long TENANT = 1L;
    private static final String CSR = "Customer Support Representative";

    private AgentEligibilityService serviceWith(String... patterns) {
        JobTitleIncludePatternRepository patternRepo = mock(JobTitleIncludePatternRepository.class);
        when(patternRepo.findByTenantId(anyLong())).thenReturn(
                List.of(patterns).stream().map(p -> {
                    JobTitleIncludePattern e = new JobTitleIncludePattern();
                    e.setTenantId(TENANT);
                    e.setPattern(p);
                    return e;
                }).toList());
        return new AgentEligibilityService(mock(JobTitleConfigRepository.class), patternRepo);
    }

    @Test
    void noPatterns_allowlistInactive_everyTitlePasses() {
        AgentEligibilityService service = serviceWith();

        assertThat(service.isIncludedByTitleAllowlist(TENANT, CSR)).isTrue();
        assertThat(service.isIncludedByTitleAllowlist(TENANT, "Quality Assurance")).isTrue();
        assertThat(service.isIncludedByTitleAllowlist(TENANT, null)).isTrue();
        assertThat(service.isIncludedByTitleAllowlist(TENANT, "")).isTrue();
    }

    @Test
    void exactTitleMatches() {
        assertThat(serviceWith(CSR).isIncludedByTitleAllowlist(TENANT, CSR)).isTrue();
    }

    @Test
    void substringMatch_prefixAndSuffixVariantsAreIncluded() {
        AgentEligibilityService service = serviceWith(CSR);

        assertThat(service.isIncludedByTitleAllowlist(TENANT, "Senior " + CSR)).isTrue();
        assertThat(service.isIncludedByTitleAllowlist(TENANT, CSR + " II")).isTrue();
        assertThat(service.isIncludedByTitleAllowlist(TENANT, "Senior " + CSR + " (Nights)")).isTrue();
    }

    @Test
    void matchIsCaseInsensitive() {
        AgentEligibilityService service = serviceWith(CSR);

        assertThat(service.isIncludedByTitleAllowlist(TENANT, "customer support representative")).isTrue();
        assertThat(service.isIncludedByTitleAllowlist(TENANT, "CUSTOMER SUPPORT REPRESENTATIVE")).isTrue();
    }

    @Test
    void nonMatchingTitleIsExcludedWhenAllowlistActive() {
        AgentEligibilityService service = serviceWith(CSR);

        assertThat(service.isIncludedByTitleAllowlist(TENANT, "Quality Assurance")).isFalse();
        assertThat(service.isIncludedByTitleAllowlist(TENANT, "Team Lead")).isFalse();
    }

    @Test
    void nullOrBlankTitleIsExcludedWhenAllowlistActive() {
        AgentEligibilityService service = serviceWith(CSR);

        assertThat(service.isIncludedByTitleAllowlist(TENANT, null)).isFalse();
        assertThat(service.isIncludedByTitleAllowlist(TENANT, "   ")).isFalse();
    }

    @Test
    void anyOfSeveralPatternsMatches() {
        AgentEligibilityService service = serviceWith(CSR, "Technical Support");

        assertThat(service.isIncludedByTitleAllowlist(TENANT, "Senior " + CSR)).isTrue();
        assertThat(service.isIncludedByTitleAllowlist(TENANT, "Technical Support Engineer")).isTrue();
        assertThat(service.isIncludedByTitleAllowlist(TENANT, "Finance Analyst")).isFalse();
    }

    @Test
    void blankStoredPatternIsIgnored_doesNotMatchEverything() {
        // Defence in depth: the service rejects blank patterns on write, but a blank that
        // reached the table must not silently re-include every title.
        AgentEligibilityService service = serviceWith("   ", CSR);

        assertThat(service.isIncludedByTitleAllowlist(TENANT, "Finance Analyst")).isFalse();
        assertThat(service.isIncludedByTitleAllowlist(TENANT, CSR)).isTrue();
    }
}
