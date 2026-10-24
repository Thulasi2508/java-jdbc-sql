PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS students (
    roll_number TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    department TEXT NOT NULL,
    current_semester INTEGER NOT NULL CHECK (current_semester BETWEEN 1 AND 8)
);

CREATE TABLE IF NOT EXISTS courses (
    course_code TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    credits REAL NOT NULL CHECK (credits > 0),
    course_type TEXT NOT NULL CHECK (course_type IN ('THEORY', 'PRACTICAL', 'EMBEDDED'))
);

CREATE TABLE IF NOT EXISTS enrollments (
    enrollment_id INTEGER PRIMARY KEY AUTOINCREMENT,
    roll_number TEXT NOT NULL,
    course_code TEXT NOT NULL,
    semester INTEGER NOT NULL CHECK (semester BETWEEN 1 AND 8),
    total_classes INTEGER NOT NULL DEFAULT 0 CHECK (total_classes >= 0),
    attended_classes INTEGER NOT NULL DEFAULT 0 CHECK (attended_classes >= 0 AND attended_classes <= total_classes),
    medical_condonation_approved INTEGER NOT NULL DEFAULT 0 CHECK (medical_condonation_approved IN (0, 1)),
    UNIQUE (roll_number, course_code, semester),
    FOREIGN KEY (roll_number) REFERENCES students (roll_number) ON DELETE RESTRICT,
    FOREIGN KEY (course_code) REFERENCES courses (course_code) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS assessment_marks (
    enrollment_id INTEGER PRIMARY KEY,
    cia_test1 REAL CHECK (cia_test1 BETWEEN 0 AND 100),
    cia_test2 REAL CHECK (cia_test2 BETWEEN 0 AND 100),
    assignment REAL CHECK (assignment BETWEEN 0 AND 100),
    ese_mark REAL CHECK (ese_mark BETWEEN 0 AND 100),
    ese_absent INTEGER NOT NULL DEFAULT 0 CHECK (ese_absent IN (0, 1)),
    CHECK (ese_absent = 0 OR ese_mark IS NULL),
    FOREIGN KEY (enrollment_id) REFERENCES enrollments (enrollment_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_enrollments_semester ON enrollments (semester);
CREATE INDEX IF NOT EXISTS idx_students_department ON students (department);