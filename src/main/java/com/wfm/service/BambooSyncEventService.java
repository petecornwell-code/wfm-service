package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.dto.BambooSyncEventResponse;
import com.wfm.model.BambooSyncEvent;
import com.wfm.repository.BambooSyncEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BambooSyncEventService {

    private final BambooSyncEventRepository repository;

    public BambooSyncEventService(BambooSyncEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists a BambooSyncEvent in a new transaction so the record survives a
     * rollback of the caller's transaction (RESEARCH A3 pattern).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(BambooSyncEvent event) {
        repository.save(event);
    }

    /**
     * Returns the latest sync event for the current tenant, or a "never-synced" response
     * (startedAt=null, success=false, all counts null) when no row exists.
     */
    @Transactional(readOnly = true)
    public BambooSyncEventResponse getLatest() {
        long tenantId = TenantContext.getTenantId();
        return repository.findFirstByTenantIdOrderByStartedAtDesc(tenantId)
                .map(this::toResponse)
                .orElseGet(() -> new BambooSyncEventResponse(null, null, false, null, null, null, null));
    }

    private BambooSyncEventResponse toResponse(BambooSyncEvent event) {
        return new BambooSyncEventResponse(
                event.getStartedAt(),
                event.getFinishedAt(),
                event.isSuccess(),
                event.getErrorMessage(),
                event.getAgentsSynced(),
                event.getTimeOffPulled(),
                event.getRetryAfterSeconds()
        );
    }
}
