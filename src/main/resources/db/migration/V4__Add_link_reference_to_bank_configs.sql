-- Add link_reference column to bank_configs for secure UUID-based callback identification
ALTER TABLE bank_configs ADD COLUMN link_reference VARCHAR(255) UNIQUE;
