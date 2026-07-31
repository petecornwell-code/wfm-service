-- D-12: recurring day-off descriptive flavour (MANDATORY / PTO) for the enriched
-- upload parser (Phase 10). Stored on agent_day_hours -- NOT as dated AgentDayOff
-- rows -- because BambooRefreshService.refreshDeskAgents deletes and regenerates
-- every AgentDayOff row in its rolling lookback/lookahead window on every sync
-- (deleteByAgent_IdAndDateBetween, then reinsert from BambooHR data only). A
-- spreadsheet-sourced dated row would be silently wiped with no regeneration path.
-- agent_day_hours has no such window: it is queried by desk with no date-range
-- restriction (SolverService.resolveEffectiveHours), so a 0.00 row here already
-- durably blocks the solver for that weekday indefinitely, with zero extra code.
-- This column is reporting/label metadata only -- never read by the solver.
ALTER TABLE agent_day_hours
    ADD COLUMN day_off_type VARCHAR(9);
-- NULL for all existing rows (no backfill -- no spreadsheet has ever populated
-- this column before this migration; mirrors V29's no-backfill-for-pre-existing style).
-- NULL      = a normal worked day (hours > 0) or a plain unlabelled 0 (no descriptive reason).
-- 'MANDATORY' / 'PTO' = the spreadsheet cell used that keyword (D-03); reuses the
-- existing DayOffType enum vocabulary without reusing AgentDayOff's dated materialization.
