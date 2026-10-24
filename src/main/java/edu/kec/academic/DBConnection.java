package edu.kec.academic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DBConnection {
    private static final String URL = System.getProperty("kec.db.url", "jdbc:sqlite:kec_academic.db");

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("SQLite JDBC driver is missing. Run the application with Maven.", exception);
        }
        Connection connection = DriverManager.getConnection(URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public static void initializeDatabase() throws SQLException {
        try (InputStream input = DBConnection.class.getResourceAsStream("/kec_schema.sql")) {
            if (input == null) {
                throw new SQLException("kec_schema.sql was not found on the application classpath.");
            }
            StringBuilder sql = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                 Connection connection = getConnection();
                 Statement statement = connection.createStatement()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sql.append(line).append('\n');
                }
                for (String command : sql.toString().split(";")) {
                    if (!command.isBlank()) {
                        statement.execute(command);
                    }
                }
            } catch (IOException exception) {
                throw new SQLException("Could not read kec_schema.sql.", exception);
            }
        } catch (IOException exception) {
            throw new SQLException("Could not close kec_schema.sql.", exception);
        }
    }
}