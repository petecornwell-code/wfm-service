-- Convert HardSoftScore two-INT-column pairs to single VARCHAR columns
-- using Timefold's standard format: "<hard>hard/<soft>soft"

-- ============================================================
-- constraint_weights: replace each pair with a single VARCHAR
-- ============================================================

ALTER TABLE constraint_weights
    ADD COLUMN agent_day_off_weight          VARCHAR(50),
    ADD COLUMN spec_match_weight             VARCHAR(50),
    ADD COLUMN no_overlap_weight             VARCHAR(50),
    ADD COLUMN exactly_one_break_weight      VARCHAR(50),
    ADD COLUMN break_duration_weight         VARCHAR(50),
    ADD COLUMN break_blocked_window_weight   VARCHAR(50),
    ADD COLUMN break_alignment_weight        VARCHAR(50),
    ADD COLUMN prefer_primary_weight         VARCHAR(50),
    ADD COLUMN honour_start_time_weight      VARCHAR(50),
    ADD COLUMN honour_break_time_weight      VARCHAR(50),
    ADD COLUMN break_clustering_weight       VARCHAR(50),
    ADD COLUMN contracted_hours_weight       VARCHAR(50),
    ADD COLUMN bulk_overallocation_limit_weight    VARCHAR(50),
    ADD COLUMN bulk_underallocation_soft_weight    VARCHAR(50),
    ADD COLUMN bulk_underallocation_hard_weight    VARCHAR(50);

UPDATE constraint_weights SET
    agent_day_off_weight        = agent_day_off_weight_hard_score        || 'hard/' || agent_day_off_weight_soft_score        || 'soft',
    spec_match_weight           = spec_match_weight_hard_score           || 'hard/' || spec_match_weight_soft_score           || 'soft',
    no_overlap_weight           = no_overlap_weight_hard_score           || 'hard/' || no_overlap_weight_soft_score           || 'soft',
    exactly_one_break_weight    = exactly_one_break_weight_hard_score    || 'hard/' || exactly_one_break_weight_soft_score    || 'soft',
    break_duration_weight       = break_duration_weight_hard_score       || 'hard/' || break_duration_weight_soft_score       || 'soft',
    break_blocked_window_weight = break_blocked_window_weight_hard_score || 'hard/' || break_blocked_window_weight_soft_score || 'soft',
    break_alignment_weight      = break_alignment_weight_hard_score      || 'hard/' || break_alignment_weight_soft_score      || 'soft',
    prefer_primary_weight       = prefer_primary_weight_hard_score       || 'hard/' || prefer_primary_weight_soft_score       || 'soft',
    honour_start_time_weight    = honour_start_time_weight_hard_score    || 'hard/' || honour_start_time_weight_soft_score    || 'soft',
    honour_break_time_weight    = honour_break_time_weight_hard_score    || 'hard/' || honour_break_time_weight_soft_score    || 'soft',
    break_clustering_weight     = break_clustering_weight_hard_score     || 'hard/' || break_clustering_weight_soft_score     || 'soft',
    contracted_hours_weight     = contracted_hours_weight_hard_score     || 'hard/' || contracted_hours_weight_soft_score     || 'soft',
    bulk_overallocation_limit_weight = bulk_overallocation_limit_weight_hard_score || 'hard/' || bulk_overallocation_limit_weight_soft_score || 'soft',
    bulk_underallocation_soft_weight = bulk_underallocation_soft_weight_hard_score || 'hard/' || bulk_underallocation_soft_weight_soft_score || 'soft',
    bulk_underallocation_hard_weight = bulk_underallocation_hard_weight_hard_score || 'hard/' || bulk_underallocation_hard_weight_soft_score || 'soft';

ALTER TABLE constraint_weights
    ALTER COLUMN agent_day_off_weight        SET NOT NULL,
    ALTER COLUMN spec_match_weight           SET NOT NULL,
    ALTER COLUMN no_overlap_weight           SET NOT NULL,
    ALTER COLUMN exactly_one_break_weight    SET NOT NULL,
    ALTER COLUMN break_duration_weight       SET NOT NULL,
    ALTER COLUMN break_blocked_window_weight SET NOT NULL,
    ALTER COLUMN break_alignment_weight      SET NOT NULL,
    ALTER COLUMN prefer_primary_weight       SET NOT NULL,
    ALTER COLUMN honour_start_time_weight    SET NOT NULL,
    ALTER COLUMN honour_break_time_weight    SET NOT NULL,
    ALTER COLUMN break_clustering_weight     SET NOT NULL,
    ALTER COLUMN contracted_hours_weight     SET NOT NULL,
    ALTER COLUMN bulk_overallocation_limit_weight SET NOT NULL,
    ALTER COLUMN bulk_underallocation_soft_weight SET NOT NULL,
    ALTER COLUMN bulk_underallocation_hard_weight SET NOT NULL;

ALTER TABLE constraint_weights
    ALTER COLUMN agent_day_off_weight        SET DEFAULT '1hard/0soft',
    ALTER COLUMN spec_match_weight           SET DEFAULT '1hard/0soft',
    ALTER COLUMN no_overlap_weight           SET DEFAULT '1hard/0soft',
    ALTER COLUMN exactly_one_break_weight    SET DEFAULT '1hard/0soft',
    ALTER COLUMN break_duration_weight       SET DEFAULT '1hard/0soft',
    ALTER COLUMN break_blocked_window_weight SET DEFAULT '1hard/0soft',
    ALTER COLUMN break_alignment_weight      SET DEFAULT '1hard/0soft',
    ALTER COLUMN prefer_primary_weight       SET DEFAULT '0hard/1soft',
    ALTER COLUMN honour_start_time_weight    SET DEFAULT '0hard/1soft',
    ALTER COLUMN honour_break_time_weight    SET DEFAULT '0hard/1soft',
    ALTER COLUMN break_clustering_weight     SET DEFAULT '0hard/2soft',
    ALTER COLUMN contracted_hours_weight     SET DEFAULT '1hard/0soft',
    ALTER COLUMN bulk_overallocation_limit_weight SET DEFAULT '1hard/0soft',
    ALTER COLUMN bulk_underallocation_soft_weight SET DEFAULT '0hard/1soft',
    ALTER COLUMN bulk_underallocation_hard_weight SET DEFAULT '1hard/0soft';

ALTER TABLE constraint_weights
    DROP COLUMN agent_day_off_weight_hard_score,
    DROP COLUMN agent_day_off_weight_soft_score,
    DROP COLUMN spec_match_weight_hard_score,
    DROP COLUMN spec_match_weight_soft_score,
    DROP COLUMN no_overlap_weight_hard_score,
    DROP COLUMN no_overlap_weight_soft_score,
    DROP COLUMN exactly_one_break_weight_hard_score,
    DROP COLUMN exactly_one_break_weight_soft_score,
    DROP COLUMN break_duration_weight_hard_score,
    DROP COLUMN break_duration_weight_soft_score,
    DROP COLUMN break_blocked_window_weight_hard_score,
    DROP COLUMN break_blocked_window_weight_soft_score,
    DROP COLUMN break_alignment_weight_hard_score,
    DROP COLUMN break_alignment_weight_soft_score,
    DROP COLUMN prefer_primary_weight_hard_score,
    DROP COLUMN prefer_primary_weight_soft_score,
    DROP COLUMN honour_start_time_weight_hard_score,
    DROP COLUMN honour_start_time_weight_soft_score,
    DROP COLUMN honour_break_time_weight_hard_score,
    DROP COLUMN honour_break_time_weight_soft_score,
    DROP COLUMN break_clustering_weight_hard_score,
    DROP COLUMN break_clustering_weight_soft_score,
    DROP COLUMN contracted_hours_weight_hard_score,
    DROP COLUMN contracted_hours_weight_soft_score,
    DROP COLUMN bulk_overallocation_limit_weight_hard_score,
    DROP COLUMN bulk_overallocation_limit_weight_soft_score,
    DROP COLUMN bulk_underallocation_soft_weight_hard_score,
    DROP COLUMN bulk_underallocation_soft_weight_soft_score,
    DROP COLUMN bulk_underallocation_hard_weight_hard_score,
    DROP COLUMN bulk_underallocation_hard_weight_soft_score;

-- ============================================================
-- schedule: merge hard_score + soft_score into a single column
-- ============================================================

ALTER TABLE schedule ADD COLUMN score VARCHAR(50);

UPDATE schedule SET score = COALESCE(hard_score, 0) || 'hard/' || COALESCE(soft_score, 0) || 'soft'
    WHERE hard_score IS NOT NULL OR soft_score IS NOT NULL;

ALTER TABLE schedule
    DROP COLUMN hard_score,
    DROP COLUMN soft_score;
