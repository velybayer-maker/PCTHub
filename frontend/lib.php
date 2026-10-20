<?php
declare(strict_types=1);
defined('PCTHUB') or exit;

date_default_timezone_set('Asia/Manila');
$https = (($_SERVER['HTTPS'] ?? '') === 'on') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
session_name('PCTHUBSESSID');
session_set_cookie_params(['lifetime' => 0, 'path' => '/', 'secure' => $https, 'httponly' => true, 'samesite' => 'Lax']);
session_start();

$apiBase = rtrim(getenv('API_BASE_URL') ?: 'http://127.0.0.1:8081/api', '/');
$basePath = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
if ($basePath === '.' || $basePath === '/') $basePath = '';
if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(32));

function esc($value): string { return htmlspecialchars((string)($value ?? ''), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); }
function val($array, $key, $default = '') { return is_array($array) ? ($array[$key] ?? $default) : $default; }
function money($v): string { return '₱' . number_format((float)($v ?? 0), 2); }
function csrf_field(string $action, string $back = ''): string {
    return '<input type="hidden" name="csrf" value="' . esc($_SESSION['csrf']) . '"><input type="hidden" name="action" value="' . esc($action) . '">'
        . '<input type="hidden" name="back" value="' . esc($back) . '">';
}
function flash(string $message, string $type = 'success'): void { $_SESSION['flash'] = ['message' => $message, 'type' => $type]; }
function redirect(string $query = ''): void {
    global $basePath;
    $query = preg_replace('/[^A-Za-z0-9_=&.\-]/', '', $query) ?? '';
    header('Location: ' . $basePath . '/' . ($query !== '' ? '?' . $query : ''));
    exit;
}
function fmt_dt(?string $s, string $format = 'M j, Y g:i A'): string {
    if ($s === null || $s === '') return '';
    try { return (new DateTimeImmutable($s))->format($format); } catch (Exception $e) { return $s; }
}
function dt_local(?string $s): string { return $s ? substr(str_replace(' ', 'T', $s), 0, 16) : ''; }

/** Calls the Java API. Returns ['status'=>int, 'data'=>array, 'error'=>string]. */
function api(string $method, string $path, ?array $body = null, bool $auth = true): array {
    global $apiBase;
    $ch = curl_init($apiBase . $path);
    $headers = ['Accept: application/json', 'Content-Type: application/json'];
    if ($auth && !empty($_SESSION['token'])) $headers[] = 'Authorization: Bearer ' . $_SESSION['token'];
    curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_CUSTOMREQUEST => $method, CURLOPT_HTTPHEADER => $headers, CURLOPT_CONNECTTIMEOUT => 4, CURLOPT_TIMEOUT => 12]);
    if ($body !== null) curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($body, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));
    $raw = curl_exec($ch); $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE); $err = curl_error($ch); curl_close($ch);
    if ($raw === false) return ['status' => 503, 'data' => [], 'error' => 'The Java backend is not reachable. Start run-api.bat and confirm port 8081 is available. ' . $err];
    $data = json_decode((string)$raw, true);
    $error = '';
    if (!is_array($data)) { $data = []; $error = 'Unexpected response from backend'; }
    elseif ($code >= 400) { $error = (string)($data['error'] ?? 'Request failed'); if ($error === 'Forbidden') $error = 'You are not allowed to do that.'; }
    return ['status' => $code, 'data' => $data, 'error' => $error];
}
/** Turns an API result into a flash message. */
function flash_result(array $r, string $fallback): void {
    $ok = $r['status'] < 300 && $r['error'] === '';
    $msg = $ok ? (string)($r['data']['message'] ?? 'Done') : ($r['error'] ?: $fallback);
    if ($ok && !empty($r['data']['referenceNo'])) $msg .= ' — Receipt reference: ' . $r['data']['referenceNo'];
    flash($msg, $ok ? 'success' : 'error');
}
function badge(string $status): string {
    $labels = ['CLEARED' => 'Cleared', 'PENDING' => 'Pending', 'HOLD' => 'On hold', 'IN_PROGRESS' => 'In progress', 'NOT_CONFIGURED' => 'Not set up'];
    $class = strtolower(str_replace('_', '-', $status));
    return '<span class="badge b-' . esc($class) . '">' . esc($labels[$status] ?? $status) . '</span>';
}
function confirm_attr(string $msg): string { return ' onsubmit="return confirm(\'' . esc(addslashes($msg)) . '\')"'; }

/** Absolute URL of the verification page for a class card code (used in the QR code). */
function verify_url(string $code): string {
    global $basePath;
    $https = (($_SERVER['HTTPS'] ?? '') === 'on') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
    $host = preg_replace('/[^A-Za-z0-9.:\-]/', '', (string)($_SERVER['HTTP_HOST'] ?? 'localhost')) ?? 'localhost';
    return ($https ? 'https' : 'http') . '://' . $host . $basePath . '/?page=verify&code=' . rawurlencode($code);
}
function current_school_year(): string { $y = (int)date('Y'); return ((int)date('n') >= 6) ? $y . '-' . ($y + 1) : ($y - 1) . '-' . $y; }
function is_paid(array $c): bool { return !empty($c['paid']); }
