package com.pcthub;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the tables for announcements, the event calendar and student clearance
 * the first time the API starts (safe to run every time: CREATE TABLE IF NOT EXISTS).
 */
@Component
@Order(1)
class CampusSchema implements CommandLineRunner {
    private final JdbcTemplate db;
    CampusSchema(JdbcTemplate db) { this.db = db; }

    private static final String[] DDL = {
        """
        CREATE TABLE IF NOT EXISTS announcements (
          id BIGINT PRIMARY KEY AUTO_INCREMENT,
          title VARCHAR(180) NOT NULL,
          body TEXT NOT NULL,
          audience ENUM('ALL','STUDENT','TEACHER') NOT NULL DEFAULT 'ALL',
          pinned BOOLEAN NOT NULL DEFAULT FALSE,
          created_by BIGINT NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          CONSTRAINT fk_announcements_creator FOREIGN KEY (created_by) REFERENCES users(id),
          INDEX idx_announcements_created (created_at)
        ) ENGINE=InnoDB
        """,
        """
        CREATE TABLE IF NOT EXISTS calendar_events (
          id BIGINT PRIMARY KEY AUTO_INCREMENT,
          title VARCHAR(180) NOT NULL,
          description TEXT NULL,
          location VARCHAR(160) NULL,
          start_at DATETIME NOT NULL,
          end_at DATETIME NULL,
          created_by BIGINT NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          CONSTRAINT fk_events_creator FOREIGN KEY (created_by) REFERENCES users(id),
          INDEX idx_events_start (start_at)
        ) ENGINE=InnoDB
        """,
        """
        CREATE TABLE IF NOT EXISTS clearance_items (
          id BIGINT PRIMARY KEY AUTO_INCREMENT,
          name VARCHAR(120) NOT NULL UNIQUE,
          description VARCHAR(255) NULL,
          sort_order INT NOT NULL DEFAULT 0
        ) ENGINE=InnoDB
        """,
        """
        CREATE TABLE IF NOT EXISTS student_clearances (
          student_id BIGINT NOT NULL,
          item_id BIGINT NOT NULL,
          status ENUM('PENDING','CLEARED','HOLD') NOT NULL DEFAULT 'PENDING',
          remarks VARCHAR(255) NULL,
          updated_by BIGINT NULL,
          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          PRIMARY KEY (student_id, item_id),
          CONSTRAINT fk_clearance_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
          CONSTRAINT fk_clearance_item FOREIGN KEY (item_id) REFERENCES clearance_items(id) ON DELETE CASCADE,
          CONSTRAINT fk_clearance_updater FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
        ) ENGINE=InnoDB
        """,
        """
        CREATE TABLE IF NOT EXISTS user_profiles (
          user_id BIGINT PRIMARY KEY,
          course VARCHAR(160) NULL,
          year_level VARCHAR(20) NULL,
          pct_email VARCHAR(160) NULL,
          personal_email VARCHAR(160) NULL,
          contact_no VARCHAR(40) NULL,
          CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        ) ENGINE=InnoDB
        """,
        """
        CREATE TABLE IF NOT EXISTS class_cards (
          id BIGINT PRIMARY KEY AUTO_INCREMENT,
          student_id BIGINT NOT NULL,
          teacher_id BIGINT NOT NULL,
          school_year VARCHAR(20) NOT NULL,
          semester VARCHAR(30) NOT NULL,
          subject_code VARCHAR(30) NOT NULL,
          subject_description VARCHAR(160) NOT NULL,
          units DECIMAL(4,1) NOT NULL DEFAULT 3,
          mode_of_delivery VARCHAR(40) NOT NULL DEFAULT 'F2F',
          start_date DATE NOT NULL,
          end_date DATE NOT NULL,
          schedule VARCHAR(200) NULL,
          fee DECIMAL(10,2) NOT NULL DEFAULT 0,
          verify_code VARCHAR(20) NOT NULL UNIQUE,
          final_grade VARCHAR(10) NULL,
          remarks VARCHAR(255) NULL,
          finalized_by BIGINT NULL,
          finalized_at DATETIME NULL,
          created_by BIGINT NOT NULL,
          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          UNIQUE KEY uq_class_card (student_id, teacher_id, school_year, semester, subject_code),
          CONSTRAINT fk_cc_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
          CONSTRAINT fk_cc_teacher FOREIGN KEY (teacher_id) REFERENCES users(id),
          CONSTRAINT fk_cc_creator FOREIGN KEY (created_by) REFERENCES users(id),
          CONSTRAINT fk_cc_finalizer FOREIGN KEY (finalized_by) REFERENCES users(id) ON DELETE SET NULL,
          INDEX idx_cc_teacher (teacher_id)
        ) ENGINE=InnoDB
        """,
        """
        CREATE TABLE IF NOT EXISTS class_card_payments (
          id BIGINT PRIMARY KEY AUTO_INCREMENT,
          class_card_id BIGINT NOT NULL UNIQUE,
          amount DECIMAL(10,2) NOT NULL,
          reference_no VARCHAR(40) NOT NULL UNIQUE,
          or_number VARCHAR(30) NULL UNIQUE,
          payment_method ENUM('CASH','BANK_TRANSFER','GCASH','OTHER') NOT NULL DEFAULT 'CASH',
          recorded_by BIGINT NOT NULL,
          paid_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
          CONSTRAINT fk_ccp_card FOREIGN KEY (class_card_id) REFERENCES class_cards(id),
          CONSTRAINT fk_ccp_recorder FOREIGN KEY (recorded_by) REFERENCES users(id)
        ) ENGINE=InnoDB
        """
    };

    @Override
    public void run(String... args) {
        try {
            for (String statement : DDL) db.execute(statement);
            Integer count = db.queryForObject("SELECT COUNT(*) FROM clearance_items", Integer.class);
            if (count != null && count == 0) {
                String[][] defaults = {
                    {"Finance Office (Cashier)", "Tuition and other fees are settled"},
                    {"Library", "Borrowed books returned and no unpaid fines"},
                    {"Registrar", "Academic records are complete"},
                    {"Department Head", "Department requirements are completed"},
                    {"Guidance Office", "Guidance requirements are completed"}
                };
                int order = 1;
                for (String[] d : defaults) {
                    db.update("INSERT INTO clearance_items(name,description,sort_order) VALUES (?,?,?)", d[0], d[1], order++);
                }
            }
        } catch (Exception e) {
            System.err.println("PCTHub: could not create the announcement/calendar/clearance tables automatically: " + e.getMessage());
            System.err.println("PCTHub: import db/campus-modules.sql in phpMyAdmin (with the pcthub database selected), then restart the API.");
        }
    }
}
