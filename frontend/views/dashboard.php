<?php defined('PCTHUB') or exit;
$isStaff = in_array($role, ['CASHIER', 'ADMIN', 'TEACHER'], true);
$canPay = in_array($role, ['CASHIER', 'ADMIN'], true);
$canTask = in_array($role, ['TEACHER', 'ADMIN'], true);
$sum = api('GET', '/payments/summary'); $summary = $sum['data'];
$pay = api('GET', '/payments'); $payments = array_values(array_filter($pay['data'], 'is_array'));
$tk = api('GET', '/tasks'); $tasks = array_values(array_filter($tk['data'], 'is_array'));
$students = $canPay || $canTask ? array_values(array_filter(api('GET', '/students')['data'], 'is_array')) : [];
$reports = $isStaff ? api('GET', '/reports')['data'] : [];
$ann = array_slice(array_values(array_filter(api('GET', '/announcements')['data'], 'is_array')), 0, 3);
$ev = array_slice(array_values(array_filter(api('GET', '/events?from=' . date('Y-m-d') . '&to=' . date('Y-m-d', strtotime('+60 days')))['data'], 'is_array')), 0, 4);
$loadError = $sum['error'];
?>
<div style="margin-bottom:20px"><h1>Hello, <?= esc($user['fullName'] ?? '') ?>.</h1><div class="muted">Here is your payment and academic overview.</div></div>
<?php if ($loadError): ?><div class="alert error"><?= esc($loadError) ?></div><?php endif; ?>
<div class="grid"><div class="card"><div class="muted">Total school fees</div><div class="metric"><?= money(val($summary, 'totalFees', 0)) ?></div></div><div class="card"><div class="muted">Payments recorded</div><div class="metric"><?= money(val($summary, 'paid', 0)) ?></div></div><div class="card"><div class="muted">Outstanding balance</div><div class="metric"><?= money(val($summary, 'balance', 0)) ?></div></div></div>

<div class="grid2">
<div class="card"><div class="post-head"><h2 style="margin:0">Latest announcements</h2><a href="?page=announcements">View all</a></div>
<?php if (!$ann): ?><div class="empty">No announcements yet.</div><?php endif; ?>
<?php foreach ($ann as $a): ?><div class="mini"><strong><?= !empty($a['pinned']) ? '📌 ' : '' ?><?= esc(val($a, 'title')) ?></strong><div class="meta"><?= esc(fmt_dt((string)val($a, 'created_at'), 'M j, Y')) ?></div></div><?php endforeach; ?></div>
<div class="card"><div class="post-head"><h2 style="margin:0">Upcoming events</h2><a href="?page=calendar">Open calendar</a></div>
<?php if (!$ev): ?><div class="empty">No events in the next 60 days.</div><?php endif; ?>
<?php foreach ($ev as $e): ?><div class="mini"><strong><?= esc(val($e, 'title')) ?></strong><div class="meta"><?= esc(fmt_dt((string)val($e, 'start_at'), 'D, M j · g:i A')) ?><?= val($e, 'location') ? ' · ' . esc(val($e, 'location')) : '' ?></div></div><?php endforeach; ?></div>
</div>

<div class="section-title"><h2>Academic tasks</h2><?php if ($canTask): ?><span class="tag">Task management</span><?php endif; ?></div>
<div class="card"><?php if ($tasks): foreach ($tasks as $task): $tid = (int)val($task, 'id', 0); ?>
<article class="task"><h3><?= esc(val($task, 'title')) ?></h3><div class="meta"><?= esc(val($task, 'subject')) ?> · Due <?= esc(fmt_dt((string)val($task, 'due_at'))) ?> · By <?= esc(val($task, 'created_by')) ?></div><p><?= nl2br(esc(val($task, 'description'))) ?></p>
<?php if ($role === 'STUDENT'): ?><p><span class="tag">Status: <?= esc(val($task, 'submission_status', 'Not submitted')) ?></span></p>
<form class="inline-form" method="post"><?= csrf_field('submit_task') ?><input type="hidden" name="task_id" value="<?= $tid ?>"><label>Submission / response</label><textarea name="submission_text" placeholder="Enter your response or submission notes" required></textarea><button type="submit">Save submission</button></form>
<?php else: ?><div class="meta">Submissions: <?= esc(val($task, 'submissions', '—')) ?></div><?php endif; ?>
<?php if ($role === 'ADMIN'): ?><details class="edit"><summary>Edit or delete</summary>
<form method="post"><?= csrf_field('task_update') ?><input type="hidden" name="id" value="<?= $tid ?>"><div class="form-grid"><div><label>Task title</label><input name="title" required maxlength="180" value="<?= esc(val($task, 'title')) ?>"></div><div><label>Subject</label><input name="subject" required maxlength="120" value="<?= esc(val($task, 'subject')) ?>"></div><div><label>Deadline</label><input type="datetime-local" name="due_at" required value="<?= esc(dt_local((string)val($task, 'due_at'))) ?>"></div><div><label>Description</label><textarea name="description"><?= esc(val($task, 'description')) ?></textarea></div></div><p><button type="submit">Save changes</button></p></form>
<form method="post"<?= confirm_attr('Delete this task and all of its submissions?') ?>><?= csrf_field('task_delete') ?><input type="hidden" name="id" value="<?= $tid ?>"><button class="danger" type="submit">Delete task</button></form></details><?php endif; ?>
</article><?php endforeach; else: ?><div class="empty">No academic tasks have been added yet.</div><?php endif; ?></div>

<?php if ($canTask): ?><div class="section-title"><h2>Create academic task</h2></div><div class="card"><form method="post"><?= csrf_field('create_task') ?><div class="form-grid"><div><label>Task title</label><input name="title" required maxlength="180"></div><div><label>Subject</label><input name="subject" required maxlength="120"></div><div><label>Deadline</label><input type="datetime-local" name="due_at" required></div><div><label>Description</label><textarea name="description" placeholder="Instructions and requirements"></textarea></div></div><p><button type="submit">Create task</button></p></form></div><?php endif; ?>

<div class="section-title"><h2><?= $canPay ? 'Payment records' : 'My payment history' ?></h2></div>
<div class="card"><div class="table-wrap"><table><thead><tr><?php if ($canPay): ?><th>Student</th><?php endif; ?><th>Receipt reference</th><th>Date</th><th>Method</th><th>Amount</th><th>Recorded by</th></tr></thead><tbody>
<?php if ($payments): foreach ($payments as $p): ?><tr><?php if ($canPay): ?><td><?= esc(val($p, 'student_name')) ?> <span class="muted"><?= esc(val($p, 'student_number')) ?></span></td><?php endif; ?><td><?= esc(val($p, 'reference_no')) ?></td><td><?= esc(fmt_dt((string)val($p, 'paid_at'))) ?></td><td><?= esc(val($p, 'payment_method')) ?></td><td><?= money(val($p, 'amount')) ?></td><td><?= esc(val($p, 'recorded_by')) ?></td></tr>
<?php endforeach; else: ?><tr><td colspan="6" class="empty">No payment records found.</td></tr><?php endif; ?></tbody></table></div></div>

<?php if ($canPay): ?><div class="section-title"><h2>Record a student payment</h2></div><div class="card"><form method="post"><?= csrf_field('record_payment') ?><div class="form-grid"><div><label>Student account</label><select name="student_id" required><?php foreach ($students as $s): ?><option value="<?= esc(val($s, 'id')) ?>"><?= esc(val($s, 'full_name')) ?> — <?= esc(val($s, 'student_number')) ?></option><?php endforeach; ?></select></div><div><label>Amount (PHP)</label><input name="amount" type="number" min="0.01" step="0.01" required></div><div><label>Payment method</label><select name="payment_method"><option value="CASH">Cash</option><option value="BANK_TRANSFER">Bank transfer</option><option value="GCASH">GCash</option><option value="OTHER">Other</option></select></div><div><label>Notes (optional)</label><input name="notes" maxlength="255"></div></div><p><button type="submit">Record payment</button></p><div class="muted" style="font-size:12px">This records a payment entry; it does not process or verify an actual bank or GCash transaction.</div></form></div><?php endif; ?>

<?php if ($isStaff && $reports): ?><div class="section-title"><h2>System summary</h2></div><div class="grid"><div class="card"><div class="muted">Payment transactions</div><div class="metric"><?= esc(val(val($reports, 'payments', []), 'payment_count', 0)) ?></div></div><div class="card"><div class="muted">Total collected</div><div class="metric"><?= money(val(val($reports, 'payments', []), 'collected', 0)) ?></div></div><div class="card"><div class="muted">Task submissions</div><div class="metric"><?= esc(val(val($reports, 'academic', []), 'submission_count', 0)) ?></div></div></div><?php endif; ?>
