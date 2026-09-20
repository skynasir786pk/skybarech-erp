<?php
declare(strict_types=1);

final class Config
{
    private static array $values = [];

    public static function load(string $file): void
    {
        if (!is_file($file)) {
            throw new RuntimeException('Server configuration is missing.');
        }
        $values = require $file;
        if (!is_array($values)) {
            throw new RuntimeException('Server configuration is invalid.');
        }
        self::$values = $values;
        date_default_timezone_set((string) self::get('timezone', 'UTC'));
    }

    public static function get(string $key, mixed $default = null): mixed
    {
        $envKey = strtoupper($key);
        $fromEnv = getenv($envKey);
        return $fromEnv !== false ? $fromEnv : (self::$values[$key] ?? $default);
    }
}

