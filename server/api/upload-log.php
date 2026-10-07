<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
if ($_SERVER['REQUEST_METHOD'] !== 'POST') { http_response_code(405); echo json_encode(['ok'=>false,'error'=>'POST required']); exit; }
$body = file_get_contents('php://input');
if ($body === false || $body === '' || strlen($body) > 5 * 1024 * 1024) { http_response_code(400); echo json_encode(['ok'=>false,'error'=>'empty or oversized payload']); exit; }
function clean_header(string $name, string $fallback): string {
    $key = 'HTTP_' . strtoupper(str_replace('-', '_', $name));
    $v = $_SERVER[$key] ?? $fallback;
    $v = preg_replace('/[^A-Za-z0-9._-]/', '_', (string)$v);
    return substr($v ?: $fallback, 0, 96);
}
$install = clean_header('X-Touran-Install', 'unknown-install');
$session = clean_header('X-Touran-Session', 'unknown-session');
$app = clean_header('X-Touran-App', 'unknown-app');
$queue = clean_header('X-Touran-Queue-File', 'upload');
$report = clean_header('X-Touran-Report', 'log');
$clientDay = clean_header('X-Touran-Date', '');
$now = new DateTimeImmutable('now', new DateTimeZone('Europe/Berlin'));
if (preg_match('/^\d{4}-\d{2}-\d{2}$/', $clientDay)) {
    $d = DateTimeImmutable::createFromFormat('!Y-m-d', $clientDay, new DateTimeZone('Europe/Berlin'));
    $dayPath = $d !== false ? $d->format('Y/m/d') : $now->format('Y/m/d');
} else {
    $dayPath = $now->format('Y/m/d');
}
$root = dirname(__DIR__) . '/logs';
$dir = $root . '/' . $dayPath . '/' . $install;
if (!is_dir($dir) && !mkdir($dir, 0750, true) && !is_dir($dir)) { http_response_code(500); echo json_encode(['ok'=>false,'error'=>'mkdir failed']); exit; }
$stamp = $now->format('His_u');
$file = $dir . '/' . $stamp . '_' . $report . '_' . $session . '.log';
if (file_put_contents($file, $body, LOCK_EX) === false) { http_response_code(500); echo json_encode(['ok'=>false,'error'=>'write failed']); exit; }
$index = $dir . '/index.ndjson';
$entry = ['received'=>$now->format(DATE_ATOM),'log_day'=>str_replace('/','-',$dayPath),'file'=>basename($file),'report'=>$report,'app'=>$app,'session'=>$session,'queue_file'=>$queue,'bytes'=>strlen($body),'remote'=>hash('sha256', (string)($_SERVER['REMOTE_ADDR'] ?? ''))];
file_put_contents($index, json_encode($entry, JSON_UNESCAPED_SLASHES|JSON_UNESCAPED_UNICODE)."\n", FILE_APPEND|LOCK_EX);
http_response_code(201);
echo json_encode(['ok'=>true,'stored'=>$dayPath . '/' . $install . '/' . basename($file),'bytes'=>strlen($body),'server_time'=>$now->format(DATE_ATOM)], JSON_UNESCAPED_SLASHES);
