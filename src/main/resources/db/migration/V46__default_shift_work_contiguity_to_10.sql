-- G-15-30: lower the "Shift work contiguity" default from 100 to 10, and pull existing rows off
-- the old default.
--
-- V45 shipped this weight at 100hard, matching exactly_one_break_weight on the reasoning that
-- fragmenting a working day is categorically worse than one mis-placed seat. The reasoning was
-- sound; the SCALE was not, because shift_envelope_compliance_weight is 1hard. A 100:1 ratio means
-- the solver rationally breaches the envelope up to 99 times to avoid a single split shift, and on
-- the live desk it did exactly that -- 52 envelope violations, 43 of them on one date, with
-- reported spans like 08:00-20:00 that no template in the library provides.
--
-- Measured on Stubhub (EN), all runs holding every operator requirement (contiguous days,
-- mid-shift breaks, weekend edge coverage), varying only this ratio:
--     contiguity 100 : envelope   1  ->  52 envelope violations
--     contiguity 100 : envelope 100  ->   1, but the model became too rigid to place agents
--     contiguity  10 : envelope   1  ->   6, zero split shifts
--     contiguity  10 : envelope  10  ->   3, zero split shifts   <-- best measured
--
-- 10 keeps contiguity strictly above envelope compliance -- a split shift still outranks a single
-- out-of-envelope seat, which is the intent -- without letting it dominate the whole model.
ALTER TABLE constraint_weights
    ALTER COLUMN shift_work_contiguity_weight SET DEFAULT '10hard/0soft';

-- Existing rows still carrying V45's default carry the arbitrage with them. Rows an operator has
-- deliberately set to anything else are left alone.
UPDATE constraint_weights
   SET shift_work_contiguity_weight = '10hard/0soft'
 WHERE shift_work_contiguity_weight = '100hard/0soft';
