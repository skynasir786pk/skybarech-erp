<?php
declare(strict_types=1);

final class Auth
{
    public static function issue(PDO $db, int $userId, int $shopId, ?int $branchId): array
    {
        $access = self::randomToken();
        $refresh = self::randomToken();
        $accessTtl = (int) Config::get('token_ttl_seconds', 43200);
        $refreshTtl = (int) Config::get('refresh_ttl_seconds', 2592000);
        $stmt = $db->prepare(
            'INSERT INTO auth_sessions
             (user_id, shop_id, branch_id, access_token_hash, refresh_token_hash, access_expires_at, refresh_expires_at, ip_address, user_agent)
             VALUES (?, ?, ?, ?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? SECOND), DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? SECOND), ?, ?)'
        );
        $stmt->execute([
            $userId, $shopId, $branchId, self::hash($access), self::hash($refresh),
            $accessTtl, $refreshTtl, Http::clientIp(), substr((string) ($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 255),
        ]);
        return ['access_token' => $access, 'refresh_token' => $refresh, 'token_type' => 'Bearer', 'expires_in' => $accessTtl];
    }

    public static function requireUser(PDO $db): array
    {
        $token = Http::bearerToken();
        if ($token === null) {
            Http::error(401, 'authentication_required', 'A valid access token is required.');
        }
        $stmt = $db->prepare(
            'SELECT s.id AS session_id, s.user_id, s.shop_id, u.branch_id, u.full_name, u.email, u.mobile, u.role,
                    u.status AS user_status, sh.status AS shop_status, sh.expires_at AS shop_expires_at
             FROM auth_sessions s
             JOIN users u ON u.id = s.user_id AND u.shop_id = s.shop_id
             JOIN shops sh ON sh.id = s.shop_id
             WHERE s.access_token_hash = ? AND s.revoked_at IS NULL AND s.access_expires_at > UTC_TIMESTAMP()
             LIMIT 1'
        );
        $stmt->execute([self::hash($token)]);
        $user = $stmt->fetch();
        if (!$user) {
            Http::error(401, 'session_invalid', 'Session is expired or revoked.');
        }
        if ($user['user_status'] !== 'active') {
            Http::error(403, 'user_inactive', 'This user account is blocked or inactive.');
        }
        if (!in_array($user['shop_status'], ['active', 'trial'], true)) {
            Http::error(403, 'shop_inactive', 'This shop is suspended, blocked, or expired.');
        }
        if (!empty($user['shop_expires_at']) && strtotime((string) $user['shop_expires_at']) <= time()) {
            Http::error(403, 'shop_expired', 'This shop subscription has expired.');
        }
        if (($user['role'] ?? '') === 'super_admin') {
            $user['branch_id'] = null;
        }
        return $user;
    }

    public static function hash(string $token): string
    {
        return hash('sha256', $token);
    }

    private static function randomToken(): string
    {
        return rtrim(strtr(base64_encode(random_bytes(48)), '+/', '-_'), '=');
    }
}

