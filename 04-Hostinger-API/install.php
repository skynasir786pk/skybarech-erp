<?php
declare(strict_types=1);

header('Content-Type: text/html; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");

$configPath = __DIR__ . '/config/config.php';
$schemaPath = __DIR__ . '/database/schema.sql';
$isHttps = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
    || strtolower((string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')) === 'https';
$host = preg_replace('/[^A-Za-z0-9.:-]/', '', (string) ($_SERVER['HTTP_HOST'] ?? ''));
$origin = ($isHttps ? 'https://' : 'http://') . $host;

function page(string $body): never
{
    echo '<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">';
    echo '<title>SkyBarech Hostinger Setup</title><style>';
    echo ':root{font-family:Inter,system-ui,sans-serif;color:#10204a;background:#f2f6ff}*{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;padding:20px;background:radial-gradient(circle at 10% 10%,#dce9ff,transparent 35%),#f6f9ff}.card{width:min(680px,100%);background:#fff;border:1px solid #dce6f8;border-radius:26px;box-shadow:0 28px 80px rgba(20,53,130,.15);overflow:hidden}.head{padding:28px 30px;color:#fff;background:linear-gradient(135deg,#071b4f,#1457df)}.brand{display:flex;align-items:center;gap:12px}.logo{display:grid;place-items:center;width:46px;height:46px;border-radius:14px;background:#ffffff1f;font-weight:900}.head h1{margin:22px 0 7px;font-size:28px}.head p{margin:0;color:#dce8ff;font-size:13px;line-height:1.6}.body{padding:28px 30px}.grid{display:grid;grid-template-columns:1fr 120px;gap:13px}.field{display:grid;gap:7px;margin-bottom:14px}.field label{font-size:12px;font-weight:800}.field input{width:100%;height:47px;padding:0 13px;border:1px solid #d5dfef;border-radius:12px;background:#fbfdff;font:inherit;font-size:14px}.field input:focus{outline:3px solid #cdddff;border-color:#3271ef}.button{width:100%;height:51px;border:0;border-radius:13px;color:#fff;background:linear-gradient(135deg,#1d63ef,#0a3bb8);font-weight:850;font-size:14px;cursor:pointer}.note,.error,.success{padding:13px 14px;border-radius:12px;font-size:12px;line-height:1.6;margin:0 0 18px}.note{background:#eef5ff;color:#38517f}.error{background:#ffedf0;color:#a32238}.success{background:#e8faef;color:#126b40}.result{padding:14px;border:1px dashed #89a9ee;border-radius:13px;background:#f8fbff}.result code{display:block;margin-top:7px;padding:10px;border-radius:9px;background:#0c1e4c;color:#fff;overflow-wrap:anywhere}.steps{margin:15px 0 0;padding-left:20px;color:#52617e;font-size:12px;line-height:1.75}a{color:#155bd7;font-weight:800}@media(max-width:560px){.head,.body{padding:23px 19px}.grid{grid-template-columns:1fr}.card{border-radius:20px}}';
    echo '</style></head><body><main class="card"><header class="head"><div class="brand"><div class="logo">SB</div><strong>SkyBarech Technology</strong></div><h1>Hostinger Easy Setup</h1><p>Secure one-time API, database and Super Admin configuration.</p></header><section class="body">';
    echo $body;
    echo '</section></main></body></html>';
    exit;
}

function e(string $value): string
{
    return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

if (is_file($configPath)) {
    page('<p class="success"><strong>Setup already completed.</strong><br>The installer is locked because config/config.php exists.</p>'
        . '<ol class="steps"><li>Check <a href="./health">API health</a>.</li><li>Open the Super Admin page at /admin/.</li><li>To reconfigure, create a new empty database and remove config/config.php manually only after taking a backup.</li></ol>');
}

if (PHP_VERSION_ID < 80100 || !extension_loaded('pdo_mysql')) {
    page('<p class="error"><strong>Server requirement missing.</strong><br>PHP 8.1+ with PDO MySQL is required. Select PHP 8.2 or newer in Hostinger hPanel.</p>');
}
if (!$isHttps) {
    page('<p class="error"><strong>HTTPS required.</strong><br>Enable SSL in Hostinger and reopen this installer using https://.</p>');
}

ini_set('session.use_strict_mode', '1');
session_set_cookie_params(['secure' => true, 'httponly' => true, 'samesite' => 'Strict']);
session_start();
$_SESSION['setup_csrf'] ??= bin2hex(random_bytes(24));
$error = '';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $csrf = (string) ($_POST['csrf'] ?? '');
    $dbHost = trim((string) ($_POST['db_host'] ?? 'localhost'));
    $dbPort = (int) ($_POST['db_port'] ?? 3306);
    $dbName = trim((string) ($_POST['db_name'] ?? ''));
    $dbUser = trim((string) ($_POST['db_user'] ?? ''));
    $dbPassword = (string) ($_POST['db_password'] ?? '');
    $adminName = trim((string) ($_POST['admin_name'] ?? 'SkyBarech Administrator'));
    $adminEmail = strtolower(trim((string) ($_POST['admin_email'] ?? 'admin@skybarech.com')));
    $allowedOrigin = rtrim(trim((string) ($_POST['allowed_origin'] ?? $origin)), '/');

    try {
        if (!hash_equals((string) $_SESSION['setup_csrf'], $csrf)) throw new RuntimeException('Setup form expired. Refresh and try again.');
        if (!preg_match('/^[A-Za-z0-9.-]+$/', $dbHost) || $dbPort < 1 || $dbPort > 65535) throw new RuntimeException('Database host or port is invalid.');
        if (!preg_match('/^[A-Za-z0-9_-]{1,64}$/', $dbName) || !preg_match('/^[A-Za-z0-9_-]{1,64}$/', $dbUser) || $dbPassword === '') throw new RuntimeException('Complete database name, username and password are required.');
        if ($adminName === '' || !filter_var($adminEmail, FILTER_VALIDATE_EMAIL)) throw new RuntimeException('Administrator name or email is invalid.');
        $originParts = parse_url($allowedOrigin);
        if (($originParts['scheme'] ?? '') !== 'https' || empty($originParts['host']) || !empty($originParts['query']) || !empty($originParts['fragment']) || !in_array($originParts['path'] ?? '', ['', '/'], true)) {
            throw new RuntimeException('Website origin must be like https://yourdomain.com without /admin or /api.');
        }
        if (!is_file($schemaPath)) throw new RuntimeException('database/schema.sql is missing from the upload.');

        $pdo = new PDO("mysql:host={$dbHost};port={$dbPort};dbname={$dbName};charset=utf8mb4", $dbUser, $dbPassword, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
            PDO::MYSQL_ATTR_INIT_COMMAND => "SET time_zone = '+00:00'",
        ]);
        $tables = $pdo->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);
        $required = ['shops','platform_admins','platform_auth_sessions','branches','users','role_permissions','auth_sessions','login_attempts','activations','sync_records','sync_changes','sync_operations','registered_devices','sync_conflicts'];
        if (!$tables) {
            $sql = (string) file_get_contents($schemaPath);
            $statements = preg_split('/;\s*(?:\r?\n|$)/', $sql) ?: [];
            foreach ($statements as $statement) {
                if (trim($statement) !== '') $pdo->exec($statement);
            }
            $tables = $pdo->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);
        }
        $missing = array_values(array_diff($required, $tables));
        if ($missing) throw new RuntimeException('Database is partial/old. Use a new empty database. Missing tables: ' . implode(', ', $missing));

        $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%';
        $adminPassword = '';
        $adminPassword = str_pad((string) random_int(0, 9999), 4, '0', STR_PAD_LEFT);
        $pdo->beginTransaction();
        $admin = $pdo->prepare("INSERT INTO platform_admins (full_name,email,password_hash,status) VALUES (?,?,?,'active') ON DUPLICATE KEY UPDATE full_name=VALUES(full_name),password_hash=VALUES(password_hash),status='active'");
        $admin->execute([$adminName, $adminEmail, password_hash($adminPassword, PASSWORD_DEFAULT)]);
        $adminIdQuery = $pdo->prepare('SELECT id FROM platform_admins WHERE email = ? LIMIT 1');
        $adminIdQuery->execute([$adminEmail]);
        $adminId = (int) $adminIdQuery->fetchColumn();
        $pdo->prepare('UPDATE platform_auth_sessions SET revoked_at=UTC_TIMESTAMP() WHERE admin_id=? AND revoked_at IS NULL')->execute([$adminId]);
        $pdo->commit();

        $values = [
            'app_env' => 'production', 'app_debug' => false, 'timezone' => 'UTC',
            'db_host' => $dbHost, 'db_port' => $dbPort, 'db_name' => $dbName,
            'db_user' => $dbUser, 'db_password' => $dbPassword,
            'allowed_origins' => [$allowedOrigin],
            'token_ttl_seconds' => 43200, 'refresh_ttl_seconds' => 2592000,
            'max_json_bytes' => 1048576,
        ];
        $configContents = "<?php\ndeclare(strict_types=1);\n\nreturn " . var_export($values, true) . ";\n";
        $temporary = tempnam(dirname($configPath), 'skybarech-config-');
        if ($temporary === false || file_put_contents($temporary, $configContents, LOCK_EX) === false) throw new RuntimeException('Hostinger could not write config/config.php. Check config folder permissions.');
        @chmod($temporary, 0600);
        if (!rename($temporary, $configPath)) {
            @unlink($temporary);
            throw new RuntimeException('Hostinger could not finalize config/config.php. Check config folder permissions.');
        }
        session_destroy();
        page('<p class="success"><strong>Setup completed successfully.</strong><br>Database, API configuration and Super Admin are ready. The installer is now locked.</p>'
            . '<div class="result"><strong>Save these credentials now</strong><code>Email: ' . e($adminEmail) . '</code><code>Password: ' . e($adminPassword) . '</code></div>'
            . '<ol class="steps"><li>Open <a href="./health">API health</a> and confirm database=connected.</li><li>Open /admin/ and sign in.</li><li>Never share or place this password inside source files.</li></ol>');
    } catch (Throwable $exception) {
        if (isset($pdo) && $pdo->inTransaction()) $pdo->rollBack();
        $error = $exception->getMessage();
        if (($dbPassword ?? '') !== '') {
            $error = str_replace($dbPassword, '[hidden]', $error);
        }
    }
}

$body = $error !== '' ? '<p class="error"><strong>Setup was not completed.</strong><br>' . e($error) . '</p>' : '<p class="note">Use a <strong>new empty MySQL database</strong>. The installer will import the latest schema. Do not enter your hPanel account password—enter the MySQL database password.</p>';
$body .= '<form method="post" autocomplete="off"><input type="hidden" name="csrf" value="' . e((string) $_SESSION['setup_csrf']) . '">'
    . '<div class="grid"><div class="field"><label>Database host</label><input name="db_host" value="' . e((string) ($_POST['db_host'] ?? 'localhost')) . '" required></div><div class="field"><label>Port</label><input name="db_port" inputmode="numeric" value="' . e((string) ($_POST['db_port'] ?? '3306')) . '" required></div></div>'
    . '<div class="field"><label>Full database name</label><input name="db_name" placeholder="u123456789_skybarech" value="' . e((string) ($_POST['db_name'] ?? '')) . '" required></div>'
    . '<div class="field"><label>Full database username</label><input name="db_user" placeholder="u123456789_admin" value="' . e((string) ($_POST['db_user'] ?? '')) . '" required></div>'
    . '<div class="field"><label>MySQL database password</label><input name="db_password" type="password" required></div>'
    . '<div class="field"><label>Website origin</label><input name="allowed_origin" value="' . e((string) ($_POST['allowed_origin'] ?? $origin)) . '" required></div>'
    . '<div class="field"><label>Super Admin name</label><input name="admin_name" value="' . e((string) ($_POST['admin_name'] ?? 'SkyBarech Administrator')) . '" required></div>'
    . '<div class="field"><label>Super Admin email</label><input name="admin_email" type="email" value="' . e((string) ($_POST['admin_email'] ?? 'admin@skybarech.com')) . '" required></div>'
    . '<button class="button" type="submit">Configure SkyBarech securely</button></form>';
page($body);
