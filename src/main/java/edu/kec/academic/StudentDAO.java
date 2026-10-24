package edu.kec.academic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;

public class StudentDAO {
    public void addStudent(String rollNumber, String name, String department, int currentSemester) throws SQLException {
        if (rollNumber.isBlank() || name.isBlank() || department.isBlank()) {
            throw new IllegalArgumentException("Roll number, name, and department are required.");
        }
        if (currentSemester < 1 || currentSemester > 8) {
            throw new IllegalArgumentException("Semester must be between 1 and 8.");
        }
        String sql = "INSERT INTO students (roll_number, name, department, current_semester) VALUES (?, ?, ?, ?)";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rollNumber.trim().toUpperCase(Locale.ROOT));
            statement.setString(2, name.trim());
            statement.setString(3, department.trim().toUpperCase(Locale.ROOT));
            statement.setInt(4, currentSemester);
            statement.executeUpdate();
        }
    }
}