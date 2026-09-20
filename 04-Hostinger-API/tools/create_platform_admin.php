<?php
declare(strict_types=1);

require dirname(__DIR__) . '/src/Config.php';
require dirname(__DIR__) . '/src/Database.php';

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}
if ($argc === 1) {
    $fullName = 'SkyBarech Administrator';
    $email = 'admin@skybarech.com';
    $password = str_pad((string) random_int(0, 9999), 4, '0', STR_PAD_LEFT);
} elseif ($argc === 4) {
    [, $fullName, $email, $password] = $argv;
} else {
    fwrite(STDERR, "Usage:\n");
    fwrite(STDERR, "  php create_platform_admin.php\n");
    fwrite(STDERR, "  php create_platform_admin.php FULL_NAME EMAIL PASSWORD\n");
    exit(1);
}
$email = strtolower(trim($email));
if (trim($fullName) === '' || !filter_var($email, FILTER_VALIDATE_EMAIL) || preg_match('/\A[0-9]{4}\z/', $password) !== 1) {
    fwrite(STDERR, "Full name, valid email and PIN of exactly 4 digits are required.\n");
    exit(1);
}
Config::load(dirname(__DIR__) . '/config/config.php');
$db = Database::connection();
$db->beginTransaction();
try {
    $stmt = $db->prepare(
        "INSERT INTO platform_admins (full_name, email, password_hash, status)
         VALUES (?, ?, ?, 'active')
         ON DUPLICATE KEY UPDATE full_name=VALUES(full_name), password_hash=VALUES(password_hash), status='active'"
    );
    $stmt->execute([trim($fullName), $email, password_hash($password, PASSWORD_DEFAULT)]);
    $admin = $db->prepare('SELECT id FROM platform_admins WHERE email = ? LIMIT 1');
    $admin->execute([$email]);
    $adminId = (int) $admin->fetchColumn();
    $db->prepare('UPDATE platform_auth_sessions SET revoked_at = UTC_TIMESTAMP() WHERE admin_id = ? AND revoked_at IS NULL')
        ->execute([$adminId]);
    $db->commit();
} catch (Throwable $error) {
    if ($db->inTransaction()) $db->rollBack();
    fwrite(STDERR, "Super Admin could not be created/reset: {$error->getMessage()}\n");
    exit(1);
}
fwrite(STDOUT, "\nSUPER ADMIN READY\n");
fwrite(STDOUT, "Login email: {$email}\n");
fwrite(STDOUT, "PIN: {$password}\n");
fwrite(STDOUT, "Save this PIN now. Running this command again resets it.\n\n");
