-- Migration: Add role enum and account lockout columns to auth_users

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
        CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');
    END IF;
END $$;

ALTER TABLE auth_users
ADD COLUMN IF NOT EXISTS role user_role NOT NULL DEFAULT 'USER',
ADD COLUMN IF NOT EXISTS failed_login_attempts INT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_auth_users_role ON auth_users(role);

-- Sync existing ADMIN roles from auth_user_roles if any exist
UPDATE auth_users u
SET role = 'ADMIN'
WHERE EXISTS (
    SELECT 1 FROM auth_user_roles ur
    WHERE ur.user_id = u.id AND ur.role = 'ADMIN'
);
