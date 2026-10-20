<?php
declare(strict_types=1);
define('PCTHUB', true);
require __DIR__ . '/lib.php';
if ($_SERVER['REQUEST_METHOD'] === 'POST') { require __DIR__ . '/actions.php'; exit; }

$user = $_SESSION['user'] ?? null;
$flash = $_SESSION['flash'] ?? null; unset($_SESSION['flash']);
$role = (string)($user['role'] ?? '');
$page = 'dashboard'; $nav = [];

if ($user && !empty($_SESSION['token'])) {
    $me = api('GET', '/me');
    if (in_array($me['status'], [401, 403], true)) { $_SESSION = []; session_destroy(); redirect(); }
    $nav = ['dashboard' => 'Dashboard', 'announcements' => 'Announcements', 'calendar' => 'Calendar', 'classcards' => 'Class cards'];
    if ($role === 'STUDENT') $nav['clearance'] = 'My clearance';
    if ($role === 'ADMIN') { $nav['clearance'] = 'Clearance'; $nav['users'] = 'Accounts'; }
    if (in_array($role, ['TEACHER', 'CASHIER', 'ADMIN'], true)) $nav['verify'] = 'Verify card';
    $titles = $nav + ['classcard' => 'Class card', 'verify' => 'Verify class card'];
    $page = (string)($_GET['page'] ?? 'dashboard');
    if (!isset($titles[$page])) $page = 'dashboard';
} else {
    $user = null;
    // someone scanned a class card QR code before signing in: send them there after login
    if (($_GET['page'] ?? '') === 'verify') $_SESSION['after_login'] = 'page=verify&code=' . preg_replace('/[^A-Za-z0-9\-]/', '', (string)($_GET['code'] ?? ''));
}
?>
<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"><title>PCTHub | <?= esc($user ? $titles[$page] : 'Sign in') ?></title>
<link rel="icon" type="image/png" href="assets/favicon.png">
<link rel="manifest" href="manifest.json">
<meta name="theme-color" content="#152746">
<link rel="apple-touch-icon" href="assets/icon-180.png">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="PCTHub">
<link rel="stylesheet" href="assets/style.css"></head><body>
<header><div class="brand"><span class="brand-logo" role="img" aria-label="Philippine College of Technology logo"></span><div class="brand-text">PCTHub <small>Integrated Student Payment & Academic Task Management</small></div></div>
<?php if ($user): ?><div class="top-actions"><div><?= esc($user['fullName'] ?? '') ?> <span class="role"><?= esc($role) ?></span></div><form method="post"><?= csrf_field('logout') ?><button class="secondary" type="submit">Sign out</button></form></div><?php endif; ?></header>
<?php if (!$user): ?>
<main class="login"><div class="login-logo" role="img" aria-label="Philippine College of Technology logo"></div><h1>Welcome to PCTHub</h1><p class="muted">Sign in to view payments, announcements, the school calendar, clearance and academic tasks.</p>
<?php if ($flash): ?><div class="alert <?= esc($flash['type']) ?>"><?= esc($flash['message']) ?></div><?php endif; ?>
<form method="post"><?= csrf_field('login') ?><label for="username">Username</label><input id="username" name="username" autocomplete="username" required autofocus><label for="password">Password</label><input id="password" type="password" name="password" autocomplete="current-password" required><p><button type="submit" style="width:100%;margin-top:12px">Sign in</button></p></form>
<div class="demo"><strong>Demo accounts</strong><br>Username: <code>student</code>, <code>teacher</code>, <code>cashier</code>, or <code>admin</code><br>Password for all: <code>PCTHub123!</code><br><br>These are development credentials. Replace or disable demo seeding before any real deployment.</div></main>
<?php else: ?>
<nav class="tabs" aria-label="Main"><?php foreach ($nav as $key => $label): ?><a href="<?= esc(($basePath ?: '') . '/?page=' . $key) ?>"<?= $key === $page ? ' class="active" aria-current="page"' : '' ?>><?= esc($label) ?></a><?php endforeach; ?></nav>
<main class="wrap">
<?php if ($flash): ?><div class="alert <?= esc($flash['type']) ?>"><?= esc($flash['message']) ?></div><?php endif; ?>
<?php if ($me['status'] >= 500): ?><div class="alert error"><?= esc($me['error']) ?></div><?php endif; ?>
<?php require __DIR__ . '/views/' . $page . '.php'; ?>
<div class="footer">PCTHub · Development prototype · Use real student data only after security review and school authorization.</div></main>
<?php endif; ?>
<script>if('serviceWorker' in navigator){window.addEventListener('load',function(){navigator.serviceWorker.register('sw.js').catch(function(){});});}</script></body></html>
