-- Repeatable development data for cashier review E2E verification.
-- Only creates or restores the explicitly named E2E cashier user.

INSERT INTO app_users (
    username,
    display_name,
    email,
    department_id,
    active
)
VALUES (
    'e2e.cashier',
    'E2E 測試出納',
    'e2e.cashier@example.test',
    (SELECT id FROM departments WHERE code = 'E2E-DEPT'),
    TRUE
)
ON CONFLICT (username) DO UPDATE
SET display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    department_id = EXCLUDED.department_id,
    active = TRUE;

SELECT
    id AS "cashierId",
    username,
    display_name AS "displayName",
    active,
    department_id AS "departmentId"
FROM app_users
WHERE username = 'e2e.cashier';
