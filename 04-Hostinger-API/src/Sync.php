<?php
declare(strict_types=1);

final class Sync
{
    private const ENTITY_TYPES = [
        'product', 'imei', 'purchase', 'sale', 'repair', 'repair_payment', 'installment',
        'installment_payment', 'customer', 'supplier', 'expense', 'wallet', 'cash_session',
        'support_request', 'shop_profile', 'device_snapshot', 'staff_activity',
    ];

    private const ENTITY_PERMISSIONS = [
        'product' => 'inventory', 'imei' => 'inventory', 'purchase' => 'inventory',
        'sale' => 'pos_sales',
        'repair' => 'repairs', 'repair_payment' => 'repairs',
        'installment' => 'installments', 'installment_payment' => 'installments',
        'customer' => 'customers_suppliers', 'supplier' => 'customers_suppliers',
        'expense' => 'expenses', 'wallet' => 'wallets', 'cash_session' => 'cash_closing',
        'support_request' => 'support', 'shop_profile' => 'staff_management',
        'staff_activity' => 'support', 'device_snapshot' => 'backup_restore',
    ];

    public static function push(PDO $db, array $user, array $body): array
    {
        $operations = $body['operations'] ?? null;
        if (!is_array($operations) || count($operations) > 100) {
            Http::error(422, 'invalid_operations', 'Operations must be an array with at most 100 items.');
        }
        $results = [];
        $permissionLevels = self::permissionLevels($db, $user);
        $db->beginTransaction();
        try {
            foreach ($operations as $operation) {
                $results[] = self::apply($db, $user, is_array($operation) ? $operation : [], $permissionLevels);
            }
            $db->commit();
        } catch (Throwable $error) {
            if ($db->inTransaction()) {
                $db->rollBack();
            }
            throw $error;
        }
        return $results;
    }

    private static function apply(PDO $db, array $user, array $operation, array $permissionLevels): array
    {
        $operationId = trim((string) ($operation['operation_id'] ?? ''));
        $entityType = trim((string) ($operation['entity_type'] ?? ''));
        $entityId = trim((string) ($operation['entity_id'] ?? ''));
        $action = trim((string) ($operation['action'] ?? ''));
        $deviceId = trim((string) ($operation['device_id'] ?? ($_SERVER['HTTP_X_DEVICE_ID'] ?? '')));
        $expectedVersion = isset($operation['base_version']) ? (int) $operation['base_version'] : null;
        $payload = $operation['payload'] ?? [];
        if (!preg_match('/^[A-Za-z0-9._:-]{8,100}$/', $operationId)
            || !in_array($entityType, self::ENTITY_TYPES, true)
            || !preg_match('/^[A-Za-z0-9._:-]{1,100}$/', $entityId)
            || !in_array($action, ['upsert', 'delete'], true)
            || ($deviceId !== '' && !preg_match('/^[A-Za-z0-9._:-]{8,100}$/', $deviceId))
            || !is_array($payload)) {
            return ['operation_id' => $operationId, 'status' => 'rejected', 'error' => 'invalid_operation'];
        }

        if ($user['role'] !== 'super_admin') {
            if ($entityType === 'device_snapshot') {
                return ['operation_id' => $operationId, 'status' => 'rejected', 'error' => 'owner_only_snapshot'];
            }
            $permissionKey = self::ENTITY_PERMISSIONS[$entityType] ?? null;
            $level = $permissionKey !== null ? ($permissionLevels[$permissionKey] ?? 'block') : 'block';
            if ($level === 'block') {
                return ['operation_id' => $operationId, 'status' => 'rejected', 'error' => 'permission_denied'];
            }
            if ($level === 'approval') {
                return ['operation_id' => $operationId, 'status' => 'rejected', 'error' => 'permission_approval_required'];
            }
            if ($action === 'delete') {
                $deleteLevel = $permissionLevels['delete_records'] ?? 'block';
                if ($deleteLevel === 'block') return ['operation_id' => $operationId, 'status' => 'rejected', 'error' => 'delete_permission_denied'];
                if ($deleteLevel === 'approval') return ['operation_id' => $operationId, 'status' => 'rejected', 'error' => 'delete_approval_required'];
            }
        }

        $existing = $db->prepare('SELECT result_status FROM sync_operations WHERE shop_id = ? AND operation_id = ?');
        $existing->execute([(int) $user['shop_id'], $operationId]);
        $previous = $existing->fetchColumn();
        if ($previous !== false) {
            return $previous === 'conflict'
                ? ['operation_id' => $operationId, 'status' => 'conflict', 'error' => 'version_conflict']
                : ['operation_id' => $operationId, 'status' => 'duplicate', 'result' => $previous];
        }

        $branchId = $user['branch_id'] !== null ? (int) $user['branch_id'] : null;
        if ($deviceId !== '') {
            $device = $db->prepare('SELECT status FROM registered_devices WHERE shop_id = ? AND device_id = ? LIMIT 1');
            $device->execute([(int) $user['shop_id'], $deviceId]);
            $deviceStatus = $device->fetchColumn();
            if ($deviceStatus !== 'active') {
                return ['operation_id' => $operationId, 'status' => 'rejected', 'error' => 'device_not_registered_or_blocked'];
            }
        }
        $payloadJson = json_encode($payload, JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE);
        $current = $db->prepare('SELECT version, branch_id FROM sync_records WHERE shop_id = ? AND entity_type = ? AND entity_id = ? FOR UPDATE');
        $current->execute([(int) $user['shop_id'], $entityType, $entityId]);
        $currentRecord = $current->fetch();
        if ($currentRecord && $user['role'] !== 'super_admin' && $branchId !== null
            && ($currentRecord['branch_id'] === null || (int) $currentRecord['branch_id'] !== $branchId)) {
            return ['operation_id' => $operationId, 'status' => 'rejected', 'error' => 'branch_write_denied'];
        }
        $serverVersion = $currentRecord === false ? 0 : (int) $currentRecord['version'];
        if ($expectedVersion !== null && $expectedVersion !== $serverVersion) {
            $conflict = $db->prepare(
                'INSERT INTO sync_conflicts (shop_id, entity_type, entity_id, operation_id, device_id, expected_version, server_version, client_payload)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
            );
            $conflict->execute([(int) $user['shop_id'], $entityType, $entityId, $operationId, $deviceId, $expectedVersion, $serverVersion, $payloadJson]);
            $logConflict = $db->prepare(
                "INSERT INTO sync_operations (shop_id, branch_id, user_id, operation_id, entity_type, entity_id, action_name, device_id, result_status)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'conflict')"
            );
            $logConflict->execute([(int) $user['shop_id'], $branchId, (int) $user['user_id'], $operationId, $entityType, $entityId, $action, $deviceId]);
            return ['operation_id' => $operationId, 'status' => 'conflict', 'server_version' => $serverVersion, 'error' => 'version_conflict'];
        }
        $deleted = $action === 'delete' ? 1 : 0;
        $stmt = $db->prepare(
            'INSERT INTO sync_records (shop_id, branch_id, entity_type, entity_id, payload, is_deleted, version, updated_by)
             VALUES (?, ?, ?, ?, ?, ?, 1, ?)
             ON DUPLICATE KEY UPDATE
               payload = VALUES(payload), is_deleted = VALUES(is_deleted), version = version + 1,
               updated_by = VALUES(updated_by), updated_at = UTC_TIMESTAMP()'
        );
        $stmt->execute([(int) $user['shop_id'], $branchId, $entityType, $entityId, $payloadJson, $deleted, (int) $user['user_id']]);
        $change = $db->prepare(
            'INSERT INTO sync_changes (shop_id, branch_id, entity_type, entity_id, payload, is_deleted, changed_by)
             VALUES (?, ?, ?, ?, ?, ?, ?)'
        );
        $change->execute([(int) $user['shop_id'], $branchId, $entityType, $entityId, $payloadJson, $deleted, (int) $user['user_id']]);
        $cursor = (int) $db->lastInsertId();
        $log = $db->prepare(
            'INSERT INTO sync_operations (shop_id, branch_id, user_id, operation_id, entity_type, entity_id, action_name, device_id, result_status)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
        );
        $log->execute([(int) $user['shop_id'], $branchId, (int) $user['user_id'], $operationId, $entityType, $entityId, $action, $deviceId, 'applied']);
        return ['operation_id' => $operationId, 'status' => 'applied', 'cursor' => $cursor, 'version' => $serverVersion + 1];
    }

    public static function pull(PDO $db, array $user, int $cursor, int $limit): array
    {
        $limit = max(1, min($limit, 500));
        // CURSOR is reserved in MySQL/MariaDB; quote the wire-format alias.
        $sql = 'SELECT id AS `cursor`, entity_type, entity_id, payload, is_deleted, changed_at AS updated_at
                FROM sync_changes WHERE shop_id = ? AND id > ?';
        $params = [(int) $user['shop_id'], $cursor];
        if ($user['branch_id'] !== null) {
            $sql .= ' AND (branch_id = ? OR branch_id IS NULL)';
            $params[] = (int) $user['branch_id'];
        }
        if ($user['role'] !== 'super_admin') {
            $levels = self::permissionLevels($db, $user);
            $allowedTypes = [];
            foreach (self::ENTITY_PERMISSIONS as $entityType => $permissionKey) {
                if ($entityType === 'device_snapshot' || $entityType === 'shop_profile') continue;
                if (($levels[$permissionKey] ?? 'block') !== 'block') $allowedTypes[] = $entityType;
            }
            $allowedTypes = array_values(array_unique($allowedTypes));
            if (!$allowedTypes) return [];
            $sql .= ' AND entity_type IN (' . implode(',', array_fill(0, count($allowedTypes), '?')) . ')';
            array_push($params, ...$allowedTypes);
        }
        $sql .= ' ORDER BY id ASC LIMIT ' . $limit;
        $stmt = $db->prepare($sql);
        $stmt->execute($params);
        $rows = $stmt->fetchAll();
        $deletedRows = $db->prepare('SELECT entity_type, entity_id FROM sync_records WHERE shop_id = ? AND is_deleted = 1');
        $deletedRows->execute([(int) $user['shop_id']]);
        $deleted = [];
        foreach ($deletedRows->fetchAll() as $entry) $deleted[$entry['entity_type']][(string) $entry['entity_id']] = true;
        $aliases = ['products'=>'product', 'sales'=>'sale', 'invoices'=>'sale', 'repairs'=>'repair',
            'customers'=>'customer', 'suppliers'=>'supplier', 'installments'=>'installment', 'ewallets'=>'wallet',
            'expenses'=>'expense', 'cashClosings'=>'cash_session', 'cashSessions'=>'cash_session', 'supportRequests'=>'support_request'];
        foreach ($rows as &$row) {
            $row['payload'] = json_decode((string) $row['payload'], true) ?: [];
            if ($row['entity_type'] === 'device_snapshot') {
                $snapshot =& $row['payload'];
                if (isset($snapshot['snapshot']) && is_array($snapshot['snapshot'])) $snapshot =& $snapshot['snapshot'];
                foreach ($aliases as $collection => $type) {
                    if (!isset($snapshot[$collection]) || !is_array($snapshot[$collection])) continue;
                    $snapshot[$collection] = array_values(array_filter($snapshot[$collection],
                        static fn ($item) => is_array($item) && !isset($deleted[$type][(string) ($item['id'] ?? '')])));
                }
                unset($snapshot);
            }
            $row['is_deleted'] = (bool) $row['is_deleted'];
            $row['cursor'] = (int) $row['cursor'];
        }
        return $rows;
    }

    private static function permissionLevels(PDO $db, array $user): array
    {
        if (($user['role'] ?? '') === 'super_admin') return [];
        PlatformAdmin::ensureDefaultPermissions($db, (int) $user['shop_id']);
        $stmt = $db->prepare('SELECT permission_key, access_level FROM role_permissions WHERE shop_id = ? AND role_name = ?');
        $stmt->execute([(int) $user['shop_id'], (string) $user['role']]);
        $levels = [];
        foreach ($stmt->fetchAll() as $row) $levels[(string) $row['permission_key']] = (string) $row['access_level'];
        return $levels;
    }
}
