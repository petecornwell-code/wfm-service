package com.wfm.repository;

import com.wfm.model.BambooSyncEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BambooSyncEventRepository extends JpaRepository<BambooSyncEvent, UUID> {

    Optional<BambooSyncEvent> findFirstByTenantIdOrderByStartedAtDesc(long tenantId);
}
