<?php defined('PCTHUB') or exit;
$roles = ['STUDENT' => 'Students', 'TEACHER' => 'Teachers', 'CASHIER' => 'Cashiers', 'ADMIN' => 'Administrators'];
$filter = strtoupper((string)($_GET['r'] ?? 'ALL')); if (!isset($roles[$filter])) $filter = 'ALL';
$q = strtolower(trim((string)($_GET['q'] ?? ''))); $open = (int)($_GET['open'] ?? 0);
$r = api('GET', '/admin/users');
$all = array_values(array_filter($r['data'], 'is_array'));
$counts = array_fill_keys(array_keys($roles), 0); foreach ($all as $u) { $k = (string)val($u, 'role'); if (isset($counts[$k])) $counts[$k]++; }
$list = array_values(array_filter($all, static function ($u) use ($filter, $q) {
    if ($filter !== 'ALL' && val($u, 'role') !== $filter) return false;
    return $q === '' || str_contains(strtolower(val($u, 'full_name') . ' ' . val($u, 'username') . ' ' . val($u, 'student_number')), $q);
}));
$meId = (int)($user["id"] ?? 0); $base = ($basePath ?: '') . '/?page=users&r=';
?>
<div style="margin-bottom:20px"><h1>Accounts</h1><div class="muted">Register new students and teachers, and manage existing accounts. Only administrators can do this.</div></div>
<?php if ($r['error']): ?><div class="alert error"><?= esc($r['error']) ?></div><?php endif; ?>

<div class="card"><h2 style="margin-top:0">Register a new account</h2>
<form method="post" autocomplete="off"><?= csrf_field('user_register', 'page=users') ?>
<div class="form-grid"><div><label>Account type</label><select name="role" id="reg-role"><option value="STUDENT">Student</option><option value="TEACHER">Teacher</option><option value="CASHIER">Cashier</option></select></div>
<div><label>Full name</label><input name="full_name" required minlength="2" maxlength="160"></div>
<div><label>Username</label><input name="username" required minlength="3" maxlength="40" pattern="[A-Za-z0-9._\-]{3,40}" autocapitalize="none" title="Letters, numbers, dots, dashes and underscores"></div>
<div><label>Temporary password</label><input type="password" name="password" required minlength="8" maxlength="72" autocomplete="new-password"></div>
<div class="student-only"><label>Student number</label><input name="student_number" maxlength="40" placeholder="e.g. PCT-2026-014"></div>
<div class="student-only"><label>Total school fees (PHP)</label><input name="total_fees" type="number" min="0" step="0.01" placeholder="0.00"></div>
<div class="student-only"><label>Course</label><input name="course" maxlength="160" placeholder="e.g. Bachelor of Science in Information Technology"></div>
<div class="student-only"><label>Year level</label><input name="year_level" maxlength="20" placeholder="e.g. 1st Year"></div>
<div><label>PCT email (.edu.ph)</label><input type="email" name="pct_email" maxlength="160" placeholder="name@pctdavao.edu.ph"></div>
<div><label>Contact number</label><input name="contact_no" maxlength="40" inputmode="tel"></div>
<div class="student-only"><label>Personal email</label><input type="email" name="personal_email" maxlength="160"></div></div>
<div class="muted" style="font-size:12px;margin-top:8px">Passwords need at least 8 characters with a letter and a number. Tell the person to keep it private.</div>
<p><button type="submit">Create account</button></p></form></div>

<div class="section-title"><h2>All accounts</h2><span class="tag"><?= count($all) ?> total</span></div>
<div class="filters"><a class="pill<?= $filter === 'ALL' ? ' on' : '' ?>" href="<?= esc($base . 'ALL') ?>">All (<?= count($all) ?>)</a>
<?php foreach ($roles as $k => $l): ?><a class="pill<?= $filter === $k ? ' on' : '' ?>" href="<?= esc($base . $k) ?>"><?= esc($l) ?> (<?= $counts[$k] ?>)</a><?php endforeach; ?></div>
<form method="get" class="searchbar"><input type="hidden" name="page" value="users"><input type="hidden" name="r" value="<?= esc($filter) ?>"><input name="q" placeholder="Search by name, username or student number" value="<?= esc($_GET['q'] ?? '') ?>"><button type="submit">Search</button></form>
<div class="card"><?php if (!$list): ?><div class="empty">No accounts match.</div><?php endif; ?>
<?php foreach (array_slice($list, 0, 150) as $u): $uid = (int)val($u, 'id', 0); $isStudent = val($u, 'role') === 'STUDENT'; $back = 'page=users&r=' . $filter . '&open=' . $uid; ?>
<details class="student"<?= $uid === $open ? ' open' : '' ?>><summary><span class="who"><strong><?= esc(val($u, 'full_name')) ?></strong> <span class="muted">@<?= esc(val($u, 'username')) ?><?= $isStudent ? ' · ' . esc(val($u, 'student_number')) : '' ?></span></span>
<span><span class="tag"><?= esc($roles[val($u, 'role')] ?? val($u, 'role')) ?></span> <?= val($u, 'active') ? '<span class="badge b-cleared">Active</span>' : '<span class="badge b-hold">Inactive</span>' ?></span></summary>
<div class="student-body">
<form method="post" autocomplete="off"><?= csrf_field('user_update', $back) ?><input type="hidden" name="id" value="<?= $uid ?>">
<div class="form-grid"><div><label>Full name</label><input name="full_name" required minlength="2" maxlength="160" value="<?= esc(val($u, 'full_name')) ?>"></div>
<div><label>Username</label><input value="<?= esc(val($u, 'username')) ?>" disabled></div>
<?php if ($isStudent): ?><div><label>Student number</label><input name="student_number" required maxlength="40" value="<?= esc(val($u, 'student_number')) ?>"></div>
<div><label>Total school fees (PHP)</label><input name="total_fees" type="number" min="0" step="0.01" value="<?= esc(number_format((float)val($u, 'total_fees', 0), 2, '.', '')) ?>"></div><?php endif; ?>
<?php if ($isStudent): ?><div><label>Course</label><input name="course" maxlength="160" value="<?= esc(val($u, 'course')) ?>"></div><div><label>Year level</label><input name="year_level" maxlength="20" value="<?= esc(val($u, 'year_level')) ?>"></div><div><label>Personal email</label><input type="email" name="personal_email" maxlength="160" value="<?= esc(val($u, 'personal_email')) ?>"></div><?php endif; ?>
<div><label>PCT email (.edu.ph)</label><input type="email" name="pct_email" maxlength="160" value="<?= esc(val($u, 'pct_email')) ?>"></div><div><label>Contact number</label><input name="contact_no" maxlength="40" inputmode="tel" value="<?= esc(val($u, 'contact_no')) ?>"></div>
<div><label>Reset password (optional)</label><input type="password" name="new_password" minlength="8" maxlength="72" autocomplete="new-password" placeholder="Leave blank to keep the current one"></div></div>
<label class="check"><input type="checkbox" name="active" value="1"<?= val($u, 'active') ? ' checked' : '' ?><?= $uid === $meId ? ' disabled' : '' ?>> Account is active (inactive accounts cannot sign in)</label>
<?php if ($uid === $meId): ?><input type="hidden" name="active" value="1"><div class="muted" style="font-size:12px">This is your own account, so it cannot be deactivated or deleted.</div><?php endif; ?>
<p><button type="submit">Save changes</button></p></form>
<?php if ($uid !== $meId): ?><form method="post"<?= confirm_attr('Delete this account permanently? If it has payment records it cannot be deleted; deactivate it instead.') ?>><?= csrf_field('user_delete', 'page=users&r=' . $filter) ?><input type="hidden" name="id" value="<?= $uid ?>"><button class="danger" type="submit">Delete account</button></form><?php endif; ?>
</div></details>
<?php endforeach; ?></div>
<script>(function(){var r=document.getElementById('reg-role');if(!r)return;function t(){var s=r.value==='STUDENT';document.querySelectorAll('.student-only').forEach(function(e){e.style.display=s?'':'none';});}r.addEventListener('change',t);t();})();</script>
