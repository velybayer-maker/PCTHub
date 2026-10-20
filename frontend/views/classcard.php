<?php defined('PCTHUB') or exit;
$school = 'Philippine College of Technology - Calinan Branch';
$address = 'Garden Park, Village, Bajada, Davao City';
$registrarEmail = 'rmo@pctdavao.edu.ph';
$reminders = [
    'Instructors are given seven (7) days after the end of the subject to finalize the final grade in PCTHub.',
    'The instructor\'s digital submission (name, date and time) below replaces the handwritten e-signature.',
    'Check that this card is genuine: scan the QR code or enter the verification code on the PCTHub "Verify card" page.',
    'This CLASS CARD serves as the grading sheet and exam permit of the student for this subject.',
    'Only the official email address given (pctdavao.edu.ph) will be used during the conduct of online classes.',
    'Tuition fee shall be paid before every subject starts.',
    'Pay only through PCT Cashier or through RCBC, RD Pawnshop, Palawan Express or through Bank to Bank transfer.',
];
$id = (int)($_GET['id'] ?? 0);
$r = api('GET', '/class-cards/' . $id); $c = $r['data'];
$back = ($basePath ?: '') . '/?page=classcards';
if ($r['error'] !== ''):
?>
<div class="card"><h2 style="margin-top:0"><?= $r['status'] === 402 ? 'Payment required' : 'Class card unavailable' ?></h2><div class="alert error"><?= esc($r['error']) ?></div><a class="button" href="<?= esc($back) ?>">← Back to class cards</a></div>
<?php return; endif;
$dash = static fn($v): string => (string)$v === '' ? '—' : esc($v);
$term = 'SY' . val($c, 'school_year') . ' / ' . val($c, 'semester');
$orNo = val($c, 'or_number') ?: val($c, 'payment_reference');
$graded = val($c, 'final_grade', '') !== '' && val($c, 'final_grade', null) !== null;
$code = (string)val($c, 'verify_code');
$yearCourse = trim(val($c, 'course') . ' ' . (val($c, 'year_level') ? '- ' . val($c, 'year_level') : ''));
?>
<div class="no-print cc-actions"><a class="button secondary" href="<?= esc($back) ?>">← Back</a><button type="button" onclick="window.print()">Print / Save as PDF</button></div>
<section class="cc-sheet" aria-label="Class card">
  <div class="cc-head"><img class="cc-logo" src="assets/logo.png" alt="PCT logo" width="84" height="84">
    <div><div class="cc-school"><?= esc($school) ?></div><div class="cc-addr"><?= esc($address) ?></div></div>
    <div class="cc-stamp"><strong>PAID</strong><span>OR No. <?= esc($orNo) ?></span><span><?= esc(fmt_dt((string)val($c, 'paid_at'), 'M j, Y')) ?></span></div></div>
  <h1 class="cc-title">Class Card</h1><div class="cc-term"><?= esc($term) ?></div>
  <table class="cc-table"><tbody>
    <tr><th>Name of Student</th><td><strong><?= $dash(strtoupper((string)val($c, 'student_name'))) ?></strong> <span class="muted"><?= esc(val($c, 'student_number')) ?></span></td></tr>
    <tr><th>Course &amp; Year</th><td><?= $dash($yearCourse) ?></td></tr>
    <tr><th>Student PCT email (.edu.ph)</th><td><?= $dash(val($c, 'student_pct_email')) ?></td></tr>
    <tr><th>Contact # of Student</th><td><?= $dash(val($c, 'student_contact')) ?></td></tr>
    <tr><th>Personal Email Address of Student</th><td><?= $dash(val($c, 'student_personal_email')) ?></td></tr>
    <tr><th>Official Receipt No.</th><td><strong><?= $dash($orNo) ?></strong> <span class="muted">· <?= esc(money(val($c, 'fee', 0))) ?> · ref <?= esc(val($c, 'payment_reference')) ?></span></td></tr>
    <tr><th>Name of Teacher</th><td><?= $dash(strtoupper((string)val($c, 'teacher_name'))) ?></td></tr>
    <tr><th>Email Address of Teacher</th><td><?= $dash(val($c, 'teacher_email')) ?></td></tr>
    <tr><th>Contact # of Teacher</th><td><?= $dash(val($c, 'teacher_contact')) ?></td></tr>
    <tr><th>Subject Code</th><td><strong><?= $dash(val($c, 'subject_code')) ?></strong></td></tr>
    <tr><th>Subject Description</th><td><?= $dash(val($c, 'subject_description')) ?></td></tr>
    <tr><th>Mode of Delivery</th><td><?= $dash(val($c, 'mode_of_delivery')) ?></td></tr>
    <tr><th>No. of Units</th><td><?= $dash(val($c, 'units')) ?></td></tr>
    <tr><th>Duration</th><td><?= esc(fmt_dt((string)val($c, 'start_date'), 'm/d/Y')) ?> – <?= esc(fmt_dt((string)val($c, 'end_date'), 'm/d/Y')) ?></td></tr>
    <tr><th>Schedule (day/time/room/type)</th><td><?= $dash(val($c, 'schedule')) ?></td></tr>
  </tbody></table>
  <div class="cc-grade"><div><span class="lbl">Final Grade</span><span class="big"><?= $graded ? esc(val($c, 'final_grade')) : 'Pending' ?></span></div><div><span class="lbl">Remarks</span><span class="rem"><?= $graded ? $dash(val($c, 'remarks')) : 'Grade not yet submitted by the instructor' ?></span></div></div>
  <div class="cc-verify"><div id="qr" class="cc-qr" data-url="<?= esc(verify_url($code)) ?>" aria-label="QR code to verify this class card"></div>
    <div><div class="lbl">Verification code</div><div class="code"><?= esc($code) ?></div><div class="meta">To confirm this class card is genuine, scan the QR code or enter the code under Verify card in PCTHub. Issued <?= esc(fmt_dt((string)val($c, 'paid_at'))) ?>.</div></div></div>
  <div class="cc-rem"><div class="lbl">Important Reminders</div><ol class="cc-rem-list"><?php foreach ($reminders as $line): ?><li><?= esc($line) ?></li><?php endforeach; ?></ol></div>
  <div class="cc-sign"><div><div class="lbl">Prepared by:</div><div class="sigline"><?= esc(val($c, 'prepared_by')) ?></div><div class="meta">Record In-Charge</div></div>
    <div><div class="lbl">Submitted by:</div><div class="sigline"><?= $graded ? esc(val($c, 'finalized_by_name') ?: val($c, 'teacher_name')) : esc(val($c, 'teacher_name')) ?></div><div class="meta">Instructor Signature over Printed Name</div></div></div>
</section>
<script src="assets/vendor/qrcode.js"></script>
<script>(function(){var el=document.getElementById('qr');if(!el||typeof qrcode==='undefined')return;var q=qrcode(0,'M');q.addData(el.getAttribute('data-url'));q.make();el.innerHTML=q.createSvgTag({cellSize:4,margin:2,scalable:true});})();</script>
