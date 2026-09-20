<?php
declare(strict_types=1);
require __DIR__ . '/../04-Hostinger-API/src/RateLimit.php';
$cases = ['0000'=>true, '0123'=>true, '9999'=>true, '123'=>false, '12345'=>false, '123456'=>false, 'password123456'=>false, '12a4'=>false, ' 1234'=>false, "1234\n"=>false, '１２３４'=>false];
foreach ($cases as $pin => $expected) {
    if (RateLimit::validShopCredential((string) $pin) !== $expected) throw new RuntimeException('PIN policy regression');
}
echo "PIN policy tests passed\n";
