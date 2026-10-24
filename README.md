# KEC R2024 Student Academic & Continuous Evaluation System

A menu-driven Java JDBC application backed by SQLite. It manages students, courses, semester enrollments, cumulative attendance, phased CIA marks, ESE results, eligibility, grades, SGPA, attendance defaulters, and department summaries.

## Requirements

- JDK 17 or newer
- Maven 3.8 or newer

## Run

From this directory, run:

```powershell
mvn clean compile exec:java
```

The application creates `kec_academic.db` in the current directory and initializes it from `kec_schema.sql`.

## Workflow

1. Register a student and add course catalog entries.
2. Enroll the student in each course for the semester.
3. Record attendance updates as `(classes held, classes attended)`; counts accumulate.
4. Enter CIA Test 1, CIA Test 2, and assignment / quiz / lab marks separately as each becomes available. Re-entering a component updates it.
5. Approve medical condonation for attendance from 65% to below 75% before recording an ESE result. Attendance below 65% is barred and receives SA.
6. Record an ESE mark out of 100 or mark the student absent.
7. Generate a student's semester mark sheet, attendance defaulters, or department summary.

## Evaluation assumptions

The supplied requirements specify aggregate CIA and ESE weights but not individual CIA component weights. The implementation takes the arithmetic mean of CIA Test 1, CIA Test 2, and assignment / quiz / lab, then scales that mean to the course's CIA weight. ESE input is a percentage from 0 to 100 and is scaled to the ESE weight. The ESE minimum is checked against the scaled score: 27/60 for THEORY, 20/40 for PRACTICAL, and 22.5/50 for EMBEDDED. A course passes only when the scaled ESE minimum and 50-point aggregate minimum are both met.

Grades use the supplied inclusive lower bounds: O (91), A+ (81), A (71), B+ (61), B (50); otherwise RA. SA takes precedence when attendance is below 65%. AB is assigned for an eligible student marked absent in ESE. Attendance of 65% to below 75% remains unresolved until condonation approval. Marksheets show SGPA as pending if any course eligibility or assessment remains unresolved; department summaries exclude those students from finalized counts and average SGPA. Finalized failures (RA, SA, AB) contribute 0 grade points to SGPA.

The exact R2024 regulations may define additional assessment composition or rounding rules. Those details were not included in the supplied specification; confirm them with the institution before production use.

## Database and source

- `kec_schema.sql` creates the required `students`, `courses`, `enrollments`, and `assessment_marks` tables with constraints and foreign keys.
- Java source is under `src/main/java/edu/kec/academic/`.
- `DBConnection` initializes the SQLite schema and provides JDBC connections.

## Execution evidence

For submission screenshots, run the program in the VS Code terminal and capture the terminal after each workflow step. Recommended proof set:

1. Successful student registration followed by successful course creation.
2. Enrollment and attendance update.
3. CIA Test 1, CIA Test 2, and assignment entry on separate menu visits, followed by the mark sheet.
4. ESE entry (or the absent option) and the resulting mark sheet / SGPA.

The screenshots should show the command `mvn clean compile exec:java` and the corresponding success messages or report output. Screenshots must be captured in the target environment after entering its own records; no fabricated execution screenshots are included.