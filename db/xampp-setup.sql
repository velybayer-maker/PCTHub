-- PCTHub setup for XAMPP / MariaDB.
-- In phpMyAdmin, sign in as root and import this entire file.
-- Default XAMPP commonly has root with a blank password; if yours differs, use that password.

CREATE DATABASE IF NOT EXISTS pcthub CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'pcthub_user'@'localhost' IDENTIFIED BY 'PCTHubLocal123!';
GRANT ALL PRIVILEGES ON pcthub.* TO 'pcthub_user'@'localhost';
FLUSH PRIVILEGES;
USE pcthub;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  full_name VARCHAR(160) NOT NULL,
  role ENUM('STUDENT','TEACHER','CASHIER','ADMIN') NOT NULL,
  student_number VARCHAR(40) NULL UNIQUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS student_accounts (
  user_id BIGINT PRIMARY KEY,
  total_fees DECIMAL(12,2) NOT NULL DEFAULT 0,
  CONSTRAINT fk_student_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS payments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  reference_no VARCHAR(80) NOT NULL UNIQUE,
  payment_method ENUM('CASH','BANK_TRANSFER','GCASH','OTHER') NOT NULL DEFAULT 'CASH',
  notes VARCHAR(255) NULL,
  recorded_by BIGINT NOT NULL,
  paid_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_payment_amount CHECK (amount > 0),
  CONSTRAINT fk_payments_student FOREIGN KEY (student_id) REFERENCES users(id),
  CONSTRAINT fk_payments_recorder FOREIGN KEY (recorded_by) REFERENCES users(id),
  INDEX idx_payments_student_date (student_id, paid_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS academic_tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(180) NOT NULL,
  description TEXT NULL,
  subject VARCHAR(120) NOT NULL,
  due_at DATETIME NOT NULL,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_tasks_creator FOREIGN KEY (created_by) REFERENCES users(id),
  INDEX idx_tasks_due (due_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS submissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  submission_text TEXT NOT NULL,
  status ENUM('SUBMITTED','REVIEWED') NOT NULL DEFAULT 'SUBMITTED',
  submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_task_student (task_id, student_id),
  CONSTRAINT fk_submissions_task FOREIGN KEY (task_id) REFERENCES academic_tasks(id) ON DELETE CASCADE,
  CONSTRAINT fk_submissions_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;
