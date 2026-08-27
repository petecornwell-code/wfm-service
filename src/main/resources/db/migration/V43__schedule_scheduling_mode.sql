-- Phase 15 gap closure (CR-02): schedule.scheduling_mode records the mode THIS schedule was
-- actually solved under, written by ScheduleService.acceptSchedule from the in-memory Schedule's
-- own recorded mode (SolverService.buildSchedule always sets it from desk.getSchedulingMode()
-- before a solve starts -- see V39's own comment on desk.scheduling_mode).
--
-- This replaces the CR-02 defect where ScheduleService.loadSnapshotData INFERRED the mode of an
-- accepted schedule from whether any agent_shift_assignment rows existed for it:
-- acceptSchedule only ever writes a row for a shift assignment whose shiftBandPair is non-null,
-- and acceptSchedule requires neither feasibility nor that any shift was actually placed, so a
-- SHIFT-mode solve stopped early (or one whose live library matched no agent's contracted hours)
-- could legitimately reach COMPLETED/STOPPED with zero placed shifts. Accepting it wrote zero
-- agent_shift_assignment rows, and the inference then permanently mislabeled that accepted
-- schedule SLOT on every subsequent load -- corrupting an otherwise-immutable historical record
-- and silently switching the Agent Allocation view (ScheduleResults.tsx) to the wrong rendering
-- branch (ENVL-10/XCUT-01).
--
-- The correctness requirement this defends (P-32, 15-07-SUMMARY.md: record what THIS schedule
-- was solved under, never a live desk read, because a desk's mode can change after acceptance)
-- is unchanged -- only the MECHANISM moves from "inferred from a side effect" (a collection that
-- can legitimately be empty for the state it is supposed to signal) to "recorded as its own
-- fact" at accept time.
--
-- Backfill for pre-existing accepted rows uses the SAME inference the application code used
-- before this migration: EXISTS (an agent_shift_assignment row for this schedule) -> SHIFT, else
-- SLOT. This is exactly as accurate as today's runtime behaviour for historical rows -- no worse
-- -- but it is best-effort ONLY, not authoritative: a pre-V43 SHIFT-mode accept that placed zero
-- shifts is backfilled SLOT here too, because the true fact was never recorded and cannot be
-- recovered after the fact. Every row written by acceptSchedule AFTER this migration is
-- authoritative, not inferred.
ALTER TABLE schedule
    ADD COLUMN scheduling_mode VARCHAR(10) NOT NULL DEFAULT 'SLOT';

UPDATE schedule s
SET scheduling_mode = 'SHIFT'
WHERE EXISTS (
    SELECT 1 FROM agent_shift_assignment asa WHERE asa.schedule_id = s.id
);
