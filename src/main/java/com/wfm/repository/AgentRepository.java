package com.wfm.repository;

import com.wfm.model.Agent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    List<Agent> findByTenantId(long tenantId, Pageable pageable);

    Optional<Agent> findByIdAndTenantId(UUID id, long tenantId);

    Optional<Agent> findByTenantIdAndBamboohrId(long tenantId, String bamboohrId);

    List<Agent> findByTenantIdAndNameContainingIgnoreCase(long tenantId, String name, Pageable pageable);

    /**
     * {@code :search} is CAST on both occurrences, and that is load-bearing on Postgres — not
     * defensive noise. An untyped JDBC null reaches Postgres as {@code unknown}, which it resolves
     * to {@code bytea} inside {@code lower('%' || ? || '%')}, and {@code lower(bytea)} has no
     * overload: the whole query fails with "function lower(bytea) does not exist". The symptom was
     * that the DEFAULT listing — {@code GET /api/v1/agents} with no parameters — returned 500,
     * while {@code ?search=} (an empty string, which types fine) returned 200.
     *
     * <p>H2 tolerates the untyped null, and the test suite runs on H2, so no test can reproduce
     * this. The cast is the guard; see {@code AgentServiceListFilterTest} for the behavioural
     * contract it must preserve.
     *
     * <p>Both occurrences are cast deliberately. SQL does not guarantee the {@code IS NULL} branch
     * short-circuits before the {@code CONCAT} is typed, and the reported failure came from the
     * CONCAT side.
     */
    @Query("SELECT a FROM Agent a WHERE a.tenantId = :tenantId " +
           "AND (CAST(:search AS String) IS NULL "
           + "OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))) " +
           "AND (:unassigned = false OR a.deskId IS NULL) " +
           "ORDER BY a.name, a.id")
    List<Agent> findFiltered(long tenantId, String search, boolean unassigned, Pageable pageable);

    /** Cursor-paged twin of {@link #findFiltered} — same {@code :search} typing, same reason. */
    @Query("SELECT a FROM Agent a WHERE a.tenantId = :tenantId " +
           "AND (CAST(:search AS String) IS NULL "
           + "OR LOWER(a.name) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))) " +
           "AND (:unassigned = false OR a.deskId IS NULL) " +
           "AND (a.name > :cursorName OR (a.name = :cursorName AND a.id > :cursorId)) " +
           "ORDER BY a.name, a.id")
    List<Agent> findFilteredAfterCursor(long tenantId, String search, boolean unassigned,
                                         String cursorName, UUID cursorId, Pageable pageable);

    List<Agent> findAllByIdInAndTenantId(List<UUID> ids, long tenantId);

    Optional<Agent> findByTenantIdAndEmailIgnoreCase(long tenantId, String email);

    Optional<Agent> findByTenantIdAndDeskIdAndEmailIgnoreCase(long tenantId, UUID deskId, String email);

    Optional<Agent> findByTenantIdAndNameIgnoreCase(long tenantId, String name);

    // --- Desk-scoped queries (formerly on DeskAgentRepository) ---

    @EntityGraph(attributePaths = {"primarySpecialization", "secondarySpecializations"})
    List<Agent> findByTenantIdAndDeskId(long tenantId, UUID deskId);

    @EntityGraph(attributePaths = {"primarySpecialization", "secondarySpecializations"})
    Optional<Agent> findByIdAndTenantIdAndDeskId(UUID id, long tenantId, UUID deskId);

    long countByTenantIdAndDeskId(long tenantId, UUID deskId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Agent a " +
           "WHERE a.primarySpecialization.id = :specId")
    boolean existsByPrimarySpecialization_Id(UUID specId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Agent a " +
           "JOIN a.secondarySpecializations s WHERE s.id = :specId")
    boolean existsBySecondarySpecializationsContaining(UUID specId);
}
