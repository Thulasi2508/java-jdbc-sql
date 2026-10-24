package edu.kec.academic;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class MainApp {
    private final Scanner scanner = new Scanner(System.in);
    private final StudentDAO studentDAO = new StudentDAO();
    private final AcademicDAO academicDAO = new AcademicDAO();

    public static void main(String[] args) {
        try {
            DBConnection.initializeDatabase();
            new MainApp().run();
        } catch (SQLException exception) {
            System.err.println("Could not start the academic system: " + exception.getMessage());
        }
    }

    private void run() {
        while (true) {
            printMenu();
            String choice = prompt("Select an option");
            try {
                switch (choice) {
                    case "1" -> addStudent();
                    case "2" -> addCourse();
                    case "3" -> enrollStudent();
                    case "4" -> recordAttendance();
                    case "5" -> recordCiaMark();
                    case "6" -> approveCondonation();
                    case "7" -> recordEseMark();
                    case "8" -> printMarkSheet();
                    case "9" -> printAttendanceDefaulters();
                    case "10" -> printDepartmentSummary();
                    case "0" -> {
                        System.out.println("Goodbye.");
                        return;
                    }
                    default -> System.out.println("Choose a listed option.");
                }
            } catch (SQLException | IllegalArgumentException exception) {
                System.out.println("Error: " + exception.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println("\nKEC R2024 Student Academic & Continuous Evaluation System");
        System.out.println("1. Register student  2. Add course  3. Enroll student");
        System.out.println("4. Record attendance  5. Enter CIA mark  6. Approve medical condonation");
        System.out.println("7. Enter ESE mark / absence  8. Student semester mark sheet");
        System.out.println("9. Attendance defaulters  10. Department result summary  0. Exit");
    }

    private void addStudent() throws SQLException {
        studentDAO.addStudent(prompt("Roll number"), prompt("Name"), prompt("Department"),
                readInt("Current semester"));
        System.out.println("Student registered.");
    }

    private void addCourse() throws SQLException {
        String code = prompt("Course code");
        String title = prompt("Course title");
        double credits = readDouble("Credits");
        AcademicDAO.CourseType type = AcademicDAO.CourseType.valueOf(
                prompt("Course type (THEORY, PRACTICAL, EMBEDDED)").toUpperCase(Locale.ROOT));
        academicDAO.addCourse(code, title, credits, type);
        System.out.println("Course added.");
    }

    private void enrollStudent() throws SQLException {
        academicDAO.enrollStudent(prompt("Roll number"), prompt("Course code"), readInt("Semester"));
        System.out.println("Student enrolled.");
    }

    private void recordAttendance() throws SQLException {
        String roll = prompt("Roll number");
        String course = prompt("Course code");
        int semester = readInt("Semester");
        int held = readInt("Classes held in this update");
        int attended = readInt("Classes attended in this update");
        academicDAO.recordAttendance(roll, course, semester, held, attended);
        System.out.println("Attendance updated.");
    }

    private void recordCiaMark() throws SQLException {
        String roll = prompt("Roll number");
        String course = prompt("Course code");
        int semester = readInt("Semester");
        int component = readInt("Component (1 = CIA Test 1, 2 = CIA Test 2, 3 = Assignment / Quiz / Lab)");
        academicDAO.recordCiaMark(roll, course, semester, component, readDouble("Mark (0-100)"));
        System.out.println("CIA mark saved.");
    }

    private void approveCondonation() throws SQLException {
        academicDAO.approveMedicalCondonation(prompt("Roll number"), prompt("Course code"), readInt("Semester"));
        System.out.println("Medical condonation approval recorded.");
    }

    private void recordEseMark() throws SQLException {
        String roll = prompt("Roll number");
        String course = prompt("Course code");
        int semester = readInt("Semester");
        boolean absent = prompt("Absent in ESE? (y/n)").equalsIgnoreCase("y");
        Double mark = absent ? null : readDouble("ESE mark (0-100)");
        academicDAO.recordEseMark(roll, course, semester, mark, absent);
        System.out.println("ESE result saved.");
    }

    private void printMarkSheet() throws SQLException {
        String roll = prompt("Roll number");
        int semester = readInt("Semester");
        List<AcademicDAO.MarkSheet> rows = academicDAO.getMarkSheet(roll, semester);
        if (rows.isEmpty()) {
            System.out.println("No course enrollments found.");
            return;
        }
        System.out.printf("\nMark sheet: %s, Semester %d%n", roll.toUpperCase(Locale.ROOT), semester);
        System.out.printf("%-12s %-24s %-19s %-9s %-8s %-9s %-8s %-5s %-22s%n",
                "Course", "Title", "CIA1 / CIA2 / Asg", "Internal", "ESE", "External", "Total", "Grade", "Status");
        for (AcademicDAO.MarkSheet row : rows) {
            System.out.printf("%-12s %-24s %-19s %-9s %-8s %-9s %-8s %-5s %-22s%n",
                    row.courseCode(), shorten(row.title(), 24), row.ciaScores(), row.internalScore(), row.eseScore(),
                    row.externalScore(), row.total(), row.grade(), row.status());
        }
        boolean pending = rows.stream().anyMatch(row -> row.status().equals("INCOMPLETE") ||
                row.status().equals("CONDONATION REQUIRED"));
        System.out.println(pending ? "SGPA: PENDING (assessment or eligibility unresolved)" :
                String.format(Locale.ROOT, "SGPA: %.2f | Equivalent percentage: %.2f%%",
                        AcademicDAO.calculateSgpa(rows), AcademicDAO.calculateSgpa(rows) * 10));
    }

    private void printAttendanceDefaulters() throws SQLException {
        int semester = readInt("Semester");
        List<AcademicDAO.AttendanceDefaulter> rows = academicDAO.getAttendanceDefaulters(semester);
        System.out.println("\nAttendance defaulters (below 65%, grade SA)");
        System.out.printf("%-12s %-22s %-12s %-12s %-9s%n", "Roll", "Name", "Department", "Course", "Attendance");
        for (AcademicDAO.AttendanceDefaulter row : rows) {
            System.out.printf("%-12s %-22s %-12s %-12s %.2f%%%n", row.rollNumber(), shorten(row.name(), 22),
                    row.department(), row.courseCode(), row.attendancePercent());
        }
        if (rows.isEmpty()) System.out.println("No attendance defaulters found.");
    }

    private void printDepartmentSummary() throws SQLException {
        int semester = readInt("Semester");
        List<AcademicDAO.DepartmentSummary> rows = academicDAO.getDepartmentSummary(semester);
        System.out.println("\nDepartment result summary (students with unresolved results are excluded)");
        System.out.printf("%-16s %-8s %-8s %-12s%n", "Department", "Passed", "Failed", "Average SGPA");
        for (AcademicDAO.DepartmentSummary row : rows) {
            System.out.printf("%-16s %-8d %-8d %.2f%n", row.department(), row.passed(), row.failed(), row.averageSgpa());
        }
        if (rows.isEmpty()) System.out.println("No finalized semester results found.");
    }

    private String prompt(String label) {
        System.out.print(label + ": ");
        return scanner.nextLine().trim();
    }

    private int readInt(String label) {
        return Integer.parseInt(prompt(label));
    }

    private double readDouble(String label) {
        return Double.parseDouble(prompt(label));
    }

    private static String shorten(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }
}