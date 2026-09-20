<?php
declare(strict_types=1);

return [
    'app_env' => 'production',
    'app_debug' => false,
    'timezone' => 'UTC',
    'db_host' => 'localhost',
    'db_port' => 3306,
    'db_name' => 'CHANGE_ME',
    'db_user' => 'CHANGE_ME',
    'db_password' => 'CHANGE_ME',
    'allowed_origins' => [
        'https://admin.example.com',
    ],
    'token_ttl_seconds' => 43200,
    'refresh_ttl_seconds' => 2592000,
    'max_json_bytes' => 1048576,
];

