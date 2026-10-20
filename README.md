# PCTHub — Local XAMPP Edition

PCTHub is a development MVP based on `PCTHub-1.docx`, integrating the requested languages and runtime components without Docker:

- **PHP**: web interface served by XAMPP Apache.
- **Java / Spring Boot**: API and business rules, run locally as a separate process.
- **SQL / MariaDB**: persistent data stored by the MySQL-compatible database bundled with XAMPP.

## Requirements

1. XAMPP for Windows with **Apache**, **MySQL** (MariaDB), and PHP's **curl** extension enabled.
2. JDK 21.
3. Apache Maven 3.9+ added to Windows `PATH` (`java -version` and `mvn -version` should work in a new terminal).
4. Internet access on first Java build so Maven can download project dependencies.

## Installation

### 1. Copy PHP files into XAMPP

Copy the contents of this project's `frontend` folder into:

`C:\\xampp\\htdocs\\pcthub\\`

Create the `pcthub` folder if needed. The result should include:

`C:\\xampp\\htdocs\\pcthub\\index.php`

### 2. Start XAMPP services

Open the XAMPP Control Panel and start **Apache** and **MySQL**. Both should show as running.

### 3. Create the database and application user

1. Open `http://localhost/phpmyadmin`.
2. Select the **Import** tab.
3. Choose `db/xampp-setup.sql` from this project folder and click **Import** / **Go**.
4. Confirm that the `pcthub` database and its five tables (`users`, `student_accounts`, `payments`, `academic_tasks`, `submissions`) were created.

This script expects the common local XAMPP setup where you can log in to phpMyAdmin as `root`. It creates a limited local application account named `pcthub_user` with password `PCTHubLocal123!`. If your MySQL installation has different administrative access settings, use an administrator account permitted to create databases and users.

### 4. Install / verify Java and Maven

Open a new PowerShell terminal and run:

```powershell
java -version
mvn -version
```

Use a JDK (not only a JRE), version 21. If either command is not found, install that tool and configure Windows `PATH` before continuing.

### 5. Start the Java API

Double-click `run-api.bat` in the project folder, or run it from PowerShell:

```powershell
.\run-api.bat
```

Leave this terminal open while using PCTHub. On first startup, Maven downloads dependencies. The API should start on `http://127.0.0.1:8081` and logs should show that Spring Boot has started. Check health at `http://127.0.0.1:8081/api/health`; it should return JSON containing `"status":"ok"`.

### 6. Open the PHP application

Go to:

`http://localhost/pcthub/`

The PHP frontend sends requests to the Java API at `http://127.0.0.1:8081/api`. If the page says the backend is unavailable, keep the Java terminal open and check the error there.

## Demo sign-ins

The Java API seeds these development users at startup when `APP_DEMO_SEED=true` (set by `run-api.bat`).

| Role | Username | Password |
|---|---|---|
| Student | `student` | `PCTHub123!` |
| Teacher | `teacher` | `PCTHub123!` |
| Cashier | `cashier` | `PCTHub123!` |
| Administrator | `admin` | `PCTHub123!` |

Sample student fee total is PHP 25,000, and sample academic tasks are added if no tasks exist. These are only demonstration credentials. Do not use them for real student data or expose this setup to the internet.

## Troubleshooting

- **Apache will not start:** port 80 may be occupied. Check the XAMPP logs or configure Apache to use another port, then adjust the URL.
- **MySQL will not start:** port 3306 may be occupied. Check the XAMPP MySQL logs.
- **`curl_init()` is undefined / backend error from PHP:** enable `extension=curl` in the `php.ini` used by XAMPP, restart Apache, and retry.
- **Java reports connection refused / access denied to database:** verify XAMPP MySQL is running, then confirm `pcthub_user` exists and its password matches `run-api.bat`.
- **Maven cannot resolve dependencies:** test Windows connectivity to Maven Central, then retry `mvn spring-boot:run`. Unlike the prior Docker build, Maven now runs directly on Windows.
- **Port 8081 already in use:** change `server.port=8081` in `backend/src/main/resources/application.properties` and update the PHP `$apiBase` URL in `frontend/index.php` to the same port.

## Main functions

- Role-aware login: student, teacher, cashier, administrator.
- Students view their balances, payment history, academic tasks, and submission status.
- Cashier/administrator records payment entries and obtains a receipt reference.
- Teachers/administrators create tasks and deadlines.
- Students submit text responses to tasks.
- Authorized staff view summary reports.

## Limitations and safety

This is a functional development MVP, not production-ready financial software. Payment records are entered by authorized staff; there is no payment gateway integration. Formal receipt PDFs, file uploads, comprehensive account administration, password resets, exportable reports, migrations, and automated integration tests still need development. Before using real student information, review access controls, secret handling, secure cookies/HTTPS, backups, audit requirements, retention policy, and compliance with applicable Philippine data-privacy requirements. Do not expose the development API directly to the internet.
=======
# PCTHub
