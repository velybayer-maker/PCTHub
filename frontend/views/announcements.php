<?php defined('PCTHUB') or exit;
$isAdmin = $role === 'ADMIN';
$r = api('GET', '/announcements');
$list = array_values(array_filter($r['data'], 'is_array'));
$audiences = ['ALL' => 'Everyone', 'STUDENT' => 'Students only', 'TEACHER' => 'Teachers only'];
?>
<div style="margin-bottom:20px"><h1>Announcement board</h1><div class="muted">News and notices from the school administration.</div></div>
<?php if ($r['error']): ?><div class="alert error"><?= esc($r['error']) ?></div><?php endif; ?>

<?php if ($isAdmin): ?>
<div class="card"><h2 style="margin-top:0">Post an announcement</h2>
<form method="post"><?= csrf_field('announcement_save', 'page=announcements') ?><input type="hidden" name="id" value="0">
<div class="form-grid"><div><label>Title</label><input name="title" required maxlength="180"></div>
<div><label>Who can see it</label><select name="audience"><?php foreach ($audiences as $k => $l): ?><option value="<?= esc($k) ?>"><?= esc($l) ?></option><?php endforeach; ?></select></div></div>
<label>Message</label><textarea name="body" required maxlength="5000"></textarea>
<label class="check"><input type="checkbox" name="pinned" value="1"> Pin to the top of the board</label>
<p><button type="submit">Post announcement</button></p></form></div>
<?php endif; ?>

<?php if (!$list): ?><div class="card"><div class="empty">No announcements yet.</div></div><?php endif; ?>
<?php foreach ($list as $a): $aid = (int)val($a, 'id', 0); ?>
<article class="card post<?= !empty($a['pinned']) ? ' pinned' : '' ?>">
  <div class="post-head"><h3><?= !empty($a['pinned']) ? '📌 ' : '' ?><?= esc(val($a, 'title')) ?></h3><span class="tag"><?= esc($audiences[val($a, 'audience', 'ALL')] ?? 'Everyone') ?></span></div>
  <div class="meta">Posted by <?= esc(val($a, 'author')) ?> · <?= esc(fmt_dt((string)val($a, 'created_at'), 'M j, Y g:i A')) ?><?= val($a, 'updated_at') !== val($a, 'created_at') ? ' · edited' : '' ?></div>
  <p><?= nl2br(esc(val($a, 'body'))) ?></p>
  <?php if ($isAdmin): ?>
  <details class="edit"><summary>Edit or delete</summary>
    <form method="post"><?= csrf_field('announcement_save', 'page=announcements') ?><input type="hidden" name="id" value="<?= $aid ?>">
      <div class="form-grid"><div><label>Title</label><input name="title" required maxlength="180" value="<?= esc(val($a, 'title')) ?>"></div>
      <div><label>Who can see it</label><select name="audience"><?php foreach ($audiences as $k => $l): ?><option value="<?= esc($k) ?>"<?= val($a, 'audience') === $k ? ' selected' : '' ?>><?= esc($l) ?></option><?php endforeach; ?></select></div></div>
      <label>Message</label><textarea name="body" required maxlength="5000"><?= esc(val($a, 'body')) ?></textarea>
      <label class="check"><input type="checkbox" name="pinned" value="1"<?= !empty($a['pinned']) ? ' checked' : '' ?>> Pinned</label>
      <p><button type="submit">Save changes</button></p></form>
    <form method="post"<?= confirm_attr('Delete this announcement? This cannot be undone.') ?>><?= csrf_field('announcement_delete', 'page=announcements') ?><input type="hidden" name="id" value="<?= $aid ?>"><button class="danger" type="submit">Delete announcement</button></form>
  </details>
  <?php endif; ?>
</article>
<?php endforeach; ?>
