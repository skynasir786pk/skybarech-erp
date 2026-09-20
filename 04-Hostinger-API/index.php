<?php
declare(strict_types=1);

require __DIR__ . '/src/Config.php';
require __DIR__ . '/src/Database.php';
require __DIR__ . '/src/Http.php';
require __DIR__ . '/src/Auth.php';
require __DIR__ . '/src/RateLimit.php';
require __DIR__ . '/src/Sync.php';
require __DIR__ . '/src/PlatformAuth.php';
require __DIR__ . '/src/PlatformAdmin.php';
require __DIR__ . '/src/Appearance.php';

try {
    Config::load(__DIR__ . '/config/config.php');
} catch (Throwable) {
    Http::error(503, 'server_not_configured', 'Server configuration is incomplete.');
}

$origin = rtrim((string) ($_SERVER['HTTP_ORIGIN'] ?? ''), '/');
$allowedOrigins = Config::get('allowed_origins', []);
if (is_string($allowedOrigins)) {
    $allowedOrigins = array_filter(array_map(static fn ($value) => rtrim(trim((string) $value), '/'), explode(',', $allowedOrigins)));
} else {
    $allowedOrigins = array_values(array_filter(array_map(static fn ($value) => rtrim(trim((string) $value), '/'), (array) $allowedOrigins)));
}

// Same-origin means scheme, host AND port; other origins must be explicit.
$scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$serverOrigin = $scheme . '://' . strtolower((string) ($_SERVER['HTTP_HOST'] ?? ''));
$sameHostOrigin = $origin !== '' && hash_equals($serverOrigin, strtolower($origin));
$configuredOrigin = $origin !== '' && in_array($origin, $allowedOrigins, true);

if ($sameHostOrigin || $configuredOrigin) {
    header('Access-Control-Allow-Origin: ' . $origin);
    header('Vary: Origin');
    header('Access-Control-Allow-Headers: Authorization, Content-Type, X-Client-Version, X-Device-ID, X-Platform-Token');
    header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
    header('Access-Control-Max-Age: 86400');
}
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    if ($origin !== '' && !$sameHostOrigin && !$configuredOrigin) {
        Http::error(403, 'cors_origin_not_allowed', 'This web origin is not allowed to access the API.');
    }
    http_response_code(204);
    exit;
}

$rewrittenRoute = isset($_GET['_route']) ? trim((string) $_GET['_route'], '/') : '';
if ($rewrittenRoute !== '') {
    $path = '/' . $rewrittenRoute;
    unset($_GET['_route']);
} else {
    $requestPath = '/' . trim((string) parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH), '/');
    $scriptName = str_replace('\\', '/', (string) ($_SERVER['SCRIPT_NAME'] ?? ''));
    $scriptBase = str_ends_with($scriptName, '/index.php')
        ? trim(substr($scriptName, 0, -strlen('/index.php')), '/')
        : trim(str_replace('\\', '/', dirname($scriptName)), '/');
    $pathWithoutSlashes = trim($requestPath, '/');
    if ($scriptBase !== '' && ($pathWithoutSlashes === $scriptBase || str_starts_with($pathWithoutSlashes, $scriptBase . '/'))) {
        $path = '/' . ltrim(substr($pathWithoutSlashes, strlen($scriptBase)), '/');
    } else {
        $path = $requestPath;
    }
}
date_default_timezone_set('UTC');
$method = strtoupper((string) ($_SERVER['REQUEST_METHOD'] ?? 'GET'));

/**
 * Stores and compares Pakistani owner numbers in one stable form.  This keeps
 * the same account usable when a person types 0300…, +92 300…, or adds spaces.
 */
function canonicalMobile(string $mobile): string
{
    $digits = preg_replace('/\\D+/', '', trim($mobile)) ?? '';
    if (str_starts_with($digits, '0092') && strlen($digits) === 14) {
        $digits = '0' . substr($digits, 4);
    } elseif (str_starts_with($digits, '92') && strlen($digits) === 12) {
        $digits = '0' . substr($digits, 2);
    }
    return $digits;
}

function sameMobile(string $left, string $right): bool
{
    $left = canonicalMobile($left);
    $right = canonicalMobile($right);
    return $left !== '' && $right !== '' && hash_equals($left, $right);
}

function requireActiveDevice(PDO $db, array $user): string
{
    $deviceId = trim((string) ($_SERVER['HTTP_X_DEVICE_ID'] ?? ''));
    if (!preg_match('/^[A-Za-z0-9._:-]{8,100}$/', $deviceId)) {
        Http::error(403, 'device_required', 'This device must be registered before sync.');
    }
    $stmt = $db->prepare('SELECT status FROM registered_devices WHERE shop_id = ? AND device_id = ? LIMIT 1');
    $stmt->execute([(int) $user['shop_id'], $deviceId]);
    $status = $stmt->fetchColumn();
    if ($status !== 'active') {
        Http::error(403, 'device_blocked', 'This device is blocked or not registered.');
    }
    return $deviceId;
}

try {
    $db = Database::connection();

    if ($method === 'GET' && $path === '/health') {
        $db->query('SELECT 1')->fetchColumn();
        Http::respond(['success' => true, 'status' => 'online', 'database' => 'connected', 'server_time' => gmdate('c')]);
    }

    if ($method === 'POST' && $path === '/v1/auth/login') {
        $body = Http::jsonBody();
        $identity = trim((string) ($body['identity'] ?? ''));
        $password = (string) ($body['password'] ?? '');
        if ($identity === '' || !RateLimit::validShopCredential($password, 8)) {
            Http::error(422, 'invalid_credentials', 'Enter your owner mobile or email and a 4 digit PIN.');
        }
        RateLimit::login($db, $identity);
        $mobile = canonicalMobile($identity);
        $mobileTail = strlen($mobile) >= 10 ? substr($mobile, -10) : '__not_a_mobile__';
        $stmt = $db->prepare(
            'SELECT u.*, sh.status AS shop_status, sh.expires_at AS shop_expires_at
             FROM users u JOIN shops sh ON sh.id = u.shop_id
             WHERE (LOWER(u.email) = LOWER(?)
                OR RIGHT(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(u.mobile, \' \', \'\'), \'-\', \'\'), \'+\', \'\'), \'(\', \'\'), \')\', \'\'), 10) = ?) LIMIT 2'
        );
        $stmt->execute([$identity, $mobileTail]);
        $matches = $stmt->fetchAll();
        if (count($matches) > 1) Http::error(422, 'ambiguous_identity', 'Use your unique account email; this mobile is linked to multiple shops.');
        $user = $matches[0] ?? false;
        $passwordValid = $user && RateLimit::verifyShopCredential($db, (int) $user['id'], $password);
        RateLimit::record($db, $identity, (bool) $passwordValid);
        if (!$passwordValid) {
            Http::error(401, 'invalid_credentials', 'Identity or password is incorrect.');
        }
        if ($user['status'] !== 'active') {
            Http::error(403, 'user_inactive', 'This user account is blocked or inactive.');
        }
        if (!in_array($user['shop_status'], ['active', 'trial'], true)) {
            Http::error(403, 'shop_inactive', 'This shop is suspended, blocked, or expired.');
        }
        if (!empty($user['shop_expires_at']) && strtotime((string) $user['shop_expires_at']) <= time()) {
            Http::error(403, 'shop_expired', 'This shop subscription has expired.');
        }
        $sessionBranchId = $user['role'] === 'super_admin' ? null : ($user['branch_id'] !== null ? (int) $user['branch_id'] : null);
        $tokens = Auth::issue($db, (int) $user['id'], (int) $user['shop_id'], $sessionBranchId);
        $db->commit();
        Http::respond(['success' => true, 'tokens' => $tokens, 'user' => [
            'id' => (int) $user['id'], 'shop_id' => (int) $user['shop_id'],
            'branch_id' => $sessionBranchId,
            'full_name' => $user['full_name'], 'email' => $user['email'], 'mobile' => $user['mobile'], 'role' => $user['role'],
        ]]);
    }

    if ($method === 'POST' && $path === '/v1/platform/auth/login') {
        $body = Http::jsonBody();
        $email = strtolower(trim((string) ($body['email'] ?? '')));
        $password = (string) ($body['password'] ?? '');
        if (!filter_var($email, FILTER_VALIDATE_EMAIL) || !RateLimit::validShopCredential($password)) {
            Http::error(422, 'invalid_credentials', 'A valid email and exactly 4 numeric PIN digits are required.');
        }
        $db->beginTransaction();
        $stmt = $db->prepare("SELECT * FROM platform_admins WHERE LOWER(email) = ? AND status = 'active' LIMIT 1 FOR UPDATE");
        $stmt->execute([$email]);
        $admin = $stmt->fetch();
        RateLimit::login($db, 'platform:' . $email);
        $valid = $admin && password_verify($password, (string) $admin['password_hash']);
        RateLimit::record($db, 'platform:' . $email, (bool) $valid);
        if (!$valid) { $db->commit(); Http::error(401, 'invalid_credentials', 'Email or PIN is incorrect.'); }
        $tokens = PlatformAuth::issue($db, (int) $admin['id']);
        $db->commit();
        Http::respond(['success' => true, 'tokens' => $tokens, 'admin' => [
            'id' => (int) $admin['id'], 'full_name' => $admin['full_name'], 'email' => $admin['email'],
        ]]);
    }

    if ($method === 'GET' && $path === '/v1/platform/auth/me') {
        $admin = PlatformAuth::requireAdmin($db);
        Http::respond(['success' => true, 'admin' => [
            'id' => (int) $admin['admin_id'], 'full_name' => $admin['full_name'], 'email' => $admin['email'],
        ]]);
    }

    if ($method === 'POST' && $path === '/v1/platform/auth/logout') {
        $admin = PlatformAuth::requireAdmin($db);
        $db->prepare('UPDATE platform_auth_sessions SET revoked_at = UTC_TIMESTAMP() WHERE id = ?')->execute([(int) $admin['session_id']]);
        Http::respond(['success' => true]);
    }

    if ($method === 'GET' && $path === '/v1/platform/shops') {
        PlatformAuth::requireAdmin($db);
        Http::respond(['success' => true, 'shops' => PlatformAdmin::shops($db)]);
    }

    if ($method === 'POST' && $path === '/v1/platform/shops') {
        PlatformAuth::requireAdmin($db);
        Http::respond(['success' => true, 'created' => PlatformAdmin::createShop($db, Http::jsonBody())], 201);
    }

    if ($method === 'POST' && $path === '/v1/platform/shops/update') {
        PlatformAuth::requireAdmin($db);
        PlatformAdmin::updateShop($db, Http::jsonBody());
        Http::respond(['success' => true]);
    }

    if ($method === 'POST' && $path === '/v1/platform/shops/status') {
        PlatformAuth::requireAdmin($db);
        PlatformAdmin::setStatus($db, Http::jsonBody());
        Http::respond(['success' => true]);
    }

    if ($method === 'POST' && $path === '/v1/platform/shops/delete') {
        PlatformAuth::requireAdmin($db);
        PlatformAdmin::deleteShop($db, Http::jsonBody());
        Http::respond(['success' => true]);
    }

    if ($path === '/v1/platform/appearance' && in_array($method, ['GET', 'POST'], true)) {
        PlatformAuth::requireAdmin($db);
        $body = $method === 'POST' ? Http::jsonBody() : $_GET;
        $shopId = (int) ($body['shop_id'] ?? 0);
        $exists = $db->prepare('SELECT id FROM shops WHERE id = ?');
        $exists->execute([$shopId]);
        if (!$exists->fetchColumn()) Http::error(404, 'shop_not_found', 'Shop not found.');
        if ($method === 'POST') Appearance::save($db, $shopId, $body);
        Http::respond(['success' => true, 'appearance' => Appearance::read($db, $shopId)]);
    }

    if ($method === 'GET' && $path === '/v1/platform/shop-control') {
        PlatformAuth::requireAdmin($db);
        $shopId = max(0, (int) ($_GET['shop_id'] ?? 0));
        Http::respond(['success' => true, 'control' => PlatformAdmin::shopControl($db, $shopId)]);
    }

    if ($method === 'POST' && $path === '/v1/platform/branches') {
        PlatformAuth::requireAdmin($db);
        Http::respond(['success' => true, 'branch' => PlatformAdmin::saveBranch($db, Http::jsonBody())], 201);
    }

    if ($method === 'POST' && $path === '/v1/platform/users') {
        PlatformAuth::requireAdmin($db);
        Http::respond(['success' => true, 'user' => PlatformAdmin::saveUser($db, Http::jsonBody())], 201);
    }

    if ($method === 'POST' && $path === '/v1/platform/users/status') {
        PlatformAuth::requireAdmin($db);
        PlatformAdmin::setUserStatus($db, Http::jsonBody());
        Http::respond(['success' => true]);
    }

    if ($method === 'POST' && $path === '/v1/platform/devices/status') {
        PlatformAuth::requireAdmin($db);
        PlatformAdmin::setDeviceStatus($db, Http::jsonBody());
        Http::respond(['success' => true]);
    }

    if ($method === 'POST' && $path === '/v1/platform/sessions/revoke') {
        PlatformAuth::requireAdmin($db);
        Http::respond(['success' => true, 'revoked' => PlatformAdmin::revokeSessions($db, Http::jsonBody())]);
    }

    if ($method === 'POST' && $path === '/v1/platform/activation/reset') {
        PlatformAuth::requireAdmin($db);
        Http::respond(['success' => true, 'activation' => PlatformAdmin::resetActivation($db, Http::jsonBody())], 201);
    }

    if ($method === 'GET' && $path === '/v1/admin/control') {
        $user = Auth::requireUser($db);
        if ($user['role'] !== 'super_admin') Http::error(403, 'admin_required', 'Shop owner access is required.');
        Http::respond(['success' => true, 'control' => PlatformAdmin::shopControl($db, (int) $user['shop_id'])]);
    }

    if ($method === 'POST' && $path === '/v1/admin/users') {
        $user = Auth::requireUser($db);
        if ($user['role'] !== 'super_admin') Http::error(403, 'admin_required', 'Shop owner access is required.');
        $body = Http::jsonBody();
        $body['shopId'] = (int) $user['shop_id'];
        if (strtolower((string) ($body['role'] ?? '')) === 'super_admin') Http::error(403, 'owner_protected', 'Additional Super Admin users cannot be created from a shop device.');
        Http::respond(['success' => true, 'user' => PlatformAdmin::saveUser($db, $body)], 201);
    }

    if ($method === 'POST' && $path === '/v1/admin/users/status') {
        $user = Auth::requireUser($db);
        if ($user['role'] !== 'super_admin') Http::error(403, 'admin_required', 'Shop owner access is required.');
        $body = Http::jsonBody();
        $body['shopId'] = (int) $user['shop_id'];
        PlatformAdmin::setUserStatus($db, $body);
        Http::respond(['success' => true]);
    }

    if ($method === 'GET' && $path === '/v1/platform/permissions') {
        PlatformAuth::requireAdmin($db);
        $shopId = max(0, (int) ($_GET['shop_id'] ?? 0));
        Http::respond(['success' => true, 'permissions' => PlatformAdmin::permissions($db, $shopId)]);
    }

    if ($method === 'POST' && $path === '/v1/platform/permissions') {
        PlatformAuth::requireAdmin($db);
        Http::respond(['success' => true, 'permission' => PlatformAdmin::savePermission($db, Http::jsonBody())]);
    }

    if ($method === 'GET' && $path === '/v1/platform/shop-data') {
        PlatformAuth::requireAdmin($db);
        $shopId = max(0, (int) ($_GET['shop_id'] ?? 0));
        $entityType = trim((string) ($_GET['entity_type'] ?? ''));
        $limit = max(1, min((int) ($_GET['limit'] ?? 300), 500));
        Http::respond(['success' => true, 'records' => PlatformAdmin::shopData($db, $shopId, $entityType !== '' ? $entityType : null, $limit)]);
    }

    if ($method === 'POST' && $path === '/v1/auth/refresh') {
        $body = Http::jsonBody();
        $refresh = (string) ($body['refresh_token'] ?? '');
        if (!preg_match('/^[A-Za-z0-9_-]{32,256}$/', $refresh)) {
            Http::error(401, 'invalid_refresh_token', 'Refresh token is invalid.');
        }
        $stmt = $db->prepare(
            "SELECT s.*, u.status AS user_status, u.role, sh.status AS shop_status, sh.expires_at AS shop_expires_at
             FROM auth_sessions s
             JOIN users u ON u.id = s.user_id
             JOIN shops sh ON sh.id = s.shop_id
             WHERE s.refresh_token_hash = ? AND s.revoked_at IS NULL AND s.refresh_expires_at > UTC_TIMESTAMP() LIMIT 1"
        );
        $stmt->execute([Auth::hash($refresh)]);
        $session = $stmt->fetch();
        if (!$session) {
            Http::error(401, 'invalid_refresh_token', 'Refresh token is expired or revoked.');
        }
        if ($session['user_status'] !== 'active') Http::error(403, 'user_inactive', 'This user account is blocked or inactive.');
        if (!in_array($session['shop_status'], ['active', 'trial'], true)) Http::error(403, 'shop_inactive', 'This shop is suspended, blocked, or expired.');
        if (!empty($session['shop_expires_at']) && strtotime((string) $session['shop_expires_at']) <= time()) Http::error(403, 'shop_expired', 'This shop subscription has expired.');
        $db->prepare('UPDATE auth_sessions SET revoked_at = UTC_TIMESTAMP() WHERE id = ?')->execute([(int) $session['id']]);
        $sessionBranchId = $session['role'] === 'super_admin' ? null : ($session['branch_id'] !== null ? (int) $session['branch_id'] : null);
        Http::respond(['success' => true, 'tokens' => Auth::issue($db, (int) $session['user_id'], (int) $session['shop_id'], $sessionBranchId)]);
    }

    if ($method === 'POST' && $path === '/v1/auth/password') {
        $user = Auth::requireUser($db);
        $body = Http::jsonBody();
        $current = (string) ($body['current_password'] ?? '');
        $fresh = (string) ($body['new_password'] ?? '');
        if (!RateLimit::validShopCredential($fresh) || strlen($current) > 200) {
            Http::error(422, 'invalid_password', 'Choose a 4 digit PIN.');
        }
        $key = 'password:' . $user['user_id'];
        RateLimit::login($db, $key);
        $db->beginTransaction();
        $stmt = $db->prepare('SELECT password_hash FROM users WHERE id = ? AND shop_id = ? FOR UPDATE');
        $stmt->execute([(int) $user['user_id'], (int) $user['shop_id']]);
        $currentHash = (string) $stmt->fetchColumn();
        RateLimit::account($db, (int) $user['user_id']);
        $valid = password_verify($current, $currentHash);
        if (!$valid) {
            $db->rollBack();
            RateLimit::record($db, 'shop-user:' . $user['user_id'], false);
            RateLimit::record($db, $key, false);
            Http::error(401, 'invalid_credentials', 'Current password is incorrect.');
        }
        $db->prepare('UPDATE users SET password_hash = ? WHERE id = ? AND shop_id = ?')
            ->execute([password_hash($fresh, PASSWORD_DEFAULT), (int) $user['user_id'], (int) $user['shop_id']]);
        $db->prepare('UPDATE auth_sessions SET revoked_at = UTC_TIMESTAMP() WHERE user_id = ? AND shop_id = ?')
            ->execute([(int) $user['user_id'], (int) $user['shop_id']]);
        $tokens = Auth::issue($db, (int) $user['user_id'], (int) $user['shop_id'], $user['branch_id'] === null ? null : (int) $user['branch_id']);
        $db->commit();
        Http::respond(['success' => true, 'tokens' => $tokens]);
    }

    if ($method === 'POST' && $path === '/v1/auth/logout') {
        $user = Auth::requireUser($db);
        $db->prepare('UPDATE auth_sessions SET revoked_at = UTC_TIMESTAMP() WHERE id = ?')->execute([(int) $user['session_id']]);
        Http::respond(['success' => true]);
    }

    if ($method === 'POST' && $path === '/v1/activation/verify') {
        $body = Http::jsonBody();
        $code = strtoupper(trim((string) ($body['code'] ?? '')));
        $mobile = canonicalMobile((string) ($body['mobile'] ?? ''));
        $password = (string) ($body['temporary_password'] ?? '');
        RateLimit::login($db, 'activation:' . $mobile);
        $stmt = $db->prepare(
            'SELECT a.*, sh.name AS shop_name, sh.status AS shop_status, sh.expires_at AS shop_expires_at
             FROM activations a JOIN shops sh ON sh.id = a.shop_id
             WHERE a.activation_code_hash = ? AND a.used_at IS NULL AND a.expires_at > UTC_TIMESTAMP() LIMIT 1'
        );
        $stmt->execute([hash('sha256', $code)]);
        $activation = $stmt->fetch();
        $validActivation = $activation && sameMobile((string) $activation['owner_mobile'], $mobile)
            && password_verify($password, (string) $activation['temporary_password_hash'])
            && in_array($activation['shop_status'], ['active', 'trial'], true)
            && (empty($activation['shop_expires_at']) || strtotime((string) $activation['shop_expires_at']) > time());
        RateLimit::record($db, 'activation:' . $mobile, (bool) $validActivation);
        if (!$validActivation) {
            Http::error(401, 'activation_invalid', 'Activation details are invalid or expired.');
        }
        Http::respond(['success' => true, 'activation' => [
            'activation_id' => (int) $activation['id'], 'shop_id' => (int) $activation['shop_id'],
            'branch_id' => $activation['branch_id'] !== null ? (int) $activation['branch_id'] : null,
            'shop_name' => $activation['shop_name'], 'expires_at' => $activation['expires_at'],
        ]]);
    }

    if ($method === 'POST' && $path === '/v1/activation/complete') {
        $body = Http::jsonBody();
        $activationId = (int) ($body['activation_id'] ?? 0);
        $code = strtoupper(trim((string) ($body['code'] ?? '')));
        $mobile = canonicalMobile((string) ($body['mobile'] ?? ''));
        $temporaryPassword = (string) ($body['temporary_password'] ?? '');
        $newPassword = (string) ($body['new_password'] ?? '');
        $fullName = trim((string) ($body['full_name'] ?? 'Owner'));
        $email = strtolower(trim((string) ($body['email'] ?? '')));
        if ($activationId < 1 || $code === '' || $mobile === '' || !RateLimit::validShopCredential($newPassword)
            || ($email !== '' && !filter_var($email, FILTER_VALIDATE_EMAIL))) {
            Http::error(422, 'activation_data_invalid', 'Valid activation details and a 4 digit PIN are required.');
        }
        RateLimit::login($db, 'activation:' . $mobile);
        $db->beginTransaction();
        try {
            $stmt = $db->prepare(
                'SELECT a.*, sh.status AS shop_status, sh.expires_at AS shop_expires_at FROM activations a
                 JOIN shops sh ON sh.id = a.shop_id
                 WHERE a.id = ? AND a.activation_code_hash = ?
                   AND a.used_at IS NULL AND a.expires_at > UTC_TIMESTAMP() FOR UPDATE'
            );
            $stmt->execute([$activationId, hash('sha256', $code)]);
            $activation = $stmt->fetch();
            $valid = $activation && sameMobile((string) $activation['owner_mobile'], $mobile)
                && password_verify($temporaryPassword, (string) $activation['temporary_password_hash'])
                && in_array($activation['shop_status'], ['active', 'trial'], true)
                && (empty($activation['shop_expires_at']) || strtotime((string) $activation['shop_expires_at']) > time());
            if (!$valid) {
                $db->rollBack();
                RateLimit::record($db, 'activation:' . $mobile, false);
                Http::error(401, 'activation_invalid', 'Activation details are invalid or expired.');
            }
            RateLimit::record($db, 'activation:' . $mobile, true);
            $findUser = $db->prepare("SELECT id FROM users WHERE shop_id = ? AND RIGHT(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(mobile, ' ', ''), '-', ''), '+', ''), '(', ''), ')', ''), 10) = ? LIMIT 1 FOR UPDATE");
            $findUser->execute([(int) $activation['shop_id'], substr($mobile, -10)]);
            $userId = (int) ($findUser->fetchColumn() ?: 0);
            $passwordHash = password_hash($newPassword, PASSWORD_DEFAULT);
            if ($userId > 0) {
                $update = $db->prepare("UPDATE users SET branch_id = NULL, full_name = ?, email = ?, mobile = ?, password_hash = ?, role = 'super_admin', status = 'active' WHERE id = ?");
                $update->execute([$fullName, $email !== '' ? $email : null, $mobile, $passwordHash, $userId]);
            } else {
                $insert = $db->prepare("INSERT INTO users (shop_id, branch_id, full_name, email, mobile, password_hash, role, status) VALUES (?, NULL, ?, ?, ?, ?, 'super_admin', 'active')");
                $insert->execute([(int) $activation['shop_id'], $fullName, $email !== '' ? $email : null, $mobile, $passwordHash]);
                $userId = (int) $db->lastInsertId();
            }
            $db->prepare('UPDATE activations SET used_at = UTC_TIMESTAMP() WHERE id = ?')->execute([$activationId]);
            $db->prepare('UPDATE auth_sessions SET revoked_at = UTC_TIMESTAMP() WHERE user_id = ?')->execute([$userId]);
            $tokens = Auth::issue($db, $userId, (int) $activation['shop_id'], null);
            $db->commit();
            Http::respond(['success' => true, 'tokens' => $tokens, 'user' => [
                'id' => $userId, 'shop_id' => (int) $activation['shop_id'],
                'branch_id' => null,
                'full_name' => $fullName, 'email' => $email !== '' ? $email : null, 'mobile' => $mobile, 'role' => 'super_admin',
            ]], 201);
        } catch (Throwable $error) {
            if ($db->inTransaction()) {
                $db->rollBack();
            }
            throw $error;
        }
    }

    if ($method === 'GET' && in_array($path, ['/v1/bootstrap', '/v1/biometric/bootstrap'], true)) {
        $user = Auth::requireUser($db);
        if ($path === '/v1/biometric/bootstrap') requireActiveDevice($db, $user);
        $shopStmt = $db->prepare('SELECT id, name, code, status, plan, expires_at, owner_name, owner_mobile, city, address FROM shops WHERE id = ?');
        $shopStmt->execute([(int) $user['shop_id']]);
        $branchStmt = $db->prepare('SELECT id, name, code, address, contact_1, contact_2, contact_3, status FROM branches WHERE shop_id = ? AND status = ? ORDER BY id');
        $branchStmt->execute([(int) $user['shop_id'], 'active']);
        PlatformAdmin::ensureDefaultPermissions($db, (int) $user['shop_id']);
        $permissionStmt = $db->prepare('SELECT permission_key, access_level FROM role_permissions WHERE shop_id = ? AND role_name = ?');
        $permissionStmt->execute([(int) $user['shop_id'], $user['role']]);
        Http::respond(['success' => true, 'shop' => array_merge($shopStmt->fetch(), ['appearance' => Appearance::read($db, (int) $user['shop_id'])]), 'branches' => $branchStmt->fetchAll(), 'permissions' => $permissionStmt->fetchAll()]);
    }

    if ($method === 'POST' && $path === '/v1/devices/register') {
        $user = Auth::requireUser($db);
        $body = Http::jsonBody();
        $deviceId = trim((string) ($body['device_id'] ?? ($_SERVER['HTTP_X_DEVICE_ID'] ?? '')));
        $platform = trim((string) ($body['platform'] ?? ''));
        if (!preg_match('/^[A-Za-z0-9._:-]{8,100}$/', $deviceId) || !in_array($platform, ['android', 'desktop'], true)) {
            Http::error(422, 'invalid_device', 'A valid device ID and platform are required.');
        }
        $stmt = $db->prepare(
            "INSERT INTO registered_devices (shop_id, branch_id, user_id, device_id, platform, status, last_seen_at)
             VALUES (?, ?, ?, ?, ?, 'active', UTC_TIMESTAMP())
             ON DUPLICATE KEY UPDATE user_id=VALUES(user_id), branch_id=VALUES(branch_id), platform=VALUES(platform), last_seen_at=UTC_TIMESTAMP()"
        );
        $stmt->execute([(int) $user['shop_id'], $user['branch_id'], (int) $user['user_id'], $deviceId, $platform]);
        $statusStmt = $db->prepare('SELECT status FROM registered_devices WHERE shop_id = ? AND device_id = ? LIMIT 1');
        $statusStmt->execute([(int) $user['shop_id'], $deviceId]);
        if ($statusStmt->fetchColumn() !== 'active') {
            Http::error(403, 'device_blocked', 'This device is blocked by Super Admin.');
        }
        Http::respond(['success' => true, 'device_id' => $deviceId], 201);
    }

    if ($method === 'POST' && $path === '/v1/sync/push') {
        $user = Auth::requireUser($db);
        requireActiveDevice($db, $user);
        Http::respond(['success' => true, 'results' => Sync::push($db, $user, Http::jsonBody()), 'server_time' => gmdate('c')]);
    }

    if ($method === 'GET' && $path === '/v1/sync/pull') {
        $user = Auth::requireUser($db);
        requireActiveDevice($db, $user);
        $cursor = max(0, (int) ($_GET['cursor'] ?? 0));
        $limit = max(1, min((int) ($_GET['limit'] ?? 200), 500));
        $records = Sync::pull($db, $user, $cursor, $limit);
        $nextCursor = $records ? (int) end($records)['cursor'] : $cursor;
        Http::respond(['success' => true, 'records' => $records, 'next_cursor' => $nextCursor, 'has_more' => count($records) === $limit]);
    }

    Http::error(404, 'not_found', 'API endpoint not found.');
} catch (PDOException $error) {
    if (isset($db) && $db->inTransaction()) $db->rollBack();
    error_log('Database error [' . Http::requestId() . ']: ' . $error->getMessage());
    Http::error(500, 'database_error', 'The server could not complete the database operation.');
} catch (Throwable $error) {
    if (isset($db) && $db->inTransaction()) $db->rollBack();
    error_log('Server error [' . Http::requestId() . ']: ' . $error->getMessage());
    Http::error(500, 'server_error', 'The server could not complete the request.');
}
