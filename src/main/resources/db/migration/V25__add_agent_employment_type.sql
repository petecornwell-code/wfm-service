ALTER TABLE agent ADD COLUMN employment_type VARCHAR(20) NOT NULL DEFAULT 'FULL_TIME';

-- Per V22 convention: drop the default after backfill so new rows must set explicitly
ALTER TABLE agent ALTER COLUMN employment_type DROP DEFAULT;
