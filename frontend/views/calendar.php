<?php defined('PCTHUB') or exit;
$isAdmin = $role === 'ADMIN';
$m = (string)($_GET['m'] ?? date('Y-m'));
if (!preg_match('/^\d{4}-(0[1-9]|1[0-2])$/', $m)) $m = date('Y-m');
$first = new DateTimeImmutable($m . '-01');
$days = (int)$first->format('t'); $startDow = (int)$first->format('w');
$prev = $first->modify('-1 month')->format('Y-m'); $next = $first->modify('+1 month')->format('Y-m');
$r = api('GET', '/events?from=' . $first->format('Y-m-d') . '&to=' . $first->format('Y-m-t'));
$events = array_values(array_filter($r['data'], 'is_array'));
$byDay = [];
foreach ($events as $e) { $byDay[substr((string)val($e, 'start_at'), 0, 10)][] = $e; }
$today = date('Y-m-d'); $back = 'page=calendar&m=' . $m; $base = ($basePath ?: '') . '/?page=calendar&m=';
?>
<div style="margin-bottom:20px"><h1>School calendar</h1><div class="muted">Events, deadlines and activities.</div></div>
<?php if ($r['error']): ?><div class="alert error"><?= esc($r['error']) ?></div><?php endif; ?>

<div class="cal-nav"><a class="button secondary" href="<?= esc($base . $prev) ?>">← Previous</a><h2><?= esc($first->format('F Y')) ?></h2><a class="button secondary" href="<?= esc($base . $next) ?>">Next →</a></div>
<div class="card cal-card"><div class="cal">
<?php foreach (['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'] as $d): ?><div class="cal-h"><?= $d ?></div><?php endforeach; ?>
<?php for ($i = 0; $i < $startDow; $i++): ?><div class="cal-d blank"></div><?php endfor; ?>
<?php for ($d = 1; $d <= $days; $d++): $key = sprintf('%s-%02d', $m, $d); $list = $byDay[$key] ?? []; ?>
  <div class="cal-d<?= $key === $today ? ' today' : '' ?>"><span class="num"><?= $d ?></span>
  <?php foreach (array_slice($list, 0, 3) as $e): ?><a class="chip" href="#event-<?= (int)val($e, 'id', 0) ?>" title="<?= esc(val($e, 'title')) ?>"><?= esc(val($e, 'title')) ?></a><?php endforeach; ?>
  <?php if (count($list) > 3): ?><span class="more">+<?= count($list) - 3 ?> more</span><?php endif; ?></div>
<?php endfor; ?>
</div></div>

<div class="section-title"><h2>Events in <?= esc($first->format('F')) ?></h2></div>
<div class="card">
<?php if (!$events): ?><div class="empty">No events scheduled this month.</div><?php endif; ?>
<?php foreach ($events as $e): $eid = (int)val($e, 'id', 0); ?>
<article class="task" id="event-<?= $eid ?>"><h3><?= esc(val($e, 'title')) ?></h3>
  <div class="meta"><?= esc(fmt_dt((string)val($e, 'start_at'), 'D, M j · g:i A')) ?><?= val($e, 'end_at') ? ' – ' . esc(fmt_dt((string)val($e, 'end_at'), substr((string)val($e, 'end_at'), 0, 10) === substr((string)val($e, 'start_at'), 0, 10) ? 'g:i A' : 'D, M j · g:i A')) : '' ?><?= val($e, 'location') ? ' · ' . esc(val($e, 'location')) : '' ?></div>
  <?php if (val($e, 'description')): ?><p><?= nl2br(esc(val($e, 'description'))) ?></p><?php endif; ?>
  <?php if ($isAdmin): ?>
  <details class="edit"><summary>Edit or delete</summary>
    <form method="post"><?= csrf_field('event_save', $back) ?><input type="hidden" name="id" value="<?= $eid ?>">
      <div class="form-grid"><div><label>Title</label><input name="title" required maxlength="180" value="<?= esc(val($e, 'title')) ?>"></div><div><label>Location</label><input name="location" maxlength="160" value="<?= esc(val($e, 'location')) ?>"></div>
      <div><label>Starts</label><input type="datetime-local" name="start_at" required value="<?= esc(dt_local((string)val($e, 'start_at'))) ?>"></div><div><label>Ends (optional)</label><input type="datetime-local" name="end_at" value="<?= esc(dt_local((string)val($e, 'end_at'))) ?>"></div></div>
      <label>Description</label><textarea name="description" maxlength="2000"><?= esc(val($e, 'description')) ?></textarea>
      <p><button type="submit">Save changes</button></p></form>
    <form method="post"<?= confirm_attr('Delete this event?') ?>><?= csrf_field('event_delete', $back) ?><input type="hidden" name="id" value="<?= $eid ?>"><button class="danger" type="submit">Delete event</button></form>
  </details>
  <?php endif; ?>
</article>
<?php endforeach; ?>
</div>

<?php if ($isAdmin): ?>
<div class="section-title"><h2>Add an event</h2></div>
<div class="card"><form method="post"><?= csrf_field('event_save', $back) ?><input type="hidden" name="id" value="0">
<div class="form-grid"><div><label>Title</label><input name="title" required maxlength="180"></div><div><label>Location</label><input name="location" maxlength="160"></div>
<div><label>Starts</label><input type="datetime-local" name="start_at" required></div><div><label>Ends (optional)</label><input type="datetime-local" name="end_at"></div></div>
<label>Description</label><textarea name="description" maxlength="2000" placeholder="Details, requirements, who should attend"></textarea>
<p><button type="submit">Add to calendar</button></p></form></div>
<?php endif; ?>
