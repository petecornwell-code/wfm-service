-- Seed the job-title allowlist so schedulability is restricted the moment this deploys.
--
-- Context: the allowlist became the SINGLE control for schedulability (solver, desk-assignment
-- upload, and template seeding) in place of the job_title_config.non_schedulable denylist. The
-- allowlist is fail-open — with zero patterns every title passes — so deploying that switch
-- against an empty table would have left all 836 synced titles schedulable, the opposite of the
-- intent.
--
-- Seeding here makes the intended end state ("only Customer Support Representative is
-- schedulable") true immediately, without a separate manual step.
--
-- Matching is a case-insensitive SUBSTRING, so this one row also covers variants such as
-- "Senior Customer Support Representative" and "Customer Support Representative II".
--
-- Seeded per existing tenant rather than hardcoding tenant 1, so this stays correct if more
-- tenants exist. ON CONFLICT keeps it idempotent and preserves any pattern already added by hand.
INSERT INTO job_title_include_pattern (id, tenant_id, pattern, created_at, updated_at)
SELECT gen_random_uuid(), t.tenant_id, 'Customer Support Representative',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (SELECT DISTINCT tenant_id FROM job_title_config) AS t
ON CONFLICT (tenant_id, pattern) DO NOTHING;
