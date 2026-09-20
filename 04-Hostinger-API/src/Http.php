<?php
declare(strict_types=1);

final class Http
{
    public static function requestId(): string
    {
        static $id;
        return $id ??= bin2hex(random_bytes(12));
    }

    public static function jsonBody(): array
    {
        $limit = (int) Config::get('max_json_bytes', 1048576);
        $length = (int) ($_SERVER['CONTENT_LENGTH'] ?? 0);
        if ($length > $limit) {
            self::error(413, 'payload_too_large', 'Request body is too large.');
        }
        $raw = file_get_contents('php://input', false, null, 0, $limit + 1);
        if ($raw === false || strlen($raw) > $limit) {
            self::error(413, 'payload_too_large', 'Request body is too large.');
        }
        if (trim($raw) === '') {
            return [];
        }
        try {
            $decoded = json_decode($raw, true, 128, JSON_THROW_ON_ERROR);
        } catch (JsonException) {
            self::error(400, 'invalid_json', 'Request body must be valid JSON.');
        }
        if (!is_array($decoded)) {
            self::error(400, 'invalid_json', 'JSON object is required.');
        }
        return $decoded;
    }

    public static function bearerToken(?string $fallbackHeader = null): ?string
    {
        // CGI/internal rewrites can expose the same header under different keys.
        $header = '';
        foreach ($_SERVER as $key => $value) {
            if (preg_match('/^(?:REDIRECT_)*HTTP_AUTHORIZATION$/', (string) $key) && is_string($value) && trim($value) !== '') {
                $header = trim($value);
                break;
            }
        }
        if ($header === '' && function_exists('getallheaders')) {
            foreach (getallheaders() ?: [] as $key => $value) {
                if (strcasecmp((string) $key, 'Authorization') === 0 && is_string($value)) {
                    $header = trim($value);
                    break;
                }
            }
        }
        // Platform-only alternate transport. It carries the SAME secret token,
        // checked against the SAME expiring/revocable DB session. Never a URL/cookie.
        if ($header === '' && $fallbackHeader !== null) {
            $value = trim((string) ($_SERVER[$fallbackHeader] ?? ''));
            if (preg_match('/^[A-Za-z0-9_-]{32,256}$/D', $value)) return $value;
        }
        if (!preg_match('/^Bearer\\s+([A-Za-z0-9_-]{32,256})$/iD', $header, $match)) return null;
        return $match[1];
    }

    public static function clientIp(): string
    {
        return substr((string) ($_SERVER['REMOTE_ADDR'] ?? '0.0.0.0'), 0, 45);
    }

    public static function respond(array $payload, int $status = 200): never
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        header('X-Content-Type-Options: nosniff');
        header('Cache-Control: no-store');
        $payload['request_id'] ??= self::requestId();
        echo json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        exit;
    }

    public static function error(int $status, string $code, string $message, array $details = []): never
    {
        self::respond(['success' => false, 'error' => ['code' => $code, 'message' => $message, 'details' => $details]], $status);
    }
}

