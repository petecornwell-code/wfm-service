package com.wfm.repository;

import com.wfm.model.ConstraintWeights;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConstraintWeightsRepository extends JpaRepository<ConstraintWeights, UUID> {

    Optional<ConstraintWeights> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    void deleteByTenantIdAndDeskId(long tenantId, UUID deskId);
}
