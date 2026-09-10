package com.jacobhastert.downtime.service;

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
import com.jacobhastert.downtime.repository.SqliteEquipmentRepository;
import com.jacobhastert.downtime.repository.SqliteIncidentRepository;

/**
 * Verifies incident persistence through the service and preservation
 * of existing state when database writes fail.
 */
class IncidentServicePersistenceTest {

    private static final Instant START =
            Instant.parse("2026-09-09T10:15:00Z");

    @TempDir
    Path temporaryDirectory;

    private Path databaseFile;
    private IncidentService service;

    @BeforeEach
    void setUp() throws SQLException {
        databaseFile = temporaryDirectory.resolve("downtime.db");

        EquipmentService equipmentService = new EquipmentService(
                new SqliteEquipmentRepository(databaseFile));

        equipmentService.addEquipment(new Equipment(
                "AMR-001", "Warehouse Robot One", "AMR", "Training Area"));

        service = new IncidentService(
                equipmentService,
                new SqliteIncidentRepository(databaseFile));
    }

    private IncidentService reopenService() throws SQLException {
        EquipmentService equipmentService = new EquipmentService(
                new SqliteEquipmentRepository(databaseFile));

        return new IncidentService(
                equipmentService,
                new SqliteIncidentRepository(databaseFile));
    }

    @Test
    void activeIncidentSurvivesRestartAndBlocksAnother()
            throws SQLException {

        Incident incident = service.openIncident(
                "AMR-001", "Navigation fault", START);

        service.startWork(incident.getId());

        IncidentService reopened = reopenService();

        assertEquals(
                IncidentStatus.IN_PROGRESS,
                reopened.getAllIncidents().get(0).getStatus());

        assertThrows(IllegalStateException.class, () ->
                reopened.openIncident(
                        "AMR-001", "Sensor fault", START.plusSeconds(10)));
    }

    @Test
    void resolvedIncidentRetainsNotesAndDowntimeAfterRestart()
            throws SQLException {

        Incident incident = service.openIncident(
                "AMR-001", "Navigation fault", START);

        service.resolveIncident(
                incident.getId(),
                START.plus(Duration.ofMinutes(27)),
                "Cleared obstruction.");

        IncidentService reopened = reopenService();
        Incident loaded = reopened.getAllIncidents().get(0);

        assertEquals(IncidentStatus.RESOLVED, loaded.getStatus());
        assertEquals("Cleared obstruction.", loaded.getResolutionNotes());
        assertEquals(Duration.ofMinutes(27), loaded.getDowntime());

        assertDoesNotThrow(() ->
                reopened.openIncident(
                        "AMR-001",
                        "New sensor fault",
                        START.plus(Duration.ofHours(1))));
    }

    @Test
    void failedResolutionSavePreservesMemoryAndDatabase()
            throws SQLException {

        Incident incident = service.openIncident(
                "AMR-001", "Navigation fault", START);

        // A test-only repository simulates a write failure consistently.
        SqliteIncidentRepository failingRepository =
                new SqliteIncidentRepository(databaseFile) {
                    @Override
                    public void save(Incident value) throws SQLException {
                        throw new SQLException("Simulated write failure.");
                    }
                };

        EquipmentService equipmentService = new EquipmentService(
                new SqliteEquipmentRepository(databaseFile));

        IncidentService failingService = new IncidentService(
                equipmentService, failingRepository);

        assertThrows(IllegalStateException.class, () ->
                failingService.resolveIncident(
                        incident.getId(),
                        START.plusSeconds(60),
                        "Reset controller."));

        Incident inMemory = failingService.getAllIncidents().get(0);

        assertEquals(IncidentStatus.OPEN, inMemory.getStatus());
        assertNull(inMemory.getResolvedAt());
        assertNull(inMemory.getResolutionNotes());

        Incident stored = reopenService().getAllIncidents().get(0);
        assertEquals(IncidentStatus.OPEN, stored.getStatus());
    }

    @Test
    void changingReturnedSnapshotDoesNotChangeStoredIncident()
            throws SQLException {

        Incident returned = service.openIncident(
                "AMR-001", "Navigation fault", START);

        returned.resolve(START.plusSeconds(60), "Changed outside service.");

        assertEquals(
                IncidentStatus.OPEN,
                service.getAllIncidents().get(0).getStatus());

        assertEquals(
                IncidentStatus.OPEN,
                reopenService().getAllIncidents().get(0).getStatus());
    }
    
    @Test
    void previousRestorationStillRestrictsStartTimeAfterRestart()
            throws SQLException {

        Incident first = service.openIncident(
                "AMR-001", "Navigation fault", START);

        service.resolveIncident(
                first.getId(),
                START.plusSeconds(120),
                "Cleared obstruction.");

        IncidentService reopened = reopenService();

        assertThrows(IllegalArgumentException.class, () ->
                reopened.openIncident(
                        "AMR-001",
                        "Sensor fault",
                        START.plusSeconds(60)));

        // Confirm rejection did not add a record to persistent storage.
        assertEquals(1, reopenService().getAllIncidents().size());
    }
}