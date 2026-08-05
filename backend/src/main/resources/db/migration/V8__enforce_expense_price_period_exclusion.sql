CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;

ALTER TABLE expense_price_settings
    ADD CONSTRAINT excl_expense_price_settings_active_period
    EXCLUDE USING gist (
        expense_type_id WITH =,
        price_code WITH =,
        daterange(effective_from, effective_to, '[]') WITH &&
    )
    WHERE (active = TRUE);

DROP INDEX uq_expense_price_settings_current;
