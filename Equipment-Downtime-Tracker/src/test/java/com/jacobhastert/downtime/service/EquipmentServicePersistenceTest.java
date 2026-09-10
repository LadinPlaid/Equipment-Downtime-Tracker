package com.jacobhastert.downtime.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jacobhastert.downtime.model.Equipment;
import com.jacobhastert.downtime.repository.SqliteEquipmentRepository;

/**
 * Verifies that service operations persist equipment and restore
 * registration rules when the application is reopened.
 */
class EquipmentServicePersistenceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void registeredEquipmentIsLoadedByANewService() throws SQLException {
        Path databaseFile = temporaryDirectory.resolve("downtime.db");

        EquipmentService original = new EquipmentService(
                new SqliteEquipmentRepository(databaseFile));

        original.addEquipment(new Equipment(
                "AMR-001",
                "Warehouse Robot One",
                "AMR",
                "Training Area"));

        // Recreate both objects to represent a fresh application startup.
        EquipmentService reopened = new EquipmentService(
                new SqliteEquipmentRepository(databaseFile));

        assertTrue(reopened.containsEquipment("AMR-001"));
        assertEquals(1, reopened.getAllEquipment().size());
        assertEquals(
                "Warehouse Robot One",
                reopened.getAllEquipment().get(0).getName());
    }

    @Test
    void duplicateIdIsRejectedAfterReopening() throws SQLException {
        Path databaseFile = temporaryDirectory.resolve("downtime.db");

        EquipmentService original = new EquipmentService(
                new SqliteEquipmentRepository(databaseFile));

        original.addEquipment(new Equipment(
                "AMR-001", "Original Robot", "AMR", "Training Area"));

        EquipmentService reopened = new EquipmentService(
                new SqliteEquipmentRepository(databaseFile));

        assertThrows(IllegalArgumentException.class, () ->
                reopened.addEquipment(new Equipment(
                        "AMR-001", "Replacement Robot", "AMR", "Other Area")));

        assertEquals(
                "Original Robot",
                reopened.getAllEquipment().get(0).getName());
    }

    @Test
    void failedDatabaseWriteDoesNotRegisterEquipmentInMemory()
            throws SQLException {

        Path databaseFile = temporaryDirectory.resolve("downtime.db");

        SqliteEquipmentRepository repository =
                new SqliteEquipmentRepository(databaseFile);

        EquipmentService service = new EquipmentService(repository);

        // Insert directly after service startup to force a database
        // duplicate-key rejection that its in-memory lookup cannot detect.
        repository.save(new Equipment(
                "AMR-001", "Existing Robot", "AMR", "Training Area"));

        assertThrows(IllegalStateException.class, () ->
                service.addEquipment(new Equipment(
                        "AMR-001", "New Robot", "AMR", "Other Area")));

        assertFalse(service.containsEquipment("AMR-001"));
        assertEquals("Existing Robot", repository.findAll().get(0).getName());
    }
}