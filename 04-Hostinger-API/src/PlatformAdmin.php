<?php
declare(strict_types=1);

final class PlatformAdmin
{
    public static function shops(PDO $db): array
    {
        $rows = $db->query(
            "SELECT id, name AS shopName, code AS shopCode, owner_name AS ownerName,
                    owner_mobile AS ownerMobile, city, address, plan, monthly_fee AS monthlyFee,
                    status, DATE(expires_at) AS expiryDate, notes, created_at AS createdAt,
                    updated_at AS updatedAt
             FROM shops ORDER BY id DESC"
        )->fetchAll();
        foreach ($rows as &$row) {
            $row['id'] = (string) $row['id'];
            $row['monthlyFee'] = (float) $row['monthlyFee'];
        }
        return $rows;
    }

    public static function createShop(PDO $db, array $body): array
    {
        $name = trim((string) ($body['shopName'] ?? ''));
        $owner = trim((string) ($body['ownerName'] ?? ''));
        $mobile = self::canonicalMobile((string) ($body['ownerMobile'] ?? ''));
        if ($name === '' || $owner === '' || !preg_match('/^[0-9+ -]{7,30}$/', $mobile)) {
            Http::error(422, 'invalid_shop', 'Shop name, owner name and a valid owner mobile are required.');
        }
        $code = strtoupper(trim((string) ($body['shopCode'] ?? '')));
        if ($code === '') {
            $code = 'SB-' . strtoupper(bin2hex(random_bytes(4)));
        }
        $activationCode = strtoupper(trim((string) ($body['activationCode'] ?? '')));
        if (strlen($activationCode) < 8) {
            $activationCode = 'SB-' . strtoupper(bin2hex(random_bytes(6)));
        }
        $temporaryPassword = (string) ($body['temporaryPassword'] ?? '');
        if (!RateLimit::validShopCredential($temporaryPassword)) {
            $temporaryPassword = str_pad((string) random_int(0, 9999), 4, '0', STR_PAD_LEFT);
        }
        $status = self::status((string) ($body['status'] ?? 'trial'));
        $expires = self::dateOrNull($body['expiryDate'] ?? null);

        $db->beginTransaction();
        try {
            $stmt = $db->prepare(
                'INSERT INTO shops (name, code, status, plan, owner_name, owner_mobile, city, address, monthly_fee, notes, expires_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
            );
            $stmt->execute([
                $name, $code, $status, trim((string) ($body['plan'] ?? 'Trial')), $owner, $mobile,
                trim((string) ($body['city'] ?? '')), trim((string) ($body['address'] ?? '')),
                max(0, (float) ($body['monthlyFee'] ?? 0)), trim((string) ($body['notes'] ?? '')) ?: null, $expires,
            ]);
            $shopId = (int) $db->lastInsertId();
            $branch = $db->prepare("INSERT INTO branches (shop_id, name, code, address, contact_1) VALUES (?, 'Main Branch', 'MAIN', ?, ?)");
            $branch->execute([$shopId, trim((string) ($body['address'] ?? '')), $mobile]);
            $branchId = (int) $db->lastInsertId();
            $activation = $db->prepare(
                'INSERT INTO activations (shop_id, branch_id, owner_mobile, activation_code_hash, temporary_password_hash, expires_at)
                 VALUES (?, ?, ?, ?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 7 DAY))'
            );
            $activation->execute([$shopId, $branchId, $mobile, hash('sha256', $activationCode), password_hash($temporaryPassword, PASSWORD_DEFAULT)]);
            self::ensureDefaultPermissions($db, $shopId);
            $db->commit();
            return [
                'shop_id' => (string) $shopId, 'activation_code' => $activationCode,
                'temporary_password' => $temporaryPassword,
            ];
        } catch (Throwable $error) {
            if ($db->inTransaction()) $db->rollBack();
            throw $error;
        }
    }

    public static function updateShop(PDO $db, array $body): void
    {
        $id = (int) ($body['id'] ?? 0);
        $name = trim((string) ($body['shopName'] ?? ''));
        $owner = trim((string) ($body['ownerName'] ?? ''));
        $mobile = self::canonicalMobile((string) ($body['ownerMobile'] ?? ''));
        if ($id < 1 || $name === '' || $owner === '' || !preg_match('/^[0-9+ -]{7,30}$/', $mobile)) {
            Http::error(422, 'invalid_shop', 'Valid shop fields are required.');
        }

        $db->beginTransaction();
        try {
            $current = $db->prepare('SELECT owner_mobile FROM shops WHERE id = ? FOR UPDATE');
            $current->execute([$id]);
            $oldMobile = $current->fetchColumn();
            if ($oldMobile === false) Http::error(404, 'shop_not_found', 'Shop was not found.');

            $stmt = $db->prepare(
                'UPDATE shops SET name=?, owner_name=?, owner_mobile=?, city=?, address=?, plan=?, monthly_fee=?, status=?, expires_at=?, notes=? WHERE id=?'
            );
            $stmt->execute([
                $name, $owner, $mobile, trim((string) ($body['city'] ?? '')), trim((string) ($body['address'] ?? '')),
                trim((string) ($body['plan'] ?? 'Trial')), max(0, (float) ($body['monthlyFee'] ?? 0)),
                self::status((string) ($body['status'] ?? 'trial')), self::dateOrNull($body['expiryDate'] ?? null),
                trim((string) ($body['notes'] ?? '')) ?: null, $id,
            ]);

            if ((string) $oldMobile !== $mobile) {
                $ownerUser = $db->prepare("UPDATE users SET mobile = ?, full_name = ? WHERE shop_id = ? AND role = 'super_admin' AND mobile = ?");
                $ownerUser->execute([$mobile, $owner, $id, (string) $oldMobile]);
                $activation = $db->prepare('UPDATE activations SET owner_mobile = ? WHERE shop_id = ? AND used_at IS NULL');
                $activation->execute([$mobile, $id]);
            } else {
                $db->prepare("UPDATE users SET full_name = ? WHERE shop_id = ? AND role = 'super_admin'")->execute([$owner, $id]);
            }
            $db->commit();
        } catch (Throwable $error) {
            if ($db->inTransaction()) $db->rollBack();
            throw $error;
        }
    }

    public static function setStatus(PDO $db, array $body): void
    {
        $id = (int) ($body['id'] ?? 0);
        if ($id < 1) Http::error(422, 'invalid_shop', 'Shop ID is required.');
        $status = self::status((string) ($body['status'] ?? ''));
        $stmt = $db->prepare('UPDATE shops SET status = ? WHERE id = ?');
        $stmt->execute([$status, $id]);
        if ($stmt->rowCount() < 1 && !self::exists($db, $id)) Http::error(404, 'shop_not_found', 'Shop was not found.');
        if (!in_array($status, ['active', 'trial'], true)) {
            $db->prepare('UPDATE auth_sessions SET revoked_at = UTC_TIMESTAMP() WHERE shop_id = ? AND revoked_at IS NULL')->execute([$id]);
        }
    }

    public static function deleteShop(PDO $db, array $body): void
    {
        $id = (int) ($body['id'] ?? 0);
        if ($id < 1) Http::error(422, 'invalid_shop', 'Shop ID is required.');
        $stmt = $db->prepare('DELETE FROM shops WHERE id = ?');
        $stmt->execute([$id]);
        if ($stmt->rowCount() !== 1) Http::error(404, 'shop_not_found', 'Shop was not found.');
    }

    public static function shopControl(PDO $db, int $shopId): array
    {
        if ($shopId < 1 || !self::exists($db, $shopId)) Http::error(404, 'shop_not_found', 'Shop was not found.');

        $shop = $db->prepare(
            'SELECT id, name AS shopName, code AS shopCode, owner_name AS ownerName, owner_mobile AS ownerMobile,
                    status, plan, DATE(expires_at) AS expiryDate, city, address, monthly_fee AS monthlyFee
             FROM shops WHERE id = ?'
        );
        $shop->execute([$shopId]);

        $branches = $db->prepare('SELECT id, name, code, address, contact_1 AS contact1, contact_2 AS contact2, contact_3 AS contact3, status, created_at AS createdAt FROM branches WHERE shop_id = ? ORDER BY id');
        $branches->execute([$shopId]);

        $users = $db->prepare('SELECT id, branch_id AS branchId, full_name AS fullName, email, mobile, role, status, created_at AS createdAt, updated_at AS updatedAt FROM users WHERE shop_id = ? ORDER BY id');
        $users->execute([$shopId]);

        $devices = $db->prepare('SELECT id, branch_id AS branchId, user_id AS userId, device_id AS deviceId, platform, status, last_seen_at AS lastSeenAt, created_at AS createdAt FROM registered_devices WHERE shop_id = ? ORDER BY last_seen_at DESC');
        $devices->execute([$shopId]);

        $sessionStmt = $db->prepare('SELECT COUNT(*) FROM auth_sessions WHERE shop_id = ? AND revoked_at IS NULL AND refresh_expires_at > UTC_TIMESTAMP()');
        $sessionStmt->execute([$shopId]);

        $syncStmt = $db->prepare(
            "SELECT COUNT(*) AS operations,
                    SUM(result_status = 'applied') AS applied,
                    SUM(result_status = 'conflict') AS conflicts,
                    MAX(created_at) AS lastOperationAt
             FROM sync_operations WHERE shop_id = ?"
        );
        $syncStmt->execute([$shopId]);
        $sync = $syncStmt->fetch() ?: [];
        $openConflicts = $db->prepare('SELECT COUNT(*) FROM sync_conflicts WHERE shop_id = ? AND resolved_at IS NULL');
        $openConflicts->execute([$shopId]);
        $lastChange = $db->prepare('SELECT MAX(changed_at) FROM sync_changes WHERE shop_id = ?');
        $lastChange->execute([$shopId]);

        return [
            'shop' => $shop->fetch(),
            'branches' => $branches->fetchAll(),
            'users' => $users->fetchAll(),
            'devices' => $devices->fetchAll(),
            'activeSessions' => (int) $sessionStmt->fetchColumn(),
            'sync' => [
                'operations' => (int) ($sync['operations'] ?? 0),
                'applied' => (int) ($sync['applied'] ?? 0),
                'conflicts' => (int) ($sync['conflicts'] ?? 0),
                'openConflicts' => (int) $openConflicts->fetchColumn(),
                'lastOperationAt' => $sync['lastOperationAt'] ?? null,
                'lastChangeAt' => $lastChange->fetchColumn() ?: null,
            ],
        ];
    }

    public static function saveBranch(PDO $db, array $body): array
    {
        $shopId = (int) ($body['shopId'] ?? $body['shop_id'] ?? 0);
        $id = (int) ($body['id'] ?? 0);
        $name = trim((string) ($body['name'] ?? ''));
        $code = strtoupper(trim((string) ($body['code'] ?? '')));
        $status = strtolower(trim((string) ($body['status'] ?? 'active')));
        if ($shopId < 1 || !self::exists($db, $shopId) || $name === '' || !preg_match('/^[A-Z0-9_-]{2,50}$/', $code)) {
            Http::error(422, 'invalid_branch', 'Valid shop, branch name and branch code are required.');
        }
        if (!in_array($status, ['active', 'inactive'], true)) Http::error(422, 'invalid_branch_status', 'Branch status is invalid.');
        $values = [
            trim((string) ($body['address'] ?? '')),
            trim((string) ($body['contact1'] ?? '')),
            trim((string) ($body['contact2'] ?? '')),
            trim((string) ($body['contact3'] ?? '')),
        ];
        if ($id > 0) {
            $stmt = $db->prepare('UPDATE branches SET name=?, code=?, address=?, contact_1=?, contact_2=?, contact_3=?, status=? WHERE id=? AND shop_id=?');
            $stmt->execute([$name, $code, ...$values, $status, $id, $shopId]);
            if ($stmt->rowCount() < 1) {
                $check = $db->prepare('SELECT 1 FROM branches WHERE id=? AND shop_id=?'); $check->execute([$id, $shopId]);
                if (!$check->fetchColumn()) Http::error(404, 'branch_not_found', 'Branch was not found.');
            }
        } else {
            $stmt = $db->prepare('INSERT INTO branches (shop_id, name, code, address, contact_1, contact_2, contact_3, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)');
            $stmt->execute([$shopId, $name, $code, ...$values, $status]);
            $id = (int) $db->lastInsertId();
        }
        return ['id' => (string) $id, 'shopId' => (string) $shopId, 'name' => $name, 'code' => $code, 'status' => $status];
    }

    public static function saveUser(PDO $db, array $body): array
    {
        $shopId = (int) ($body['shopId'] ?? $body['shop_id'] ?? 0);
        $id = (int) ($body['id'] ?? 0);
        $fullName = trim((string) ($body['fullName'] ?? $body['name'] ?? ''));
        $mobile = self::canonicalMobile((string) ($body['mobile'] ?? ''));
        $email = strtolower(trim((string) ($body['email'] ?? '')));
        $password = (string) ($body['password'] ?? '');
        $role = strtolower(trim((string) ($body['role'] ?? 'cashier')));
        $status = strtolower(trim((string) ($body['status'] ?? 'active')));
        $branchId = isset($body['branchId']) && $body['branchId'] !== '' ? (int) $body['branchId'] : null;
        $roles = ['super_admin', 'branch_manager', 'cashier', 'technician', 'accountant'];
        if ($shopId < 1 || !self::exists($db, $shopId) || $fullName === '' || !preg_match('/^[0-9+ -]{7,30}$/', $mobile) || !in_array($role, $roles, true)) {
            Http::error(422, 'invalid_user', 'Valid shop, name, mobile and role are required.');
        }
        if ($email !== '' && !filter_var($email, FILTER_VALIDATE_EMAIL)) Http::error(422, 'invalid_email', 'Email address is invalid.');
        if (!in_array($status, ['active', 'blocked', 'deleted'], true)) Http::error(422, 'invalid_user_status', 'User status is invalid.');
        if ($role === 'super_admin') $branchId = null;
        if ($role !== 'super_admin' && $branchId === null) Http::error(422, 'branch_required', 'A branch is required for staff users.');
        if ($branchId !== null) {
            $branch = $db->prepare('SELECT 1 FROM branches WHERE id = ? AND shop_id = ?');
            $branch->execute([$branchId, $shopId]);
            if (!$branch->fetchColumn()) Http::error(422, 'invalid_branch', 'Selected branch does not belong to this shop.');
        }

        if ($id > 0) {
            $check = $db->prepare('SELECT role FROM users WHERE id = ? AND shop_id = ?');
            $check->execute([$id, $shopId]);
            if ($check->fetchColumn() === false) Http::error(404, 'user_not_found', 'User was not found.');
            if ($password !== '' && !RateLimit::validShopCredential($password)) Http::error(422, 'weak_password', 'New PIN must contain exactly 4 digits.');
            if ($password !== '') {
                $stmt = $db->prepare('UPDATE users SET branch_id=?, full_name=?, email=?, mobile=?, password_hash=?, role=?, status=? WHERE id=? AND shop_id=?');
                $stmt->execute([$branchId, $fullName, $email !== '' ? $email : null, $mobile, password_hash($password, PASSWORD_DEFAULT), $role, $status, $id, $shopId]);
            } else {
                $stmt = $db->prepare('UPDATE users SET branch_id=?, full_name=?, email=?, mobile=?, role=?, status=? WHERE id=? AND shop_id=?');
                $stmt->execute([$branchId, $fullName, $email !== '' ? $email : null, $mobile, $role, $status, $id, $shopId]);
            }
        } else {
            if (!RateLimit::validShopCredential($password)) Http::error(422, 'weak_password', 'PIN must contain exactly 4 digits.');
            // Premium subscriptions include five active staff accounts. Other plans
            // cannot create staff until the platform administrator upgrades the shop.
            if ($role !== 'super_admin') {
                $planStmt = $db->prepare('SELECT plan FROM shops WHERE id = ?');
                $planStmt->execute([$shopId]);
                $plan = strtolower((string) $planStmt->fetchColumn());
                $staffLimit = str_contains($plan, 'premium') ? 5 : 0;
                $countStmt = $db->prepare("SELECT COUNT(*) FROM users WHERE shop_id = ? AND role <> 'super_admin' AND status = 'active'");
                $countStmt->execute([$shopId]);
                if ((int) $countStmt->fetchColumn() >= $staffLimit) {
                    Http::error(403, 'staff_limit_reached', $staffLimit > 0 ? 'Premium plan allows up to 5 active staff accounts.' : 'Staff accounts require a Premium subscription.');
                }
            }
            $stmt = $db->prepare('INSERT INTO users (shop_id, branch_id, full_name, email, mobile, password_hash, role, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)');
            $stmt->execute([$shopId, $branchId, $fullName, $email !== '' ? $email : null, $mobile, password_hash($password, PASSWORD_DEFAULT), $role, $status]);
            $id = (int) $db->lastInsertId();
        }
        return ['id' => (string) $id, 'shopId' => (string) $shopId, 'branchId' => $branchId, 'fullName' => $fullName, 'mobile' => $mobile, 'email' => $email !== '' ? $email : null, 'role' => $role, 'status' => $status];
    }

    public static function setUserStatus(PDO $db, array $body): void
    {
        $shopId = (int) ($body['shopId'] ?? 0);
        $id = (int) ($body['id'] ?? 0);
        $status = strtolower(trim((string) ($body['status'] ?? '')));
        if ($shopId < 1 || $id < 1 || !in_array($status, ['active', 'blocked', 'deleted'], true)) Http::error(422, 'invalid_user_status', 'Valid user status request is required.');
        $stmt = $db->prepare("UPDATE users SET status=? WHERE id=? AND shop_id=? AND role <> 'super_admin'");
        $stmt->execute([$status, $id, $shopId]);
        if ($stmt->rowCount() < 1) Http::error(404, 'user_not_found', 'Staff user was not found or owner account cannot be changed here.');
        if ($status !== 'active') $db->prepare('UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP() WHERE user_id=? AND shop_id=? AND revoked_at IS NULL')->execute([$id, $shopId]);
    }

    public static function setDeviceStatus(PDO $db, array $body): void
    {
        $shopId = (int) ($body['shopId'] ?? 0);
        $id = (int) ($body['id'] ?? 0);
        $status = strtolower(trim((string) ($body['status'] ?? '')));
        if ($shopId < 1 || $id < 1 || !in_array($status, ['active', 'blocked'], true)) Http::error(422, 'invalid_device_status', 'Valid device status request is required.');
        $stmt = $db->prepare('UPDATE registered_devices SET status=? WHERE id=? AND shop_id=?');
        $stmt->execute([$status, $id, $shopId]);
        if ($stmt->rowCount() < 1) Http::error(404, 'device_not_found', 'Registered device was not found.');
    }

    public static function revokeSessions(PDO $db, array $body): int
    {
        $shopId = (int) ($body['shopId'] ?? 0);
        $userId = (int) ($body['userId'] ?? 0);
        if ($shopId < 1) Http::error(422, 'invalid_shop', 'Shop ID is required.');
        if ($userId > 0) {
            $stmt = $db->prepare('UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP() WHERE shop_id=? AND user_id=? AND revoked_at IS NULL');
            $stmt->execute([$shopId, $userId]);
        } else {
            $stmt = $db->prepare('UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP() WHERE shop_id=? AND revoked_at IS NULL');
            $stmt->execute([$shopId]);
        }
        return $stmt->rowCount();
    }

    public static function resetActivation(PDO $db, array $body): array
    {
        $shopId = (int) ($body['shopId'] ?? 0);
        if ($shopId < 1) Http::error(422, 'invalid_shop', 'Shop ID is required.');
        $shop = $db->prepare('SELECT owner_mobile, status FROM shops WHERE id=?');
        $shop->execute([$shopId]);
        $row = $shop->fetch();
        if (!$row) Http::error(404, 'shop_not_found', 'Shop was not found.');
        if (!in_array($row['status'], ['active', 'trial'], true)) Http::error(409, 'shop_inactive', 'Activate the shop before resetting credentials.');
        $activationCode = 'SB-' . strtoupper(bin2hex(random_bytes(6)));
        $temporaryPassword = str_pad((string) random_int(0, 9999), 4, '0', STR_PAD_LEFT);
        $db->beginTransaction();
        try {
            $db->prepare('DELETE FROM activations WHERE shop_id=? AND used_at IS NULL')->execute([$shopId]);
            $stmt = $db->prepare('INSERT INTO activations (shop_id, branch_id, owner_mobile, activation_code_hash, temporary_password_hash, expires_at) VALUES (?, NULL, ?, ?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 7 DAY))');
            $stmt->execute([$shopId, $row['owner_mobile'], hash('sha256', $activationCode), password_hash($temporaryPassword, PASSWORD_DEFAULT)]);
            $db->prepare('UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP() WHERE shop_id=? AND revoked_at IS NULL')->execute([$shopId]);
            $db->commit();
        } catch (Throwable $error) {
            if ($db->inTransaction()) $db->rollBack();
            throw $error;
        }
        return ['shop_id' => (string) $shopId, 'owner_mobile' => $row['owner_mobile'], 'activation_code' => $activationCode, 'temporary_password' => $temporaryPassword, 'expires_in_days' => 7];
    }

    private static function canonicalMobile(string $mobile): string
    {
        $digits = preg_replace('/\\D+/', '', trim($mobile)) ?? '';
        if (str_starts_with($digits, '0092') && strlen($digits) === 14) {
            return '0' . substr($digits, 4);
        }
        if (str_starts_with($digits, '92') && strlen($digits) === 12) {
            return '0' . substr($digits, 2);
        }
        return $digits;
    }

    public static function permissions(PDO $db, int $shopId): array
    {
        if ($shopId < 1 || !self::exists($db, $shopId)) Http::error(404, 'shop_not_found', 'Shop was not found.');
        self::ensureDefaultPermissions($db, $shopId);
        $stmt = $db->prepare('SELECT role_name AS roleName, permission_key AS permissionKey, access_level AS accessLevel FROM role_permissions WHERE shop_id = ? ORDER BY role_name, permission_key');
        $stmt->execute([$shopId]);
        return [
            'definitions' => self::permissionDefinitions(),
            'rows' => $stmt->fetchAll(),
        ];
    }

    public static function savePermission(PDO $db, array $body): array
    {
        $shopId = (int) ($body['shopId'] ?? $body['shop_id'] ?? 0);
        $role = strtolower(trim((string) ($body['roleName'] ?? $body['role'] ?? '')));
        $key = strtolower(trim((string) ($body['permissionKey'] ?? $body['permission'] ?? '')));
        $level = strtolower(trim((string) ($body['accessLevel'] ?? $body['level'] ?? '')));
        $definitions = self::permissionDefinitions();
        $roles = ['super_admin', 'branch_manager', 'cashier', 'technician', 'accountant'];
        if ($shopId < 1 || !self::exists($db, $shopId) || !in_array($role, $roles, true) || !array_key_exists($key, $definitions) || !in_array($level, ['allow', 'approval', 'block'], true)) {
            Http::error(422, 'invalid_permission', 'Valid shop, role, permission and access level are required.');
        }
        if ($role === 'super_admin' && $level !== 'allow') Http::error(422, 'owner_permission_protected', 'Super Admin permissions must remain allowed.');
        $stmt = $db->prepare('INSERT INTO role_permissions (shop_id, role_name, permission_key, access_level) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE access_level = VALUES(access_level)');
        $stmt->execute([$shopId, $role, $key, $level]);
        return ['shopId' => (string) $shopId, 'roleName' => $role, 'permissionKey' => $key, 'accessLevel' => $level];
    }

    public static function shopData(PDO $db, int $shopId, ?string $entityType = null, int $limit = 300): array
    {
        if ($shopId < 1 || !self::exists($db, $shopId)) Http::error(404, 'shop_not_found', 'Shop was not found.');
        $limit = max(1, min($limit, 500));
        $params = [$shopId];
        $sql = 'SELECT entity_type AS entityType, entity_id AS entityId, branch_id AS branchId, payload, version, updated_at AS updatedAt FROM sync_records WHERE shop_id = ? AND is_deleted = 0';
        if ($entityType !== null && $entityType !== '') {
            $sql .= ' AND entity_type = ?';
            $params[] = $entityType;
        }
        $sql .= ' ORDER BY updated_at DESC LIMIT ' . $limit;
        $stmt = $db->prepare($sql);
        $stmt->execute($params);
        $rows = $stmt->fetchAll();
        foreach ($rows as &$row) {
            $row['payload'] = json_decode((string) $row['payload'], true) ?: [];
            $row['version'] = (int) $row['version'];
            $row['branchId'] = $row['branchId'] !== null ? (int) $row['branchId'] : null;
        }
        return $rows;
    }

    public static function permissionDefinitions(): array
    {
        return [
            'pos_sales' => 'POS / Sales',
            'inventory' => 'Products / Inventory / IMEI / Purchases',
            'repairs' => 'Repairs & Repair Payments',
            'installments' => 'Installments & Collections',
            'customers_suppliers' => 'Customers & Suppliers',
            'wallets' => 'EasyPaisa / JazzCash',
            'expenses' => 'Expenses',
            'cash_closing' => 'Cash Opening / Closing',
            'support' => 'Support Requests',
            'reports' => 'Reports / Analytics',
            'delete_records' => 'Delete / Archive Records',
            'staff_management' => 'Staff & Role Management',
            'backup_restore' => 'Backup / Restore',
        ];
    }

    public static function ensureDefaultPermissions(PDO $db, int $shopId): void
    {
        $definitions = self::permissionDefinitions();
        $defaults = [
            'super_admin' => array_fill_keys(array_keys($definitions), 'allow'),
            'branch_manager' => [
                'pos_sales'=>'allow','inventory'=>'allow','repairs'=>'allow','installments'=>'allow','customers_suppliers'=>'allow','wallets'=>'allow','expenses'=>'allow','cash_closing'=>'allow','support'=>'allow','reports'=>'allow','delete_records'=>'approval','staff_management'=>'approval','backup_restore'=>'approval'
            ],
            'cashier' => [
                'pos_sales'=>'allow','inventory'=>'approval','repairs'=>'block','installments'=>'block','customers_suppliers'=>'allow','wallets'=>'allow','expenses'=>'block','cash_closing'=>'allow','support'=>'allow','reports'=>'block','delete_records'=>'block','staff_management'=>'block','backup_restore'=>'block'
            ],
            'technician' => [
                'pos_sales'=>'block','inventory'=>'approval','repairs'=>'allow','installments'=>'block','customers_suppliers'=>'approval','wallets'=>'block','expenses'=>'block','cash_closing'=>'block','support'=>'allow','reports'=>'block','delete_records'=>'block','staff_management'=>'block','backup_restore'=>'block'
            ],
            'accountant' => [
                'pos_sales'=>'approval','inventory'=>'block','repairs'=>'block','installments'=>'allow','customers_suppliers'=>'allow','wallets'=>'allow','expenses'=>'allow','cash_closing'=>'allow','support'=>'allow','reports'=>'allow','delete_records'=>'block','staff_management'=>'block','backup_restore'=>'approval'
            ],
        ];
        $insert = $db->prepare('INSERT IGNORE INTO role_permissions (shop_id, role_name, permission_key, access_level) VALUES (?, ?, ?, ?)');
        foreach ($defaults as $role => $levels) {
            foreach ($definitions as $key => $_label) {
                $insert->execute([$shopId, $role, $key, $levels[$key] ?? 'block']);
            }
        }
    }

    private static function exists(PDO $db, int $id): bool
    {
        $stmt = $db->prepare('SELECT 1 FROM shops WHERE id = ?');
        $stmt->execute([$id]);
        return (bool) $stmt->fetchColumn();
    }

    private static function status(string $status): string
    {
        if (!in_array($status, ['trial', 'active', 'suspended', 'blocked', 'expired'], true)) {
            Http::error(422, 'invalid_status', 'Shop status is invalid.');
        }
        return $status;
    }

    private static function dateOrNull(mixed $value): ?string
    {
        $date = trim((string) $value);
        if ($date === '') return null;
        $parsed = DateTimeImmutable::createFromFormat('!Y-m-d', $date);
        if (!$parsed || $parsed->format('Y-m-d') !== $date) Http::error(422, 'invalid_date', 'Expiry date must use YYYY-MM-DD.');
        return $date . ' 23:59:59';
    }
}
