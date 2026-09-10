package com.jacobhastert.downtime.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jacobhastert.downtime.model.Equipment;
import com.jacobhastert.downtime.repository.SqliteEquipmentRepository;

/**
 * Manages equipment registration and lookup.
 *
 * <p>The default constructor uses memory only. Supplying a repository
 * loads existing equipment and saves new registrations to SQLite.
 *
 * <p>This service assumes a single application instance manages the data.
 */
public class EquipmentService {

    private final Map<String, Equipment> equipmentById =
            new LinkedHashMap<>();

    private final SqliteEquipmentRepository repository;

    /**
     * Creates an in-memory service for isolated tests.
     */
    public EquipmentService() {
        repository = null;
    }

    /**
     * Creates a persistent service and loads existing equipment.
     *
     * @param repository database storage for equipment
     * @throws SQLException if existing records cannot be loaded
     */
    public EquipmentService(SqliteEquipmentRepository repository)
            throws SQLException {

        this.repository = Objects.requireNonNull(
                repository, "Equipment repository is required.");

        for (Equipment equipment : repository.findAll()) {
            equipmentById.put(equipment.getId(), equipment);
        }
    }

    /**
     * Registers equipment with a unique identifier.
     *
     * @param equipment equipment to register
     * @throws IllegalArgumentException if equipment is null or its ID exists
     * @throws IllegalStateException if database storage fails
     */
    public void addEquipment(Equipment equipment) {
        if (equipment == null) {
            throw new IllegalArgumentException(
                    "Equipment cannot be null.");
        }

        if (equipmentById.containsKey(equipment.getId())) {
            throw new IllegalArgumentException(
                    "An equipment item with this ID already exists.");
        }

        if (repository != null) {
            try {
                // Save first so a failed write never appears as a successful
                // registration in the application's in-memory collection.
                repository.save(equipment);
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Equipment could not be saved to the database.",
                        exception);
            }
        }

        equipmentById.put(equipment.getId(), equipment);
    }

    /**
     * Checks whether an equipment identifier is registered.
     *
     * @param equipmentId identifier to look up
     * @return true if the equipment exists
     */
    public boolean containsEquipment(String equipmentId) {
        return equipmentId != null
                && equipmentById.containsKey(equipmentId.trim());
    }

    /**
     * Returns a copy of the equipment collection.
     *
     * @return registered equipment
     */
    public List<Equipment> getAllEquipment() {
        return new ArrayList<>(equipmentById.values());
    }
}