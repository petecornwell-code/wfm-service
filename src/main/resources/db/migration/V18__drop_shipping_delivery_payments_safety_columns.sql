-- V17 incorrectly modelled "Shipping and Delivery" and "Payments and Safety"
-- as boolean columns on agent.  They are specializations (rows in the
-- specialization table), not per-agent flags.  Drop the wrong columns.
ALTER TABLE agent DROP COLUMN IF EXISTS shipping_and_delivery;
ALTER TABLE agent DROP COLUMN IF EXISTS payments_and_safety;
