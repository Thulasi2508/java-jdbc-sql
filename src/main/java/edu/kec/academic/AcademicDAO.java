package edu.kec.academic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AcademicDAO {
    public enum CourseType {
        THEORY(40, 60, 27),
        PRACTICAL(60, 40, 20),
        EMBEDDED(50, 50, 22.5);

        private final double ciaWeight;
        private final double eseWeight;
        private final double eseMinimum;

        CourseType(double ciaWeight, double eseWeight, double eseMinimum) {
            this.ciaWeight = ciaWeight;
            this.eseWeight = eseWeight;
            this.eseMinimum = eseMinimum;
        }
    }

    public record MarkSheet(String courseCode, String title, double credits, String ciaScores,
                            String internalScore, String eseScore, String externalScore,
                            String total, String grade, String status, int gradePoint) {
    }

    public record AttendanceDefaulter(String rollNumber, String name, String department,
                                      String courseCode, String title, double attendancePercent) {
    }

    public record DepartmentSummary(String department, int passed, int failed, double averageSgpa) {
    }

    public void addCourse(String courseCode, String title, double credits, CourseType type) throws SQLException {
        if (courseCode.isBlank() || title.isBlank() || credits <= 0 || !Double.isFinite(credits) || type == null) {
            throw new IllegalArgumentException("Course code, title, and positive credits are required.");
        }
        String sql = "INSERT INTO courses (course_code, title, credits, course_type) VALUES (?, ?, ?, ?)";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, courseCode.trim().toUpperCase(Locale.ROOT));
            statement.setString(2, title.trim());
            statement.setDouble(3, credits);
            statement.setString(4, type.name());
            statement.executeUpdate();
        }
    }

    public void enrollStudent(String rollNumber, String courseCode, int semester) throws SQLException {
        if (semester < 1 || semester > 8) {
            throw new IllegalArgumentException("Semester must be between 1 and 8.");
        }
        String sql = "INSERT INTO enrollments (roll_number, course_code, semester) VALUES (?, ?, ?)";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rollNumber.trim().toUpperCase(Locale.ROOT));
            statement.setString(2, courseCode.trim().toUpperCase(Locale.ROOT));
            statement.setInt(3, semester);
            statement.executeUpdate();
        }
    }

    public void recordAttendance(String rollNumber, String courseCode, int semester,
                                 int classesHeld, int classesAttended) throws SQLException {
        if (classesHeld <= 0 || classesAttended < 0 || classesAttended > classesHeld) {
            throw new IllegalArgumentException("Held classes must be positive; attended must be from 0 through held.");
        }
        String sql = "UPDATE enrollments SET total_classes = total_classes + ?, " +
                "attended_classes = attended_classes + ? WHERE roll_number = ? AND course_code = ? AND semester = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, classesHeld);
            statement.setInt(2, classesAttended);
            statement.setString(3, rollNumber.trim().toUpperCase(Locale.ROOT));
            statement.setString(4, courseCode.trim().toUpperCase(Locale.ROOT));
            statement.setInt(5, semester);
            requireUpdated(statement.executeUpdate(), "Enrollment not found.");
        }
    }

    public void approveMedicalCondonation(String rollNumber, String courseCode, int semester) throws SQLException {
        String sql = "UPDATE enrollments SET medical_condonation_approved = 1 " +
                "WHERE roll_number = ? AND course_code = ? AND semester = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rollNumber.trim().toUpperCase());
            statement.setString(2, courseCode.trim().toUpperCase());
            statement.setInt(3, semester);
            requireUpdated(statement.executeUpdate(), "Enrollment not found.");
        }
    }

    public void recordCiaMark(String rollNumber, String courseCode, int semester,
                              int component, double mark) throws SQLException {
        validateMark(mark);
        if (component < 1 || component > 3) {
            throw new IllegalArgumentException("CIA component must be 1 (test 1), 2 (test 2), or 3 (assignment).");
        }
        String column = switch (component) {
            case 1 -> "cia_test1";
            case 2 -> "cia_test2";
            default -> "assignment";
        };
        upsertMark(rollNumber, courseCode, semester, column, mark);
    }

    public void recordEseMark(String rollNumber, String courseCode, int semester,
                              Double mark, boolean absent) throws SQLException {
        if (absent && mark != null) {
            throw new IllegalArgumentException("An absent student cannot also have an ESE mark.");
        }
        if (!absent) {
            if (mark == null) {
                throw new IllegalArgumentException("Enter an ESE mark or mark the student absent.");
            }
            validateMark(mark);
        }
        String query = "SELECT total_classes, attended_classes, medical_condonation_approved " +
                "FROM enrollments WHERE roll_number = ? AND course_code = ? AND semester = ?";
        try (Connection connection = DBConnection.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                setEnrollmentKey(statement, rollNumber, courseCode, semester);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new IllegalArgumentException("Enrollment not found.");
                    }
                    int totalClasses = result.getInt("total_classes");
                    int attendedClasses = result.getInt("attended_classes");
                    double attendance = totalClasses == 0 ? 0 : attendedClasses * 100.0 / totalClasses;
                    if (attendance < 65) {
                        throw new IllegalArgumentException("Attendance below 65%; student is barred from ESE.");
                    }
                    if (attendance < 75 && result.getInt("medical_condonation_approved") != 1) {
                        throw new IllegalArgumentException("Attendance is 65% to below 75%; medical condonation is required.");
                    }
                }
            }
            int enrollmentId = findEnrollmentId(connection, rollNumber, courseCode, semester);
            String sql = "INSERT INTO assessment_marks (enrollment_id, ese_mark, ese_absent) VALUES (?, ?, ?) " +
                    "ON CONFLICT(enrollment_id) DO UPDATE SET ese_mark = excluded.ese_mark, ese_absent = excluded.ese_absent";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, enrollmentId);
                if (absent) {
                    statement.setNull(2, java.sql.Types.REAL);
                } else {
                    statement.setDouble(2, mark);
                }
                statement.setInt(3, absent ? 1 : 0);
                statement.executeUpdate();
            }
        }
    }

    public List<MarkSheet> getMarkSheet(String rollNumber, int semester) throws SQLException {
        String sql = "SELECT c.course_code, c.title, c.credits, c.course_type, e.total_classes, " +
                "e.attended_classes, e.medical_condonation_approved, m.cia_test1, m.cia_test2, " +
                "m.assignment, m.ese_mark, m.ese_absent FROM enrollments e " +
                "JOIN courses c ON c.course_code = e.course_code " +
                "LEFT JOIN assessment_marks m ON m.enrollment_id = e.enrollment_id " +
                "WHERE e.roll_number = ? AND e.semester = ? ORDER BY c.course_code";
        List<MarkSheet> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rollNumber.trim().toUpperCase(Locale.ROOT));
            statement.setInt(2, semester);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(evaluate(result));
                }
            }
        }
        return rows;
    }

    public List<AttendanceDefaulter> getAttendanceDefaulters(int semester) throws SQLException {
        String sql = "SELECT s.roll_number, s.name, s.department, c.course_code, c.title, " +
                "e.attended_classes * 100.0 / e.total_classes AS attendance_percent " +
                "FROM enrollments e JOIN students s ON s.roll_number = e.roll_number " +
                "JOIN courses c ON c.course_code = e.course_code " +
                "WHERE e.semester = ? AND e.total_classes > 0 " +
                "AND e.attended_classes * 100.0 / e.total_classes < 65 " +
                "ORDER BY s.department, s.roll_number, c.course_code";
        List<AttendanceDefaulter> rows = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, semester);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new AttendanceDefaulter(result.getString("roll_number"), result.getString("name"),
                            result.getString("department"), result.getString("course_code"),
                            result.getString("title"), result.getDouble("attendance_percent")));
                }
            }
        }
        return rows;
    }

    public List<DepartmentSummary> getDepartmentSummary(int semester) throws SQLException {
        String sql = "SELECT DISTINCT s.roll_number, s.department FROM students s " +
                "JOIN enrollments e ON e.roll_number = s.roll_number WHERE e.semester = ? " +
                "ORDER BY s.department, s.roll_number";
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, double[]> sgpas = new LinkedHashMap<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, semester);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String rollNumber = result.getString("roll_number");
                    String department = result.getString("department");
                    List<MarkSheet> marks = getMarkSheet(rollNumber, semester);
                    if (marks.isEmpty()) {
                        continue;
                    }
                    boolean pending = marks.stream().anyMatch(row -> row.status().equals("INCOMPLETE") ||
                            row.status().equals("CONDONATION REQUIRED"));
                    if (pending) {
                        continue;
                    }
                    boolean passed = marks.stream().allMatch(row -> row.gradePoint() > 0);
                    counts.computeIfAbsent(department, ignored -> new int[2]);
                    int[] departmentCounts = counts.get(department);
                    departmentCounts[passed ? 0 : 1]++;
                    double sgpa = calculateSgpa(marks);
                    double[] aggregate = sgpas.computeIfAbsent(department, ignored -> new double[2]);
                    aggregate[0] += sgpa;
                    aggregate[1]++;
                }
            }
        }
        List<DepartmentSummary> summary = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            double[] aggregate = sgpas.get(entry.getKey());
            double average = aggregate == null || aggregate[1] == 0 ? 0 : aggregate[0] / aggregate[1];
            summary.add(new DepartmentSummary(entry.getKey(), entry.getValue()[0], entry.getValue()[1], average));
        }
        return summary;
    }

    public static double calculateSgpa(List<MarkSheet> marks) {
        double creditPoints = 0;
        double credits = 0;
        for (MarkSheet mark : marks) {
            creditPoints += mark.credits() * mark.gradePoint();
            credits += mark.credits();
        }
        return credits == 0 ? 0 : creditPoints / credits;
    }

    private static MarkSheet evaluate(ResultSet result) throws SQLException {
        String courseCode = result.getString("course_code");
        String title = result.getString("title");
        double credits = result.getDouble("credits");
        CourseType type = CourseType.valueOf(result.getString("course_type"));
        int totalClasses = result.getInt("total_classes");
        int attendedClasses = result.getInt("attended_classes");
        double attendance = totalClasses == 0 ? 0 : attendedClasses * 100.0 / totalClasses;
        boolean condonationApproved = result.getInt("medical_condonation_approved") == 1;
        Double cia1 = nullableDouble(result, "cia_test1");
        Double cia2 = nullableDouble(result, "cia_test2");
        Double assignment = nullableDouble(result, "assignment");
        Double ese = nullableDouble(result, "ese_mark");
        boolean absent = result.getInt("ese_absent") == 1;
        String ciaScores = formatNullable(cia1) + " / " + formatNullable(cia2) + " / " + formatNullable(assignment);

        if (totalClasses == 0) {
            return new MarkSheet(courseCode, title, credits, ciaScores, "-", formatNullable(ese), "-", "-", "-",
                "INCOMPLETE", 0);
        }
        if (attendance < 65) {
            return new MarkSheet(courseCode, title, credits, ciaScores, "-", "-", "-", "-", "SA",
                    "SHORTAGE OF ATTENDANCE", 0);
        }
        if (attendance < 75 && !condonationApproved) {
            return new MarkSheet(courseCode, title, credits, ciaScores, "-", formatNullable(ese), "-", "-", "-",
                    "CONDONATION REQUIRED", 0);
        }
        if (cia1 == null || cia2 == null || assignment == null) {
            return new MarkSheet(courseCode, title, credits, ciaScores, "-", formatNullable(ese), "-", "-", "-",
                    "INCOMPLETE", 0);
        }
        if (absent) {
            return new MarkSheet(courseCode, title, credits, ciaScores,
                    format(type.ciaWeight * ((cia1 + cia2 + assignment) / 3) / 100), "AB", "0.00", "-", "AB",
                    "ABSENT IN ESE", 0);
        }
        if (ese == null) {
            return new MarkSheet(courseCode, title, credits, ciaScores,
                    format(type.ciaWeight * ((cia1 + cia2 + assignment) / 3) / 100), "-", "-", "-", "-",
                    "INCOMPLETE", 0);
        }
        double internal = type.ciaWeight * ((cia1 + cia2 + assignment) / 3) / 100;
        double external = type.eseWeight * ese / 100;
        double total = internal + external;
        boolean passed = total >= 50 && external >= type.eseMinimum;
        String grade = passed ? gradeFor(total) : "RA";
        int gradePoint = passed ? gradePointFor(total) : 0;
        return new MarkSheet(courseCode, title, credits, ciaScores, format(internal), format(ese),
                format(external), format(total), grade, passed ? "PASS" : "FAIL", gradePoint);
    }

    private static String gradeFor(double total) {
        if (total >= 91) return "O";
        if (total >= 81) return "A+";
        if (total >= 71) return "A";
        if (total >= 61) return "B+";
        if (total >= 50) return "B";
        return "RA";
    }

    private static int gradePointFor(double total) {
        if (total >= 91) return 10;
        if (total >= 81) return 9;
        if (total >= 71) return 8;
        if (total >= 61) return 7;
        return 6;
    }

    private static void upsertMark(String rollNumber, String courseCode, int semester,
                                   String column, double mark) throws SQLException {
        String sql = "INSERT INTO assessment_marks (enrollment_id, " + column + ") VALUES (?, ?) " +
                "ON CONFLICT(enrollment_id) DO UPDATE SET " + column + " = excluded." + column;
        try (Connection connection = DBConnection.getConnection()) {
            int enrollmentId = findEnrollmentId(connection, rollNumber, courseCode, semester);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, enrollmentId);
                statement.setDouble(2, mark);
                statement.executeUpdate();
            }
        }
    }

    private static int findEnrollmentId(Connection connection, String rollNumber, String courseCode,
                                        int semester) throws SQLException {
        String sql = "SELECT enrollment_id FROM enrollments WHERE roll_number = ? AND course_code = ? AND semester = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setEnrollmentKey(statement, rollNumber, courseCode, semester);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalArgumentException("Enrollment not found.");
                }
                return result.getInt(1);
            }
        }
    }

    private static void setEnrollmentKey(PreparedStatement statement, String rollNumber,
                                         String courseCode, int semester) throws SQLException {
        statement.setString(1, rollNumber.trim().toUpperCase(Locale.ROOT));
        statement.setString(2, courseCode.trim().toUpperCase(Locale.ROOT));
        statement.setInt(3, semester);
    }

    private static void validateMark(double mark) {
        if (!Double.isFinite(mark) || mark < 0 || mark > 100) {
            throw new IllegalArgumentException("Marks must be between 0 and 100.");
        }
    }

    private static void requireUpdated(int updatedRows, String message) {
        if (updatedRows == 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Double nullableDouble(ResultSet result, String column) throws SQLException {
        double value = result.getDouble(column);
        return result.wasNull() ? null : value;
    }

    private static String formatNullable(Double value) {
        return value == null ? "-" : format(value);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}