package com.jacobhastert.downtime.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jacobhastert.downtime.model.Equipment;
import com.jacobhastert.downtime.model.Incident;
import com.jacobhastert.downtime.model.IncidentStatus;

/**
 * Verifies incident persistence and database-enforced relationships.
 */
class SqliteIncidentRepositoryTest {

    private static final Instant START =
            Instant.parse("2026-09-09T10:15:00Z");

    @TempDir
    Path temporaryDirectory;

    private Path databaseFile;
    private SqliteIncidentRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        databaseFile = temporaryDirectory.resolve("downtime.db");

        SqliteEquipmentRepository equipmentRepository =
                new SqliteEquipmentRepository(databaseFile);

        equipmentRepository.save(new Equipment(
                "AMR-001", "Warehouse Robot One", "AMR", "Training Area"));

        repository = new SqliteIncidentRepository(databaseFile);
    }

    @Test
    void openIncidentSurvivesReopening() throws SQLException {
        repository.save(new Incident(
                "INC-001", "AMR-001", "Navigation fault", START));

        SqliteIncidentRepository reopened =
                new SqliteIncidentRepository(databaseFile);

        assertEquals(1, reopened.findAll().size());

        Incident loaded = reopened.findAll().get(0);

        assertEquals("INC-001", loaded.getId());
        assertEquals("AMR-001", loaded.getEquipmentId());
        assertEquals("Navigation fault", loaded.getDescription());
        assertEquals(START, loaded.getStartedAt());
        assertEquals(IncidentStatus.OPEN, loaded.getStatus());
        assertNull(loaded.getResolvedAt());
    }

    @Test
    void inProgressStatusSurvivesReopening() throws SQLException {
        Incident incident = new Incident(
                "INC-001", "AMR-001", "Navigation fault", START);

        repository.save(incident);

        incident.startWork();
        repository.save(incident);

        SqliteIncidentRepository reopened =
                new SqliteIncidentRepository(databaseFile);

        assertEquals(1, reopened.findAll().size());
        assertEquals(
                IncidentStatus.IN_PROGRESS,
                reopened.findAll().get(0).getStatus());
    }

    @Test
    void resolutionAndDowntimeSurviveReopening() throws SQLException {
        Incident incident = new Incident(
                "INC-001", "AMR-001", "Navigation fault", START);

        repository.save(incident);

        Instant restoredAt = START.plus(Duration.ofMinutes(27));

        incident.resolve(restoredAt, "Cleared obstruction.");
        repository.save(incident);

        SqliteIncidentRepository reopened =
                new SqliteIncidentRepository(databaseFile);

        assertEquals(1, reopened.findAll().size());

        Incident loaded = reopened.findAll().get(0);

        assertEquals(IncidentStatus.RESOLVED, loaded.getStatus());
        assertEquals(restoredAt, loaded.getResolvedAt());
        assertEquals("Cleared obstruction.", loaded.getResolutionNotes());
        assertEquals(Duration.ofMinutes(27), loaded.getDowntime());
    }

    @Test
    void databaseRejectsIncidentForUnknownEquipment() {
        Incident incident = new Incident(
                "INC-001", "UNKNOWN", "Sensor fault", START);

        assertThrows(SQLException.class, () -> repository.save(incident));
    }

    @Test
    void databaseRejectsSecondActiveIncident() throws SQLException {
        repository.save(new Incident(
                "INC-001", "AMR-001", "Navigation fault", START));

        Incident second = new Incident(
                "INC-002", "AMR-001", "Sensor fault", START.plusSeconds(10));

        assertThrows(SQLException.class, () -> repository.save(second));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void resolvingIncidentAllowsAnotherForSameEquipment()
            throws SQLException {

        Incident first = new Incident(
                "INC-001", "AMR-001", "Navigation fault", START);

        repository.save(first);

        first.resolve(START.plusSeconds(60), "Cleared obstruction.");
        repository.save(first);

        repository.save(new Incident(
                "INC-002",
                "AMR-001",
                "Sensor fault",
                START.plusSeconds(120)));

        assertEquals(2, repository.findAll().size());
    }
}