-- 6. Drop old bank_configs columns (replaced by institution_name, iban)
ALTER TABLE bank_configs DROP COLUMN IF EXISTS bank_name;
ALTER TABLE bank_configs DROP COLUMN IF EXISTS account_number;
-- 7. Drop old unique constraint
ALTER TABLE bank_configs DROP CONSTRAINT IF EXISTS bank_configs_user_id_account_number_key;
-- 8. Add last_active_at to users (for active user sync filter)
ALTER TABLE users ADD COLUMN last_active_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
-- 9. Add useful indexes
CREATE INDEX idx_users_last_active ON users(last_active_at);
CREATE INDEX idx_bank_configs_status ON bank_configs(status);