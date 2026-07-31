-- Repeatable development data for POST /api/payment-requests/drafts E2E verification.
-- This script only upserts rows with the E2E-* fixed codes and never resets data or sequences.

INSERT INTO departments (code, name, active)
VALUES ('E2E-DEPT', 'E2E 驗收部門', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    active = TRUE;

INSERT INTO app_users (
    username,
    display_name,
    email,
    department_id,
    active
)
VALUES (
    'e2e.applicant',
    'E2E 驗收申請人',
    'e2e.applicant@example.test',
    (SELECT id FROM departments WHERE code = 'E2E-DEPT'),
    TRUE
)
ON CONFLICT (username) DO UPDATE
SET display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    department_id = EXCLUDED.department_id,
    active = TRUE;

INSERT INTO companies (code, name, active)
VALUES ('E2E-COMPANY', 'E2E 驗收公司', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    active = TRUE;

INSERT INTO customers (
    code,
    name,
    default_request_category,
    active
)
VALUES (
    'E2E-CUSTOMER',
    'E2E 驗收客戶',
    'EXPENSE',
    TRUE
)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    default_request_category = 'EXPENSE',
    active = TRUE;

INSERT INTO expense_types (code, name, calculation_type, active)
VALUES
    ('E2E-MANUAL', 'E2E 人工輸入', 'MANUAL', TRUE),
    ('E2E-MEAL', 'E2E 餐費', 'MEAL', TRUE),
    ('E2E-CONFIRM', 'E2E 函證', 'CONFIRMATION', TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    calculation_type = EXCLUDED.calculation_type,
    active = TRUE;

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
    (SELECT id FROM expense_types WHERE code = 'E2E-MEAL'),
    'DEFAULT',
    'E2E 餐費預設單價',
    80.00,
    DATE '2026-01-01',
    NULL,
    TRUE
), (
    (SELECT id FROM expense_types WHERE code = 'E2E-CONFIRM'),
    'REGISTERED_MAIL',
    'E2E 掛號函證單價',
    28.00,
    DATE '2026-01-01',
    NULL,
    TRUE
)
ON CONFLICT (expense_type_id, price_code)
WHERE active = TRUE AND effective_to IS NULL
DO UPDATE
SET price_name = EXCLUDED.price_name,
    unit_price = EXCLUDED.unit_price,
    effective_from = EXCLUDED.effective_from,
    effective_to = EXCLUDED.effective_to,
    active = TRUE;

SELECT
    (SELECT id FROM app_users WHERE username = 'e2e.applicant') AS "applicantId",
    (SELECT id FROM companies WHERE code = 'E2E-COMPANY') AS "companyId",
    (SELECT id FROM customers WHERE code = 'E2E-CUSTOMER') AS "customerId",
    (SELECT id FROM expense_types WHERE code = 'E2E-MANUAL') AS "manualExpenseTypeId",
    (SELECT id FROM expense_types WHERE code = 'E2E-MEAL') AS "mealExpenseTypeId",
    (SELECT id FROM expense_types WHERE code = 'E2E-CONFIRM') AS "confirmationExpenseTypeId";
