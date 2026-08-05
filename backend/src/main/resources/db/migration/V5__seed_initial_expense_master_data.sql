/*
   Fixed initial expense master data.

   This migration intentionally seeds only confirmed business codes and prices.
   It does not overwrite existing master data. Any conflict with an active
   overlapping price interval fails the migration transaction.
*/
DO $$
DECLARE
    v_type_code TEXT;
    v_type_name TEXT;
    v_type_calculation_type TEXT;
    v_expense_type_id BIGINT;
    v_price_expense_type_code TEXT;
    v_price_code TEXT;
    v_price_name TEXT;
    v_unit_price NUMERIC(14, 2);
    v_effective_from DATE := DATE '2026-08-04';
    v_exact_current_count INTEGER;
    v_effective_count INTEGER;
    v_overlap_count INTEGER;
BEGIN
    FOR v_type_code, v_type_name, v_type_calculation_type IN
        SELECT *
        FROM (
            VALUES
                ('MEAL', '餐費', 'MEAL'),
                ('CONFIRMATION', '函證', 'CONFIRMATION')
        ) AS seed(code, name, calculation_type)
    LOOP
        IF EXISTS (
            SELECT 1
            FROM expense_types
            WHERE code = v_type_code
              AND (
                    name IS DISTINCT FROM v_type_name
                    OR calculation_type IS DISTINCT FROM v_type_calculation_type
                    OR active IS DISTINCT FROM TRUE
              )
        ) THEN
            RAISE EXCEPTION
                'Expense type code % exists with conflicting master data',
                v_type_code;
        END IF;

        INSERT INTO expense_types (code, name, calculation_type, active)
        SELECT v_type_code, v_type_name, v_type_calculation_type, TRUE
        WHERE NOT EXISTS (
            SELECT 1
            FROM expense_types
            WHERE code = v_type_code
        );
    END LOOP;

    FOR v_price_expense_type_code, v_price_code, v_price_name, v_unit_price IN
        SELECT *
        FROM (
            VALUES
                ('MEAL', 'DEFAULT', '一般餐費', 80.00::NUMERIC(14, 2)),
                ('CONFIRMATION', 'NORMAL_MAIL', '平信', 8.00::NUMERIC(14, 2)),
                ('CONFIRMATION', 'REGISTERED_MAIL', '掛號', 28.00::NUMERIC(14, 2)),
                ('CONFIRMATION', 'EXPRESS_REGISTERED_MAIL', '限時掛號', 35.00::NUMERIC(14, 2))
        ) AS seed(expense_type_code, price_code, price_name, unit_price)
    LOOP
        SELECT id
        INTO STRICT v_expense_type_id
        FROM expense_types
        WHERE code = v_price_expense_type_code;

        SELECT COUNT(*)
        INTO v_exact_current_count
        FROM expense_price_settings
        WHERE expense_price_settings.expense_type_id = v_expense_type_id
          AND expense_price_settings.price_code = v_price_code
          AND expense_price_settings.price_name = v_price_name
          AND expense_price_settings.unit_price = v_unit_price
          AND active = TRUE
          AND expense_price_settings.effective_from <= v_effective_from
          AND effective_to IS NULL;

        IF v_exact_current_count > 1 THEN
            RAISE EXCEPTION
                'Multiple matching open-ended prices exist for expense type % and price code %',
                v_price_expense_type_code,
                v_price_code;
        END IF;

        SELECT COUNT(*)
        INTO v_effective_count
        FROM expense_price_settings
        WHERE expense_price_settings.expense_type_id = v_expense_type_id
          AND expense_price_settings.price_code = v_price_code
          AND active = TRUE
          AND expense_price_settings.effective_from <= v_effective_from
          AND (
                expense_price_settings.effective_to IS NULL
                OR expense_price_settings.effective_to >= v_effective_from
          );

        IF v_effective_count > 1 THEN
            RAISE EXCEPTION
                'Multiple effective prices exist for expense type % and price code %',
                v_price_expense_type_code,
                v_price_code;
        END IF;

        SELECT COUNT(*)
        INTO v_overlap_count
        FROM expense_price_settings
        WHERE expense_price_settings.expense_type_id = v_expense_type_id
          AND expense_price_settings.price_code = v_price_code
          AND active = TRUE
          AND (
                expense_price_settings.effective_to IS NULL
                OR expense_price_settings.effective_to >= v_effective_from
          );

        IF v_exact_current_count = 1 AND v_overlap_count = 1 THEN
            CONTINUE;
        END IF;

        IF v_overlap_count > 0 THEN
            RAISE EXCEPTION
                'Conflicting active price interval exists for expense type % and price code %',
                v_price_expense_type_code,
                v_price_code;
        END IF;

        INSERT INTO expense_price_settings (
            expense_type_id,
            price_code,
            price_name,
            unit_price,
            effective_from,
            effective_to,
            active
        )
        VALUES (
            v_expense_type_id,
            v_price_code,
            v_price_name,
            v_unit_price,
            v_effective_from,
            NULL,
            TRUE
        );
    END LOOP;
END
$$;
