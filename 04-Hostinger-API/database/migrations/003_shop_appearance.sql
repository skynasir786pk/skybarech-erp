-- Additive and safe to run again. Does not reset shops, users or business data.
CREATE TABLE IF NOT EXISTS shop_appearance (
    shop_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    settings JSON NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_appearance_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Account-wide PIN throttling uses a separate index. Keep upgrades repeatable
-- on both MariaDB and MySQL without depending on ADD INDEX IF NOT EXISTS.
SET @skybarech_pin_index_sql = (
    SELECT IF(COUNT(*) > 0, 'SELECT 1',
        'CREATE INDEX idx_login_account ON login_attempts (identity_hash, succeeded, attempted_at)')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'login_attempts'
      AND index_name = 'idx_login_account'
);
PREPARE skybarech_pin_index_stmt FROM @skybarech_pin_index_sql;
EXECUTE skybarech_pin_index_stmt;
DEALLOCATE PREPARE skybarech_pin_index_stmt;
