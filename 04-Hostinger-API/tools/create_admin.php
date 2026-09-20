<?php
declare(strict_types=1);

require dirname(__DIR__) . '/src/Config.php';
require dirname(__DIR__) . '/src/Database.php';

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}
if ($argc !== 6) {
    fwrite(STDERR, "Usage: php create_admin.php SHOP_CODE SHOP_NAME FULL_NAME EMAIL PASSWORD\n");
    exit(1);
}
[$script, $shopCode, $shopName, $fullName, $email, $password] = $argv;
if (!filter_var($email, FILTER_VALIDATE_EMAIL) || preg_match('/\A[0-9]{4}\z/', $password) !== 1) {
    fwrite(STDERR, "A valid email and PIN of exactly 4 digits are required.\n");
    exit(1);
}
Config::load(dirname(__DIR__) . '/config/config.php');
$db = Database::connection();
$db->beginTransaction();
try {
    $shop = $db->prepare("INSERT INTO shops (name, code, status, plan) VALUES (?, ?, 'active', 'Owner')");
    $shop->execute([$shopName, strtoupper($shopCode)]);
    $shopId = (int) $db->lastInsertId();
    $branch = $db->prepare("INSERT INTO branches (shop_id, name, code) VALUES (?, 'Main Branch', 'MAIN')");
    $branch->execute([$shopId]);
    $branchId = (int) $db->lastInsertId();
    $user = $db->prepare("INSERT INTO users (shop_id, branch_id, full_name, email, mobile, password_hash, role, status) VALUES (?, ?, ?, ?, '', ?, 'super_admin', 'active')");
    $user->execute([$shopId, $branchId, $fullName, strtolower($email), password_hash($password, PASSWORD_DEFAULT)]);
    $db->commit();
    fwrite(STDOUT, "Super Admin created for shop {$shopCode}.\n");
} catch (Throwable $error) {
    if ($db->inTransaction()) {
        $db->rollBack();
    }
    fwrite(STDERR, "Create failed: {$error->getMessage()}\n");
    exit(1);
}

