<?php defined('PCTHUB') or exit;
$r = api('GET', '/class-cards');
$cards = array_values(array_filter($r['data'], 'is_array'));
$semesters = ['1st Semester', '2nd Semester', 'Summer']; $modes = ['F2F', 'Online', 'Blended'];
$methods = ['CASH' => 'Cash', 'BANK_TRANSFER' => 'Bank transfer', 'GCASH' => 'GCash', 'OTHER' => 'Other'];

function cc_title(array $c): string { return trim(val($c, 'subject_code') . ' — ' . val($c, 'subject_description')); }
function cc_term(array $c): string { return 'SY ' . val($c, 'school_year') . ' · ' . val($c, 'semester'); }
function cc_steps(array $c): string {
    $paid = is_paid($c); $graded = val($c, 'final_grade', '') !== '' && val($c, 'final_grade', null) !== null;
    $s = static fn(bool $on, string $label): string => '<span class="step' . ($on ? ' done' : '') . '">' . ($on ? '✓ ' : '') . esc($label) . '</span>';
    return '<div class="steps">' . $s(true, 'Enrolled') . $s($paid, $paid ? 'Paid' : 'Payment pending') . $s($graded, $graded ? 'Graded' : 'Grade pending') . '</div>';
}
function cc_pay_form(array $c, array $methods, string $back): string {
    $id = (int)val($c, 'id', 0); $opts = '';
    foreach ($methods as $k => $l) $opts .= '<option value="' . esc($k) . '">' . esc($l) . '</option>';
    return '<form method="post" class="payrow">' . csrf_field('class_card_pay', $back) . '<input type="hidden" name="id" value="' . $id . '">'
        . '<select name="payment_method" aria-label="Payment method">' . $opts . '</select>'
        . '<input name="official_receipt_no" maxlength="30" placeholder="Official receipt no. (from the booklet)" aria-label="Official receipt number">'
        . '<button type="submit">Record ' . esc(money(val($c, 'fee', 0))) . ' payment</button></form>';
}
function cc_filter(array $cards, string $q, string $show): array {
    return array_values(array_filter($cards, static function ($c) use ($q, $show) {
        if ($show === 'unpaid' && is_paid($c)) return false;
        if ($show === 'paid' && !is_paid($c)) return false;
        return $q === '' || str_contains(strtolower(val($c, 'student_name') . ' ' . val($c, 'student_number') . ' ' . val($c, 'subject_code') . ' ' . val($c, 'subject_description')), $q);
    }));
}
$q = strtolower(trim((string)($_GET['q'] ?? '')));
$show = (string)($_GET['show'] ?? ($role === 'CASHIER' ? 'unpaid' : 'all')); if (!in_array($show, ['all', 'unpaid', 'paid'], true)) $show = 'all';
$base = ($basePath ?: '') . '/?page=classcards';
?>
<div style="margin-bottom:20px"><h1>Class cards</h1><div class="muted">
<?php if ($role === 'STUDENT'): ?>Each class card is paid for separately. Once the cashier records your payment, it unlocks here and you can print it.
<?php elseif ($role === 'TEACHER'): ?>Submit the final grade for your subjects. A grade you submit is locked; only an administrator can change it.
<?php elseif ($role === 'CASHIER'): ?>Record the payment for one specific class card. The printable card is released only after that.
<?php else: ?>Create class cards, record payments and manage grades.<?php endif; ?></div></div>
<?php if ($r['error']): ?><div class="alert error"><?= esc($r['error']) ?></div><?php endif; ?>

<?php if ($role === 'STUDENT'):
    $groups = []; foreach ($cards as $c) { $groups[cc_term($c)][] = $c; } ?>
<?php if (!$cards): ?><div class="card"><div class="empty">No class cards have been created for you yet. The registrar's office adds them when you are enrolled in a subject.</div></div><?php endif; ?>
<?php foreach ($groups as $term => $list): ?>
<div class="section-title"><h2><?= esc($term) ?></h2></div>
<?php foreach ($list as $c): $paid = is_paid($c); ?>
<article class="card cc-item<?= $paid ? ' is-paid' : '' ?>"><div class="post-head"><h3><?= esc(cc_title($c)) ?></h3><span class="tag"><?= esc(val($c, 'units')) ?> units</span></div>
<div class="meta">Teacher: <?= esc(val($c, 'teacher_name')) ?> · <?= esc(val($c, 'schedule', 'Schedule to be announced')) ?> · <?= esc(fmt_dt((string)val($c, 'start_date'), 'M j')) ?> – <?= esc(fmt_dt((string)val($c, 'end_date'), 'M j, Y')) ?></div>
<?= cc_steps($c) ?>
<?php if ($paid): ?><p>Paid <?= esc(fmt_dt((string)val($c, 'paid_at'), 'M j, Y')) ?> · Receipt <strong><?= esc(val($c, 'or_number') ?: val($c, 'payment_reference')) ?></strong><?= val($c, 'final_grade') ? ' · Final grade <strong>' . esc(val($c, 'final_grade')) . '</strong>' : '' ?></p>
<a class="button" href="<?= esc(($basePath ?: '') . '/?page=classcard&id=' . (int)val($c, 'id', 0)) ?>">View / print class card</a>
<?php else: ?><div class="notice">Pay <strong><?= esc(money(val($c, 'fee', 0))) ?></strong> for this class card at the PCT Cashier. Mention <strong><?= esc(val($c, 'subject_code')) ?></strong> so the cashier records it against this card. It unlocks here as soon as it is recorded.</div><?php endif; ?>
</article><?php endforeach; endforeach; ?>

<?php elseif ($role === 'TEACHER'):
    $groups = []; foreach ($cards as $c) { $groups[cc_term($c) . ' · ' . cc_title($c)][] = $c; } ?>
<?php if (!$cards): ?><div class="card"><div class="empty">No class cards are assigned to you yet.</div></div><?php endif; ?>
<?php foreach ($groups as $heading => $list): $first = $list[0]; $due = (new DateTimeImmutable((string)val($first, 'end_date')))->modify('+7 days'); $overdue = $due < new DateTimeImmutable('today'); ?>
<div class="section-title"><h2><?= esc($heading) ?></h2><span class="tag">Grades due <?= esc($due->format('M j, Y')) ?></span></div>
<div class="card"><?php foreach ($list as $c): $cid = (int)val($c, 'id', 0); $graded = val($c, 'final_grade') !== '' && val($c, 'final_grade', null) !== null; ?>
<div class="gr-row"><div class="gr-who"><strong><?= esc(val($c, 'student_name')) ?></strong> <span class="muted"><?= esc(val($c, 'student_number')) ?></span><div><?= is_paid($c) ? '<span class="badge b-cleared">Class card paid</span>' : '<span class="badge b-pending">Unpaid</span>' ?></div></div>
<?php if ($graded): ?><div class="gr-done"><span class="badge b-cleared">Grade submitted</span> <strong><?= esc(val($c, 'final_grade')) ?></strong><?= val($c, 'remarks') ? ' · ' . esc(val($c, 'remarks')) : '' ?><div class="meta">Locked<?= val($c, 'finalized_at') ? ' · ' . esc(fmt_dt((string)val($c, 'finalized_at'))) : '' ?></div></div>
<?php else: ?><form method="post" class="gr-form"><?= csrf_field('class_card_grade', 'page=classcards') ?><input type="hidden" name="id" value="<?= $cid ?>"><input name="final_grade" required maxlength="10" placeholder="Final grade" aria-label="Final grade for <?= esc(val($c, 'student_name')) ?>"><input name="remarks" maxlength="255" placeholder="Remarks (optional)"><button type="submit" onclick="return confirm('Submit this grade? It will be locked and only an administrator can change it.')">Submit grade</button><?= $overdue ? '<span class="badge b-hold">Overdue</span>' : '' ?></form><?php endif; ?></div>
<?php endforeach; ?></div><?php endforeach; ?>

<?php else: /* CASHIER and ADMIN */
    $isAdmin = $role === 'ADMIN'; $back = 'page=classcards&show=' . $show;
    $list = cc_filter($cards, $q, $show);
    $unpaid = count(array_filter($cards, static fn($c) => !is_paid($c)));
    if ($isAdmin) {
        $students = array_values(array_filter(api('GET', '/students')['data'], 'is_array'));
        $teachers = array_values(array_filter(api('GET', '/admin/users?role=TEACHER')['data'], static fn($u) => is_array($u) && !empty($u['active'])));
    }
?>
<?php if ($isAdmin): ?>
<div class="card"><h2 style="margin-top:0">Create class cards</h2>
<form method="post"><?= csrf_field('class_card_create', 'page=classcards') ?>
<div class="form-grid"><div><label>School year</label><input name="school_year" required pattern="\d{4}-\d{4}" value="<?= esc(current_school_year()) ?>"></div>
<div><label>Semester</label><select name="semester"><?php foreach ($semesters as $s): ?><option><?= esc($s) ?></option><?php endforeach; ?></select></div>
<div><label>Subject code</label><input name="subject_code" required maxlength="30" placeholder="e.g. MS102"></div><div><label>Subject description</label><input name="subject_description" required maxlength="160" placeholder="e.g. Quantitative Methods"></div>
<div><label>Teacher</label><select name="teacher_id" required><option value="">Choose a teacher…</option><?php foreach ($teachers as $t): ?><option value="<?= (int)val($t, 'id', 0) ?>"><?= esc(val($t, 'full_name')) ?></option><?php endforeach; ?></select></div>
<div><label>No. of units</label><input name="units" type="number" step="0.5" min="0.5" max="12" value="3" required></div>
<div><label>Mode of delivery</label><select name="mode_of_delivery"><?php foreach ($modes as $m): ?><option><?= esc($m) ?></option><?php endforeach; ?></select></div>
<div><label>Class card fee (PHP)</label><input name="fee" type="number" step="0.01" min="0" required placeholder="0.00"></div>
<div><label>Start date</label><input type="date" name="start_date" required></div><div><label>End date</label><input type="date" name="end_date" required></div></div>
<label>Schedule (day / time / room / type)</label><input name="schedule" maxlength="200" placeholder="e.g. M T W Th F / 01:00 PM - 03:00 PM / 202 / LEC">
<label>Students to enroll in this subject</label>
<div class="pick"><label class="check"><input type="checkbox" onclick="document.querySelectorAll('.pick-s').forEach(function(b){b.checked=this.checked}.bind(this))"> Select all</label>
<?php foreach ($students as $s): ?><label class="check"><input class="pick-s" type="checkbox" name="student_ids[]" value="<?= (int)val($s, 'id', 0) ?>"> <?= esc(val($s, 'full_name')) ?> <span class="muted"><?= esc(val($s, 'student_number')) ?></span></label><?php endforeach; ?>
<?php if (!$students): ?><div class="muted">Register students first (Accounts page).</div><?php endif; ?></div>
<p><button type="submit">Create class cards</button></p></form></div>
<?php endif; ?>

<div class="section-title"><h2><?= $isAdmin ? 'All class cards' : 'Record class card payments' ?></h2><span class="tag"><?= $unpaid ?> awaiting payment</span></div>
<div class="filters"><?php foreach (['unpaid' => 'Awaiting payment', 'paid' => 'Paid', 'all' => 'All'] as $k => $l): ?><a class="pill<?= $show === $k ? ' on' : '' ?>" href="<?= esc($base . '&show=' . $k) ?>"><?= esc($l) ?></a><?php endforeach; ?></div>
<form method="get" class="searchbar"><input type="hidden" name="page" value="classcards"><input type="hidden" name="show" value="<?= esc($show) ?>"><input name="q" placeholder="Search student, student number or subject" value="<?= esc($_GET['q'] ?? '') ?>"><button type="submit">Search</button></form>
<div class="card"><?php if (!$list): ?><div class="empty">No class cards match.</div><?php endif; ?>
<?php foreach (array_slice($list, 0, 150) as $c): $cid = (int)val($c, 'id', 0); $paid = is_paid($c); ?>
<div class="cc-row"><div class="cc-main"><strong><?= esc(val($c, 'student_name')) ?></strong> <span class="muted"><?= esc(val($c, 'student_number')) ?></span>
<div><?= esc(cc_title($c)) ?></div><div class="meta"><?= esc(cc_term($c)) ?> · <?= esc(val($c, 'teacher_name')) ?> · Fee <?= esc(money(val($c, 'fee', 0))) ?></div></div>
<div class="cc-side"><?php if ($paid): ?><span class="badge b-cleared">Paid</span><div class="meta">Receipt <?= esc(val($c, 'or_number') ?: val($c, 'payment_reference')) ?> · <?= esc(fmt_dt((string)val($c, 'paid_at'), 'M j, Y')) ?></div><?php else: ?><span class="badge b-pending">Awaiting payment</span><?php endif; ?></div>
<?php if (!$paid): ?><div class="cc-pay"><?= cc_pay_form($c, $methods, $back) ?></div><?php endif; ?>
<?php if ($isAdmin): ?><details class="edit cc-edit"><summary>Edit or delete</summary>
<form method="post"><?= csrf_field('class_card_update', $back) ?><input type="hidden" name="id" value="<?= $cid ?>">
<div class="form-grid"><div><label>School year</label><input name="school_year" required pattern="\d{4}-\d{4}" value="<?= esc(val($c, 'school_year')) ?>"></div>
<div><label>Semester</label><select name="semester"><?php foreach ($semesters as $s): ?><option<?= val($c, 'semester') === $s ? ' selected' : '' ?>><?= esc($s) ?></option><?php endforeach; ?></select></div>
<div><label>Subject code</label><input name="subject_code" required maxlength="30" value="<?= esc(val($c, 'subject_code')) ?>"></div><div><label>Subject description</label><input name="subject_description" required maxlength="160" value="<?= esc(val($c, 'subject_description')) ?>"></div>
<div><label>Teacher</label><select name="teacher_id"><?php foreach ($teachers as $t): ?><option value="<?= (int)val($t, 'id', 0) ?>"<?= (int)val($t, 'id', 0) === (int)val($c, 'teacher_id', 0) ? ' selected' : '' ?>><?= esc(val($t, 'full_name')) ?></option><?php endforeach; ?></select></div>
<div><label>No. of units</label><input name="units" type="number" step="0.5" min="0.5" max="12" required value="<?= esc(val($c, 'units')) ?>"></div>
<div><label>Mode of delivery</label><input name="mode_of_delivery" maxlength="40" value="<?= esc(val($c, 'mode_of_delivery')) ?>"></div>
<div><label>Fee (PHP)<?= $paid ? ' — locked after payment' : '' ?></label><input name="fee" type="number" step="0.01" min="0" required value="<?= esc(number_format((float)val($c, 'fee', 0), 2, '.', '')) ?>"<?= $paid ? ' readonly' : '' ?>></div>
<div><label>Start date</label><input type="date" name="start_date" required value="<?= esc(val($c, 'start_date')) ?>"></div><div><label>End date</label><input type="date" name="end_date" required value="<?= esc(val($c, 'end_date')) ?>"></div>
<div><label>Final grade (blank = not graded)</label><input name="final_grade" maxlength="10" value="<?= esc(val($c, 'final_grade')) ?>"></div><div><label>Remarks</label><input name="remarks" maxlength="255" value="<?= esc(val($c, 'remarks')) ?>"></div></div>
<label>Schedule</label><input name="schedule" maxlength="200" value="<?= esc(val($c, 'schedule')) ?>"><p><button type="submit">Save changes</button></p></form>
<form method="post"<?= confirm_attr('Delete this class card? A card with a recorded payment cannot be deleted.') ?>><?= csrf_field('class_card_delete', $back) ?><input type="hidden" name="id" value="<?= $cid ?>"><button class="danger" type="submit">Delete class card</button></form></details><?php endif; ?>
</div><?php endforeach; ?></div>
<?php endif; ?>
