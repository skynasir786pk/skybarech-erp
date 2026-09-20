<?php
declare(strict_types=1);

final class PlatformAuth
{
    public static function issue(PDO $db, int $adminId): array
    {
        $token = rtrim(strtr(base64_encode(random_bytes(48)), '+/', '-_'), '=');
        $ttl = (int) Config::get('token_ttl_seconds', 43200);
        $stmt = $db->prepare(
            'INSERT INTO platform_auth_sessions
             (admin_id, access_token_hash, access_expires_at, ip_address, user_agent)
             VALUES (?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? SECOND), ?, ?)'
        );
        $stmt->execute([
            $adminId, hash('sha256', $token), $ttl, Http::clientIp(),
            substr((string) ($_SERVER['HTTP_USER_AGENT'] ?? ''), 0, 255),
        ]);
        return ['access_token' => $token, 'token_type' => 'Bearer', 'expires_in' => $ttl];
    }

    public static function requireAdmin(PDO $db): array
    {
        $token = Http::bearerToken('HTTP_X_PLATFORM_TOKEN');
        if ($token === null) {
            Http::error(401, 'platform_authentication_required', 'A valid platform administrator token is required.');
        }
        $stmt = $db->prepare(
            "SELECT a.id AS admin_id, a.full_name, a.email, s.id AS session_id
             FROM platform_auth_sessions s JOIN platform_admins a ON a.id = s.admin_id
             WHERE s.access_token_hash = ? AND s.revoked_at IS NULL
               AND s.access_expires_at > UTC_TIMESTAMP() AND a.status = 'active' LIMIT 1"
        );
        $stmt->execute([hash('sha256', $token)]);
        $admin = $stmt->fetch();
        if (!$admin) {
            Http::error(401, 'platform_session_invalid', 'Platform session is expired, revoked, or inactive.');
        }
        return $admin;
    }
}
