package com.jacobhastert.downtime.repository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.jacobhastert.downtime.model.Incident;
import com.jacobhastert.downtime.model.IncidentStatus;

/**
 * Stores incidents and reconstructs their lifecycle state from SQLite.
 *
 * <p>The equipment table must be initialized before this repository is used.
 * Each operation opens and closes its own database connection.
 */
public class SqliteIncidentRepository {

    private final String databaseUrl;

    /**
     * Initializes incident storage in an existing application database.
     *
     * @param databaseFile database shared with equipment storage
     * @throws SQLException if initialization fails
     */
    public SqliteIncidentRepository(Path databaseFile) throws SQLException {
        Objects.requireNonNull(databaseFile, "Database path is required.");

        databaseUrl = "jdbc:sqlite:"
                + databaseFile.toAbsolutePath().normalize();

        initializeSchema();
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(databaseUrl);

        try {
            // SQLite foreign-key enforcement must be enabled per connection.
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    private void initializeSchema() throws SQLException {
        String tableSql = """
                CREATE TABLE IF NOT EXISTS incidents (
                    id TEXT NOT NULL PRIMARY KEY,
                    equipment_id TEXT NOT NULL,
                    description TEXT NOT NULL,
                    started_at TEXT NOT NULL,
                    status TEXT NOT NULL
                        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
                    resolved_at TEXT,
                    resolution_notes TEXT,
                    FOREIGN KEY (equipment_id) REFERENCES equipment(id),
                    CHECK (
                        (status = 'RESOLVED'
                            AND resolved_at IS NOT NULL
                            AND resolution_notes IS NOT NULL)
                        OR
                        (status IN ('OPEN', 'IN_PROGRESS')
                            AND resolved_at IS NULL
                            AND resolution_notes IS NULL)
                    )
                )
                """;

        String indexSql = """
                CREATE UNIQUE INDEX IF NOT EXISTS one_active_incident
                ON incidents (equipment_id)
                WHERE status IN ('OPEN', 'IN_PROGRESS')
                """;

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(tableSql);

            // Enforce the active-incident rule even outside the Java service.
            statement.executeUpdate(indexSql);
        }
    }

    /**
     * Inserts an incident or saves the current state of an existing incident.
     *
     * @param incident incident to persist
     * @throws SQLException if database constraints or storage prevent saving
     */
    public void save(Incident incident) throws SQLException {
        Objects.requireNonNull(incident, "Incident is required.");

        String sql = """
                INSERT INTO incidents (
                    id, equipment_id, description, started_at,
                    status, resolved_at, resolution_notes
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    status = excluded.status,
                    resolved_at = excluded.resolved_at,
                    resolution_notes = excluded.resolution_notes
                """;

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, incident.getId());
            statement.setString(2, incident.getEquipmentId());
            statement.setString(3, incident.getDescription());
            statement.setString(4, incident.getStartedAt().toString());
            statement.setString(5, incident.getStatus().name());

            // ISO-8601 timestamps preserve the exact instant, including
            // fractional seconds, without depending on the local time zone.
            statement.setString(
                    6,
                    incident.getResolvedAt() == null
                            ? null : incident.getResolvedAt().toString());

            statement.setString(7, incident.getResolutionNotes());

            statement.executeUpdate();
        }
    }

    /**
     * Loads incidents in a deterministic order.
     *
     * @return stored incidents, or an empty list
     * @throws SQLException if records cannot be read or reconstructed
     */
    public List<Incident> findAll() throws SQLException {
        String sql = """
                SELECT id, equipment_id, description, started_at,
                       status, resolved_at, resolution_notes
                FROM incidents
                ORDER BY id
                """;

        List<Incident> incidents = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {

            while (results.next()) {
                incidents.add(readIncident(results));
            }
        }

        return incidents;
    }

    private Incident readIncident(ResultSet results) throws SQLException {
        try {
            Incident incident = new Incident(
                    results.getString("id"),
                    results.getString("equipment_id"),
                    results.getString("description"),
                    Instant.parse(results.getString("started_at")));

            IncidentStatus status =
                    IncidentStatus.valueOf(results.getString("status"));

            // Reuse the model's transition rules instead of exposing setters
            // that could bypass validation.
            if (status == IncidentStatus.IN_PROGRESS) {
                incident.startWork();
            } else if (status == IncidentStatus.RESOLVED) {
                incident.resolve(
                        Instant.parse(results.getString("resolved_at")),
                        results.getString("resolution_notes"));
            }

            return incident;
        } catch (RuntimeException exception) {
            throw new SQLException(
                    "Stored incident contains invalid data.", exception);
        }
    }
}