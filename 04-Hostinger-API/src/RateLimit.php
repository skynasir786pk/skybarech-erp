<?php
declare(strict_types=1);

final class RateLimit
{
    public static function validShopCredential(string $value, int $legacyMinimum = 12): bool
    {
        return preg_match('/\A[0-9]{4}\z/', $value) === 1;
    }

    // Called while holding the user row lock. One account budget applies to all
    // email/mobile spellings and all IP addresses, including concurrent requests.
    public static function account(PDO $db, int $userId): void
    {
        $stmt = $db->prepare('SELECT COUNT(*) FROM login_attempts WHERE identity_hash = ? AND succeeded = 0 AND attempted_at > DATE_SUB(UTC_TIMESTAMP(), INTERVAL 15 MINUTE)');
        $stmt->execute([hash('sha256', 'shop-user:' . $userId)]);
        if ((int) $stmt->fetchColumn() >= 5) {
            if ($db->inTransaction()) $db->rollBack();
            Http::error(429, 'too_many_attempts', 'Too many failed PIN attempts. Try again in 15 minutes.');
        }
    }

    public static function verifyShopCredential(PDO $db, int $userId, string $value): bool
    {
        $db->beginTransaction();
        try {
            $stmt = $db->prepare('SELECT password_hash FROM users WHERE id = ? FOR UPDATE');
            $stmt->execute([$userId]);
            $hash = (string) $stmt->fetchColumn();
            self::account($db, $userId);
            $valid = password_verify($value, $hash);
            self::record($db, 'shop-user:' . $userId, $valid);
            // A valid login keeps the user row locked until its session is issued.
            // Failed attempts must commit so their budget cannot disappear.
            if (!$valid) $db->commit();
            return $valid;
        } catch (Throwable $error) {
            if ($db->inTransaction()) $db->rollBack();
            throw $error;
        }
    }

    public static function login(PDO $db, string $identity): void
    {
        $stmt = $db->prepare(
            'SELECT COUNT(*) FROM login_attempts
             WHERE identity_hash = ? AND succeeded = 0
               AND attempted_at > DATE_SUB(UTC_TIMESTAMP(), INTERVAL 15 MINUTE)'
        );
        $stmt->execute([hash('sha256', strtolower(trim($identity)))]);
        if ((int) $stmt->fetchColumn() >= 5) {
            Http::error(429, 'too_many_attempts', 'Too many failed login attempts. Try again later.');
        }
    }

    public static function record(PDO $db, string $identity, bool $succeeded): void
    {
        $stmt = $db->prepare('INSERT INTO login_attempts (ip_address, identity_hash, succeeded) VALUES (?, ?, ?)');
        $stmt->execute([Http::clientIp(), hash('sha256', strtolower(trim($identity))), $succeeded ? 1 : 0]);
    }
}

