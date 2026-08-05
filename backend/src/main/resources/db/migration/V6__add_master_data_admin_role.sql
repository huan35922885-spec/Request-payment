ALTER TABLE app_user_roles
    DROP CONSTRAINT chk_app_user_roles_role_code;

ALTER TABLE app_user_roles
    ADD CONSTRAINT chk_app_user_roles_role_code
    CHECK (
        role_code IN (
            'CASHIER',
            'PAYMENT_OPERATOR',
            'MASTER_DATA_ADMIN'
        )
    );
