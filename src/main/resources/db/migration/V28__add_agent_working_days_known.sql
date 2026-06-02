ALTER TABLE agent ADD COLUMN working_days_known BOOLEAN NOT NULL DEFAULT TRUE;

-- DEFAULT TRUE is kept permanently (unlike V25 which dropped the default).
-- Agents created before their first BambooHR refresh must not be incorrectly excluded
-- from the solver: the flag stays TRUE until BambooRefreshService explicitly sets it
-- to false for agents with blank/Variable customWorkingdays (D-07).
