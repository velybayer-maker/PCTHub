<?php defined('PCTHUB') or exit;
$code = strtoupper(trim((string)($_GET['code'] ?? '')));
$res = $code !== '' ? api('GET', '/class-cards/verify/' . rawurlencode($code)) : null;
$d = $res['data'] ?? [];
?>
<div style="margin-bottom:20px"><h1>Verify a class card</h1><div class="muted">Enter the verification code printed on a class card, or scan its QR code, to confirm it is genuine and paid.</div></div>
<form method="get" class="searchbar"><input type="hidden" name="page" value="verify"><input name="code" placeholder="e.g. CC-7F3K-92QA" value="<?= esc($code) ?>" required maxlength="20" autocapitalize="characters" autocomplete="off"><button type="submit">Verify</button></form>
<?php if ($res !== null): ?>
<?php if ($res['error'] !== ''): ?><div class="card verify-result bad"><h2 style="margin-top:0">Not valid</h2><div class="alert error"><?= esc($res['error']) ?></div><div class="muted">No genuine PCTHub class card has this code. Do not accept it as an exam permit.</div></div>
<?php else: $ok = !empty($d['paid']); ?>
<div class="card verify-result <?= $ok ? 'good' : 'bad' ?>"><h2 style="margin-top:0"><?= $ok ? '✓ Genuine and paid' : 'Genuine card, but NOT paid' ?></h2>
<table class="cc-table"><tbody>
<tr><th>Student</th><td><strong><?= esc(val($d, 'studentName')) ?></strong> <span class="muted"><?= esc(val($d, 'studentNumber')) ?></span></td></tr>
<tr><th>Subject</th><td><?= esc(val($d, 'subjectCode')) ?> — <?= esc(val($d, 'subjectDescription')) ?></td></tr>
<tr><th>Term</th><td>SY <?= esc(val($d, 'schoolYear')) ?> · <?= esc(val($d, 'semester')) ?></td></tr>
<tr><th>Teacher</th><td><?= esc(val($d, 'teacherName')) ?></td></tr>
<tr><th>Payment</th><td><?= $ok ? 'Paid ' . esc(fmt_dt((string)val($d, 'paidAt'))) : 'No payment recorded. Not valid as an exam permit.' ?></td></tr>
<tr><th>Grade</th><td><?= !empty($d['graded']) ? 'Final grade submitted' : 'Not yet submitted' ?></td></tr></tbody></table></div>
<?php endif; endif; ?>
