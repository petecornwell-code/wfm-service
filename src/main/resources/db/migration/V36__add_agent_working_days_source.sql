-- D-15: distinguishes a sheet-sourced working-days pattern from a BambooHR-sourced one,
-- so a later BambooRefreshService.persistRefreshData pass cannot silently flip
-- working_days_known back to false for an agent whose pattern came from the spreadsheet
-- (the UAT 2026-08-12 downgrade hazard). Only the desk-assignment upload ever writes
-- this column to SPREADSHEET; BambooRefreshService never assigns it, so a BambooHR
-- refresh can never reclaim ownership of a week an operator corrected.
-- DEFAULT 'BAMBOOHR' is what makes this migration safe for every existing row: nobody's
-- eligibility changes at deploy time.
ALTER TABLE agent ADD COLUMN working_days_source VARCHAR(20) NOT NULL DEFAULT 'BAMBOOHR';
