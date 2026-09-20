<?php
declare(strict_types=1);

final class Appearance
{
    public static function defaults(): array
    {
        return ['desktop' => ['enabled' => false, 'accent' => '#17604B', 'background' => '#F3F7F5', 'surface' => '#FFFFFF', 'sidebar' => '#112B24'],
                'android' => ['enabled' => false, 'accent' => '#17604B', 'background' => '#F3F7F5', 'surface' => '#FFFFFF']];
    }

    public static function read(PDO $db, int $shopId): array
    {
        try {
            $stmt = $db->prepare('SELECT settings FROM shop_appearance WHERE shop_id = ?');
            $stmt->execute([$shopId]);
            $settings = $stmt->fetchColumn();
            return $settings ? json_decode((string) $settings, true, 16, JSON_THROW_ON_ERROR) : self::defaults();
        } catch (PDOException $error) {
            // Older installations remain usable before the additive migration.
            if (($error->errorInfo[1] ?? 0) === 1146) return self::defaults();
            throw $error;
        }
    }

    private static function luminance(string $hex): float
    {
        $values = [];
        foreach ([1, 3, 5] as $offset) {
            $v = hexdec(substr($hex, $offset, 2)) / 255;
            $values[] = $v <= 0.04045 ? $v / 12.92 : (($v + 0.055) / 1.055) ** 2.4;
        }
        return $values[0] * 0.2126 + $values[1] * 0.7152 + $values[2] * 0.0722;
    }

    public static function save(PDO $db, int $shopId, array $body): void
    {
        $settings = self::defaults();
        foreach ($settings as $platform => $defaults) {
            $input = $body[$platform] ?? null;
            if (!is_array($input) || !is_bool($input['enabled'] ?? null)) Http::error(422, 'invalid_appearance', 'Desktop and Android appearance settings are required.');
            foreach ($defaults as $key => $default) {
                if ($key === 'enabled') { $settings[$platform][$key] = $input[$key]; continue; }
                $value = $input[$key] ?? '';
                if (!is_string($value) || !preg_match('/\A#[0-9a-fA-F]{6}\z/', $value)) Http::error(422, 'invalid_color', 'Colors must use a six-digit hex value.');
                $settings[$platform][$key] = strtoupper($value);
            }
            // Existing action icons and links use the accent on both surfaces.
            $accent = self::luminance($settings[$platform]['accent']);
            foreach (['background', 'surface'] as $surface) {
                $other = self::luminance($settings[$platform][$surface]);
                if ((max($accent, $other) + 0.05) / (min($accent, $other) + 0.05) < 4.5)
                    Http::error(422, 'color_contrast', 'Choose an accent with stronger contrast against the background and cards.');
            }
            $a = self::luminance($settings[$platform]['background']);
            $b = self::luminance($settings[$platform]['surface']);
            if (($a > 0.179) !== ($b > 0.179)) Http::error(422, 'color_contrast', 'Keep the background and cards both light or both dark.');
        }
        $settings['updated_at'] = gmdate('c');
        try {
            $stmt = $db->prepare('INSERT INTO shop_appearance (shop_id, settings) VALUES (?, ?) ON DUPLICATE KEY UPDATE settings = VALUES(settings)');
            $stmt->execute([$shopId, json_encode($settings, JSON_THROW_ON_ERROR)]);
        } catch (PDOException $error) {
            if (($error->errorInfo[1] ?? 0) === 1146) Http::error(503, 'appearance_setup_required', 'Import database/migrations/003_shop_appearance.sql once, then save again.');
            throw $error;
        }
    }
}
