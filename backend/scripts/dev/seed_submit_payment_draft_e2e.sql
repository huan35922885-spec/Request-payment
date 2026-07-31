-- Repeatable E2E seed for the payment request's department supervisor.
-- This script only creates/updates the dedicated e2e.supervisor test data.
-- It never truncates, deletes, resets identities, or changes payment requests.

BEGIN;

DO $$
DECLARE
    target_department_id BIGINT;
    e2e_supervisor_id BIGINT;
    assignment_id BIGINT;
BEGIN
    SELECT pr.department_id
    INTO target_department_id
    FROM payment_requests pr
    WHERE pr.request_no = 'PAY-20260731-000001'
    ORDER BY pr.id DESC
    LIMIT 1;

    IF target_department_id IS NULL THEN
        RAISE EXCEPTION
            'No eligible DRAFT payment request found for PAY-20260731-000001';
    END IF;

    INSERT INTO app_users (
        username,
        display_name,
        email,
        department_id,
        active,
        created_at,
        updated_at
    )
    VALUES (
        'e2e.supervisor',
        'E2E 測試主管',
        'e2e.supervisor@example.invalid',
        target_department_id,
        TRUE,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (username) DO UPDATE
    SET display_name = EXCLUDED.display_name,
        department_id = EXCLUDED.department_id,
        active = TRUE,
        updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO e2e_supervisor_id;

    IF EXISTS (
        SELECT 1
        FROM department_supervisors ds
        JOIN app_users u ON u.id = ds.supervisor_id
        WHERE ds.department_id = target_department_id
          AND u.username <> 'e2e.supervisor'
          AND ds.active = TRUE
          AND ds.effective_from <= DATE '2026-07-31'
          AND (
              ds.effective_to IS NULL
              OR ds.effective_to >= DATE '2026-07-31'
          )
    ) THEN
        RAISE EXCEPTION
            'An existing non-E2E effective supervisor conflicts with the E2E seed';
    END IF;

    SELECT ds.id
    INTO assignment_id
    FROM department_supervisors ds
    WHERE ds.department_id = target_department_id
      AND ds.supervisor_id = e2e_supervisor_id
    ORDER BY ds.active DESC, ds.id DESC
    LIMIT 1;

    IF assignment_id IS NULL THEN
        INSERT INTO department_supervisors (
            department_id,
            supervisor_id,
            effective_from,
            effective_to,
            active,
            created_at,
            updated_at
        )
        VALUES (
            target_department_id,
            e2e_supervisor_id,
            DATE '2026-01-01',
            NULL,
            TRUE,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        );
    ELSE
        UPDATE department_supervisors
        SET effective_from = DATE '2026-01-01',
            effective_to = NULL,
            active = TRUE,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = assignment_id;
    END IF;
END;
$$;

COMMIT;
