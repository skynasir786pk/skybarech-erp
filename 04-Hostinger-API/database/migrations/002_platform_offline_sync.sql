-- Run only when upgrading an existing Phase 2 database.
-- Fresh installations should import database/schema.sql instead.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

ALTER TABLE shops
    ADD COLUMN owner_name VARCHAR(150) NOT NULL DEFAULT '' AFTER plan,
    ADD COLUMN owner_mobile VARCHAR(30) NOT NULL DEFAULT '' AFTER owner_name,
    ADD COLUMN city VARCHAR(100) NOT NULL DEFAULT '' AFTER owner_mobile,
    ADD COLUMN address VARCHAR(255) NOT NULL DEFAULT '' AFTER city,
    ADD COLUMN monthly_fee DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER address,
    ADD COLUMN notes TEXT NULL AFTER monthly_fee;

ALTER TABLE sync_operations
    ADD COLUMN device_id VARCHAR(100) NOT NULL DEFAULT '' AFTER action_name;

CREATE TABLE platform_admins (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status ENUM('active','blocked') NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE platform_auth_sessions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT UNSIGNED NOT NULL,
    access_token_hash CHAR(64) NOT NULL UNIQUE,
    access_expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    ip_address VARCHAR(45) NOT NULL,
    user_agent VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_platform_access (access_token_hash, access_expires_at, revoked_at),
    CONSTRAINT fk_platform_session_admin FOREIGN KEY (admin_id) REFERENCES platform_admins(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE registered_devices (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT UNSIGNED NOT NULL,
    branch_id BIGINT UNSIGNED NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    platform ENUM('android','desktop') NOT NULL,
    status ENUM('active','blocked') NOT NULL DEFAULT 'active',
    last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_registered_device (shop_id, device_id),
    CONSTRAINT fk_devices_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    CONSTRAINT fk_devices_branch FOREIGN KEY (branch_id) REFERENCES branches(id) ON DELETE SET NULL,
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sync_conflicts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT UNSIGNED NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    operation_id VARCHAR(100) NOT NULL,
    device_id VARCHAR(100) NOT NULL DEFAULT '',
    expected_version BIGINT UNSIGNED NOT NULL,
    server_version BIGINT UNSIGNED NOT NULL,
    client_payload JSON NOT NULL,
    resolved_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_conflicts_open (shop_id, resolved_at, created_at),
    CONSTRAINT fk_conflicts_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
