-- Phase 18: Business Day Foundation & Guards (BDAY-01, BDAY-02).
--
-- A desk declares the time its day begins. Every existing desk defaults to 00:00 -- today's
-- behaviour, unchanged -- and this phase gates the accepted value to 00:00 only; nothing
-- honours a non-default day-start until BDAY-04 widens the accepted range and re-anchors
-- DayWindow. The column and the API land now so that later work has a place to write into
-- rather than a schema change of its own.
ALTER TABLE desk ADD COLUMN day_start TIME NOT NULL DEFAULT '00:00';

-- timeslot.business_date is distinct from its calendar date, populated at generation
-- (TimeslotGeneratorService is the sole writer, structurally guarded per BDAY-08). The nullable
-- window here is deliberately zero-length -- add nullable, backfill every existing row, then
-- SET NOT NULL, all three statements in this one migration, so no reader ever observes an unset
-- row. Backfilling equal to the calendar date is correct today because every desk's day-start is
-- 00:00; BDAY-03 introduces desks where the two values diverge.
ALTER TABLE timeslot ADD COLUMN business_date DATE;
UPDATE timeslot SET business_date = date;
ALTER TABLE timeslot ALTER COLUMN business_date SET NOT NULL;
