ALTER TABLE expense_types
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE expense_price_settings
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
