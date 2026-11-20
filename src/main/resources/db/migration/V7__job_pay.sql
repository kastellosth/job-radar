-- What a job pays, when its board or its ad says, as stated (never converted between periods or currencies).
ALTER TABLE job ADD COLUMN pay_min      INTEGER;
ALTER TABLE job ADD COLUMN pay_max      INTEGER;
ALTER TABLE job ADD COLUMN pay_currency VARCHAR(3);
ALTER TABLE job ADD COLUMN pay_period   VARCHAR(5) CHECK (pay_period IN ('YEAR', 'MONTH', 'DAY', 'HOUR'));
-- The sentence the pay came from, so he can read it as written (gross or net, 14 salaries...).
ALTER TABLE job ADD COLUMN pay_text     VARCHAR(200);
