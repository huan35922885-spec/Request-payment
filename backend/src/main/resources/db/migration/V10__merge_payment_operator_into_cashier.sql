-- Merge PAYMENT_OPERATOR into CASHIER for Excel-aligned cashier role model.

INSERT INTO app_user_roles (user_id, role_code)
SELECT user_id, 'CASHIER'
FROM app_user_roles
WHERE role_code = 'PAYMENT_OPERATOR'
ON CONFLICT (user_id, role_code) DO NOTHING;

DELETE FROM app_user_roles
WHERE role_code = 'PAYMENT_OPERATOR';

ALTER TABLE app_user_roles
    DROP CONSTRAINT chk_app_user_roles_role_code;

ALTER TABLE app_user_roles
    ADD CONSTRAINT chk_app_user_roles_role_code
    CHECK (
        role_code IN (
            'CASHIER',
            'MASTER_DATA_ADMIN'
        )
    );
