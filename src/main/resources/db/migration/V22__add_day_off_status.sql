ALTER TABLE agent_day_off ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';

-- Remove the default after backfill so new rows must set it explicitly
ALTER TABLE agent_day_off ALTER COLUMN status DROP DEFAULT;
