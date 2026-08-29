-- Add email column to users table with unique constraint (allowing NULL)
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_users_email'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);
    END IF;
END $$;

-- Allow users.password to be nullable for OAuth-only users
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- Create oauth_accounts table
CREATE TABLE IF NOT EXISTS oauth_accounts (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_oauth_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_oauth_provider_user UNIQUE (provider, provider_user_id)
);

CREATE INDEX IF NOT EXISTS idx_oauth_accounts_user_id ON oauth_accounts(user_id);
