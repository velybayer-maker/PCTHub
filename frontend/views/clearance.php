<?php defined('PCTHUB') or exit;
$statusLabels = ['PENDING' => 'Pending', 'CLEARED' => 'Cleared', 'HOLD' => 'On hold'];
if ($role === 'STUDENT'):
    $r = api('GET', '/clearance/me'); $d = $r['data'];
    $items = array_values(array_filter((array)val($d, 'items', []), 'is_array'));
    $overall = (string)val($d, 'overall', 'NOT_CONFIGURED');
    $banner = ['CLEARED' => 'You are fully cleared. Every requirement has been signed off.', 'IN_PROGRESS' => 'Your clearance is in progress. Settle the pending requirements below.', 'HOLD' => 'One or more requirements are on hold. Please see the remarks and visit the office concerned.', 'NOT_CONFIGURED' => 'The school has not set up clearance requirements yet.'];
?>
<div style="margin-bottom:20px"><h1>My clearance</h1><div class="muted">Your clearance status for each school office.</div></div>
<?php if ($r['error']): ?><div class="alert error"><?= esc($r['error']) ?></div><?php endif; ?>
<div class="card clearance-banner c-<?= esc(strtolower(str_replace('_', '-', $overall))) ?>"><div><?= badge($overall) ?></div>
<div class="metric"><?= (int)val($d, 'cleared', 0) ?> of <?= (int)val($d, 'total', 0) ?> cleared</div><div class="muted"><?= esc($banner[$overall] ?? '') ?></div></div>
<div class="card"><?php if (!$items): ?><div class="empty">No clearance requirements to show.</div><?php endif; ?>
<?php foreach ($items as $it): ?>
<article class="task"><div class="post-head"><h3><?= esc(val($it, 'name')) ?></h3><?= badge((string)val($it, 'status', 'PENDING')) ?></div>
<?php if (val($it, 'description')): ?><div class="meta"><?= esc(val($it, 'description')) ?></div><?php endif; ?>
<?php if (val($it, 'remarks')): ?><p><strong>Remarks:</strong> <?= esc(val($it, 'remarks')) ?></p><?php endif; ?>
<?php if (val($it, 'updated_at')): ?><div class="meta">Last updated <?= esc(fmt_dt((string)val($it, 'updated_at'))) ?></div><?php endif; ?></article>
<?php endforeach; ?></div>

<?php else: /* ADMIN */
    $r = api('GET', '/clearance'); $d = $r['data'];
    $items = array_values(array_filter((array)val($d, 'items', []), 'is_array'));
    $students = array_values(array_filter((array)val($d, 'students', []), 'is_array'));
    $q = strtolower(trim((string)($_GET['q'] ?? '')));
    if ($q !== '') $students = array_values(array_filter($students, static fn($s) => str_contains(strtolower(val($s, 'fullName') . ' ' . val($s, 'studentNumber')), $q)));
    $open = (int)($_GET['open'] ?? 0); $total = count($students); $students = array_slice($students, 0, 100);
?>
<div style="margin-bottom:20px"><h1>Student clearance</h1><div class="muted">Sign students off for each office. Only administrators can change clearance.</div></div>
<?php if ($r['error']): ?><div class="alert error"><?= esc($r['error']) ?></div><?php endif; ?>
<form method="get" class="searchbar"><input type="hidden" name="page" value="clearance"><input name="q" placeholder="Search by student name or number" value="<?= esc($_GET['q'] ?? '') ?>"><button type="submit">Search</button></form>
<div class="card"><?php if (!$students): ?><div class="empty">No students found.</div><?php endif; ?>
<?php foreach ($students as $s): $sid = (int)val($s, 'studentId', 0); $st = (array)val($s, 'statuses', []); ?>
<details class="student"<?= $sid === $open ? ' open' : '' ?>><summary><span class="who"><strong><?= esc(val($s, 'fullName')) ?></strong> <span class="muted"><?= esc(val($s, 'studentNumber')) ?></span></span><?= badge((string)val($s, 'overall', 'PENDING')) ?></summary>
<div class="student-body">
<?php foreach ($items as $it): $iid = (int)val($it, 'id', 0); $cell = (array)($st[(string)$iid] ?? []); $cur = (string)val($cell, 'status', 'PENDING'); ?>
  <form method="post" class="clr-row"><?= csrf_field('clearance_set', 'page=clearance&open=' . $sid) ?><input type="hidden" name="student_id" value="<?= $sid ?>"><input type="hidden" name="item_id" value="<?= $iid ?>">
    <div class="clr-name"><?= esc(val($it, 'name')) ?></div>
    <select name="status" aria-label="Status for <?= esc(val($it, 'name')) ?>"><?php foreach ($statusLabels as $k => $l): ?><option value="<?= $k ?>"<?= $cur === $k ? ' selected' : '' ?>><?= $l ?></option><?php endforeach; ?></select>
    <input name="remarks" maxlength="255" placeholder="Remarks (optional)" value="<?= esc(val($cell, 'remarks')) ?>"><button type="submit">Save</button></form>
<?php endforeach; ?>
  <div class="bulk"><form method="post"><?= csrf_field('clearance_all', 'page=clearance&open=' . $sid) ?><input type="hidden" name="student_id" value="<?= $sid ?>"><input type="hidden" name="status" value="CLEARED"><button type="submit">Mark all cleared</button></form>
  <form method="post"<?= confirm_attr('Reset every requirement for this student to Pending?') ?>><?= csrf_field('clearance_all', 'page=clearance&open=' . $sid) ?><input type="hidden" name="student_id" value="<?= $sid ?>"><input type="hidden" name="status" value="PENDING"><button class="secondary" type="submit">Reset to pending</button></form></div>
</div></details>
<?php endforeach; ?>
<?php if ($total > 100): ?><div class="muted" style="padding-top:10px">Showing the first 100 of <?= $total ?> students. Use search to narrow the list.</div><?php endif; ?></div>

<div class="section-title"><h2>Clearance requirements</h2><span class="tag">Offices students must clear</span></div>
<div class="card">
<?php foreach ($items as $it): $iid = (int)val($it, 'id', 0); ?>
<details class="edit"><summary><?= esc(val($it, 'name')) ?></summary>
  <form method="post"><?= csrf_field('clearance_item_save', 'page=clearance') ?><input type="hidden" name="id" value="<?= $iid ?>">
    <div class="form-grid"><div><label>Name</label><input name="name" required minlength="2" maxlength="120" value="<?= esc(val($it, 'name')) ?>"></div><div><label>Description</label><input name="description" maxlength="255" value="<?= esc(val($it, 'description')) ?>"></div></div>
    <p><button type="submit">Save</button></p></form>
  <form method="post"<?= confirm_attr('Delete this requirement? Every student\'s status for it will be removed.') ?>><?= csrf_field('clearance_item_delete', 'page=clearance') ?><input type="hidden" name="id" value="<?= $iid ?>"><button class="danger" type="submit">Delete requirement</button></form>
</details>
<?php endforeach; ?>
<h3 style="margin-bottom:0">Add a requirement</h3>
<form method="post"><?= csrf_field('clearance_item_save', 'page=clearance') ?><input type="hidden" name="id" value="0">
<div class="form-grid"><div><label>Name</label><input name="name" required minlength="2" maxlength="120" placeholder="e.g. Laboratory"></div><div><label>Description</label><input name="description" maxlength="255"></div></div>
<p><button type="submit">Add requirement</button></p></form></div>
<?php endif; ?>
