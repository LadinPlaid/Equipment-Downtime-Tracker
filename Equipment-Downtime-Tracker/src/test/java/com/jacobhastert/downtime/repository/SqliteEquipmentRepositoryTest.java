package com.jacobhastert.downtime.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jacobhastert.downtime.model.Equipment;

/**
 * Tests database behavior using isolated temporary files.
 *
 * These integration tests exercise the real SQLite driver.
 */
class SqliteEquipmentRepositoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void newDatabaseContainsNoEquipment() throws SQLException {
        SqliteEquipmentRepository repository =
                new SqliteEquipmentRepository(
                        temporaryDirectory.resolve("equipment.db"));

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void savedEquipmentSurvivesReopeningDatabase() throws SQLException {
        Path databaseFile = temporaryDirectory.resolve("equipment.db");

        SqliteEquipmentRepository original =
                new SqliteEquipmentRepository(databaseFile);

        original.save(new Equipment(
                "AMR-001",
                "Warehouse Robot One",
                "AMR",
                "Training Area"));

        // A new repository reads the file without sharing in-memory records.
        SqliteEquipmentRepository reopened =
                new SqliteEquipmentRepository(databaseFile);

        List<Equipment> records = reopened.findAll();

        assertEquals(1, records.size());

        Equipment equipment = records.get(0);
        assertEquals("AMR-001", equipment.getId());
        assertEquals("Warehouse Robot One", equipment.getName());
        assertEquals("AMR", equipment.getType());
        assertEquals("Training Area", equipment.getLocation());
    }

    @Test
    void duplicateIdDoesNotOverwriteExistingEquipment() throws SQLException {
        SqliteEquipmentRepository repository =
                new SqliteEquipmentRepository(
                        temporaryDirectory.resolve("equipment.db"));

        repository.save(new Equipment(
                "AMR-001", "Original Robot", "AMR", "Training Area"));

        assertThrows(SQLException.class, () ->
                repository.save(new Equipment(
                        "AMR-001", "Replacement Robot", "AMR", "Other Area")));

        List<Equipment> records = repository.findAll();

        assertEquals(1, records.size());
        assertEquals("Original Robot", records.get(0).getName());
        assertEquals("Training Area", records.get(0).getLocation());
    }

    @Test
    void apostrophesAreStoredAsOrdinaryText() throws SQLException {
        SqliteEquipmentRepository repository =
                new SqliteEquipmentRepository(
                        temporaryDirectory.resolve("equipment.db"));

        repository.save(new Equipment(
                "AMR-001",
                "Technician's Demo Robot",
                "AMR",
                "Training Area"));

        assertEquals(
                "Technician's Demo Robot",
                repository.findAll().get(0).getName());
    }
}