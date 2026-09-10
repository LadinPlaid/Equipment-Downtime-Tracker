package com.jacobhastert.downtime.repository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.jacobhastert.downtime.model.Equipment;

/**
 * Stores equipment records in a local SQLite database.
 *
 * Each operation opens and closes its own connection. The database
 * file remains available after the application exits.
 */
public class SqliteEquipmentRepository {

    private final String databaseUrl;

    /**
     * Opens or creates a database and initializes the equipment table.
     *
     * @param databaseFile database file in an existing directory
     * @throws SQLException if the database cannot be initialized
     */
    public SqliteEquipmentRepository(Path databaseFile) throws SQLException {
        Objects.requireNonNull(databaseFile, "Database path is required.");

        databaseUrl = "jdbc:sqlite:"
                + databaseFile.toAbsolutePath().normalize();

        initializeSchema();
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(databaseUrl);
    }

    private void initializeSchema() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS equipment (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    location TEXT NOT NULL
                )
                """;

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    /**
     * Inserts a new equipment record without replacing existing data.
     *
     * @param equipment validated equipment to store
     * @throws SQLException if an ID already exists or the write fails
     */
    public void save(Equipment equipment) throws SQLException {
        Objects.requireNonNull(equipment, "Equipment is required.");

        String sql = """
                INSERT INTO equipment (id, name, type, location)
                VALUES (?, ?, ?, ?)
                """;

        // Parameters keep equipment values separate from SQL instructions.
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, equipment.getId());
            statement.setString(2, equipment.getName());
            statement.setString(3, equipment.getType());
            statement.setString(4, equipment.getLocation());

            statement.executeUpdate();
        }
    }

    /**
     * Retrieves equipment sorted by identifier.
     *
     * @return stored equipment, or an empty list when no records exist
     * @throws SQLException if the database cannot be read
     */
    public List<Equipment> findAll() throws SQLException {
        String sql = """
                SELECT id, name, type, location
                FROM equipment
                ORDER BY id
                """;

        List<Equipment> equipment = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {

            while (results.next()) {
                equipment.add(new Equipment(
                        results.getString("id"),
                        results.getString("name"),
                        results.getString("type"),
                        results.getString("location")));
            }
        }

        return equipment;
    }
}