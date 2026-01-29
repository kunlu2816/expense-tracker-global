-- 1. Drop webhook_logs (no longer needed)
DROP TABLE IF EXISTS webhook_logs;

-- 2. Modify bank_configs: Remove Casso columns
ALTER TABLE bank_configs DROP COLUMN IF EXISTS casso_api_key;
ALTER TABLE bank_configs DROP COLUMN IF EXISTS secure_token;

-- 3. Modify bank_configs: Add GoCardless columns
ALTER TABLE bank_configs ADD COLUMN institution_id VARCHAR(100);
ALTER TABLE bank_configs ADD COLUMN institution_name VARCHAR(255);
ALTER TABLE bank_configs ADD COLUMN institution_logo VARCHAR(500);
ALTER TABLE bank_configs ADD COLUMN requisition_id VARCHAR(255);
ALTER TABLE bank_configs ADD COLUMN gocardless_account_id VARCHAR(255) UNIQUE;
ALTER TABLE bank_configs ADD COLUMN iban VARCHAR(50);
ALTER TABLE bank_configs ADD COLUMN account_name VARCHAR(255);
ALTER TABLE bank_configs ADD COLUMN status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE bank_configs ADD COLUMN access_expires_at TIMESTAMP;
ALTER TABLE bank_configs ADD COLUMN last_synced_at TIMESTAMP;

-- 4. Modify transactions: Add currency
ALTER TABLE transactions ADD COLUMN currency VARCHAR(3) DEFAULT 'GBP';

-- 5. Create sync_logs table
CREATE TABLE sync_logs (
                           id                   BIGSERIAL PRIMARY KEY,
                           bank_config_id       BIGINT NOT NULL REFERENCES bank_configs(id) ON DELETE CASCADE,
                           synced_at            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                           date_from            DATE,
                           date_to              DATE,
                           transactions_fetched INT DEFAULT 0,
                           transactions_new     INT DEFAULT 0,
                           status               VARCHAR(20) NOT NULL,
                           error_message        TEXT,
                           created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sync_logs_bank_config ON sync_logs(bank_config_id);
CREATE INDEX idx_sync_logs_synced_at ON sync_logs(synced_at);