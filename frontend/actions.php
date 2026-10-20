<?php
declare(strict_types=1);
defined('PCTHUB') or exit;

$csrf = (string)($_POST['csrf'] ?? '');
if (!hash_equals($_SESSION['csrf'], $csrf)) { http_response_code(400); exit('Invalid request token. Refresh and try again.'); }
$action = (string)($_POST['action'] ?? '');
$back = (string)($_POST['back'] ?? '');
$p = static fn(string $k): string => trim((string)($_POST[$k] ?? ''));
$profile = static fn(): array => ['course' => trim((string)($_POST['course'] ?? '')), 'yearLevel' => trim((string)($_POST['year_level'] ?? '')), 'pctEmail' => trim((string)($_POST['pct_email'] ?? '')), 'personalEmail' => trim((string)($_POST['personal_email'] ?? '')), 'contactNo' => trim((string)($_POST['contact_no'] ?? ''))];
$dt = static fn(string $k): string => str_replace(' ', 'T', trim((string)($_POST[$k] ?? '')));

if ($action === 'login') {
    $r = api('POST', '/auth/login', ['username' => $p('username'), 'password' => (string)($_POST['password'] ?? '')], false);
    if ($r['status'] === 200 && !empty($r['data']['token'])) {
        session_regenerate_id(true);
        $_SESSION['token'] = $r['data']['token']; $_SESSION['user'] = $r['data']['user'];
        $next = (string)($_SESSION['after_login'] ?? ''); unset($_SESSION['after_login']);
        flash('Welcome to PCTHub, ' . ($r['data']['user']['fullName'] ?? 'user') . '.'); redirect($next);
    }
    flash($r['error'] ?: 'Login failed. Check your username and password.', 'error'); redirect();
}
if ($action === 'logout') { $_SESSION = []; session_regenerate_id(true); session_destroy(); redirect(); }
if (empty($_SESSION['token'])) redirect();

switch ($action) {
    // ---- existing features
    case 'record_payment':
        flash_result(api('POST', '/payments', ['studentId' => (int)($_POST['student_id'] ?? 0), 'amount' => $p('amount'), 'paymentMethod' => $p('payment_method') ?: 'CASH', 'notes' => $p('notes')]), 'Payment request failed'); break;
    case 'create_task':
        flash_result(api('POST', '/tasks', ['title' => $p('title'), 'description' => $p('description'), 'subject' => $p('subject'), 'dueAt' => $dt('due_at')]), 'Task request failed'); break;
    case 'submit_task':
        flash_result(api('POST', '/tasks/' . (int)($_POST['task_id'] ?? 0) . '/submissions', ['submissionText' => $p('submission_text')]), 'Submission failed'); break;
    case 'task_update':
        flash_result(api('PUT', '/tasks/' . (int)($_POST['id'] ?? 0), ['title' => $p('title'), 'subject' => $p('subject'), 'description' => $p('description'), 'dueAt' => $dt('due_at')]), 'Could not update the task'); break;
    case 'task_delete':
        flash_result(api('DELETE', '/tasks/' . (int)($_POST['id'] ?? 0)), 'Could not delete the task'); break;

    // ---- announcements
    case 'announcement_save':
        $id = (int)($_POST['id'] ?? 0);
        $body = ['title' => $p('title'), 'body' => $p('body'), 'audience' => $p('audience') ?: 'ALL', 'pinned' => isset($_POST['pinned'])];
        flash_result($id > 0 ? api('PUT', '/announcements/' . $id, $body) : api('POST', '/announcements', $body), 'Could not save the announcement'); break;
    case 'announcement_delete':
        flash_result(api('DELETE', '/announcements/' . (int)($_POST['id'] ?? 0)), 'Could not delete the announcement'); break;

    // ---- calendar
    case 'event_save':
        $id = (int)($_POST['id'] ?? 0);
        $body = ['title' => $p('title'), 'description' => $p('description'), 'location' => $p('location'), 'startAt' => $dt('start_at'), 'endAt' => $dt('end_at')];
        flash_result($id > 0 ? api('PUT', '/events/' . $id, $body) : api('POST', '/events', $body), 'Could not save the event'); break;
    case 'event_delete':
        flash_result(api('DELETE', '/events/' . (int)($_POST['id'] ?? 0)), 'Could not delete the event'); break;

    // ---- clearance
    case 'clearance_set':
        flash_result(api('PUT', '/clearance/' . (int)($_POST['student_id'] ?? 0) . '/' . (int)($_POST['item_id'] ?? 0), ['status' => $p('status'), 'remarks' => $p('remarks')]), 'Could not update clearance'); break;
    case 'clearance_all':
        flash_result(api('PUT', '/clearance-all/' . (int)($_POST['student_id'] ?? 0), ['status' => $p('status')]), 'Could not update clearance'); break;
    case 'clearance_item_save':
        $id = (int)($_POST['id'] ?? 0);
        $body = ['name' => $p('name'), 'description' => $p('description')];
        flash_result($id > 0 ? api('PUT', '/clearance-items/' . $id, $body) : api('POST', '/clearance-items', $body), 'Could not save the requirement'); break;
    case 'clearance_item_delete':
        flash_result(api('DELETE', '/clearance-items/' . (int)($_POST['id'] ?? 0)), 'Could not delete the requirement'); break;

    // ---- accounts
    case 'user_register':
        flash_result(api('POST', '/admin/users', ['role' => $p('role'), 'fullName' => $p('full_name'), 'username' => $p('username'), 'password' => (string)($_POST['password'] ?? ''), 'studentNumber' => $p('student_number'), 'totalFees' => $p('total_fees')] + $profile()), 'Could not create the account'); break;
    case 'user_update':
        flash_result(api('PUT', '/admin/users/' . (int)($_POST['id'] ?? 0), ['fullName' => $p('full_name'), 'studentNumber' => $p('student_number'), 'totalFees' => $p('total_fees'), 'active' => isset($_POST['active']), 'newPassword' => (string)($_POST['new_password'] ?? '')] + $profile()), 'Could not update the account'); break;
    case 'user_delete':
        flash_result(api('DELETE', '/admin/users/' . (int)($_POST['id'] ?? 0)), 'Could not delete the account'); break;

    // ---- class cards
    case 'class_card_create': case 'class_card_update':
        $body = ['teacherId' => $p('teacher_id'), 'schoolYear' => $p('school_year'), 'semester' => $p('semester'), 'subjectCode' => $p('subject_code'), 'subjectDescription' => $p('subject_description'), 'units' => $p('units'), 'modeOfDelivery' => $p('mode_of_delivery'), 'startDate' => $p('start_date'), 'endDate' => $p('end_date'), 'schedule' => $p('schedule'), 'fee' => $p('fee')];
        if ($action === 'class_card_create') { $body['studentIds'] = array_map('intval', (array)($_POST['student_ids'] ?? [])); flash_result(api('POST', '/class-cards', $body), 'Could not create the class cards'); }
        else { $body['finalGrade'] = $p('final_grade'); $body['remarks'] = $p('remarks'); flash_result(api('PUT', '/class-cards/' . (int)($_POST['id'] ?? 0), $body), 'Could not update the class card'); }
        break;
    case 'class_card_delete':
        flash_result(api('DELETE', '/class-cards/' . (int)($_POST['id'] ?? 0)), 'Could not delete the class card'); break;
    case 'class_card_pay':
        flash_result(api('POST', '/class-cards/' . (int)($_POST['id'] ?? 0) . '/payment', ['paymentMethod' => $p('payment_method') ?: 'CASH', 'officialReceiptNo' => $p('official_receipt_no')]), 'Could not record the payment'); break;
    case 'class_card_grade':
        flash_result(api('POST', '/class-cards/' . (int)($_POST['id'] ?? 0) . '/grade', ['finalGrade' => $p('final_grade'), 'remarks' => $p('remarks')]), 'Could not submit the grade'); break;
}
redirect($back);
