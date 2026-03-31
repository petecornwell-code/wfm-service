-- Increase honour preferred start/break time weights from soft(1) to soft(5)
UPDATE constraint_weights SET honour_start_time_weight = '0hard/5soft' WHERE honour_start_time_weight = '0hard/1soft';
UPDATE constraint_weights SET honour_break_time_weight = '0hard/5soft' WHERE honour_break_time_weight = '0hard/1soft';
