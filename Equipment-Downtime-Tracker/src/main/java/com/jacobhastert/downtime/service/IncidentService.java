package com.jacobhastert.downtime.service;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.jacobhastert.downtime.model.Incident;
import com.jacobhastert.downtime.model.IncidentStatus;
import com.jacobhastert.downtime.repository.SqliteIncidentRepository;

/**
 * Manages incident lifecycle rules and optional database persistence.
 *
 * <p>Updates are applied to a copy and saved before replacing the stored
 * in-memory record. Failed writes therefore preserve the previous state.
 *
 * <p>This service assumes a single application instance manages the data.
 */
public class IncidentService {

    private final EquipmentService equipmentService;
    private final SqliteIncidentRepository repository;

    private final Map<String, Incident> incidents = new LinkedHashMap<>();

    /**
     * Creates an in-memory service for isolated tests.
     *
     * @param equipmentService equipment registry used to validate incidents
     */
    public IncidentService(EquipmentService equipmentService) {
        this.equipmentService = Objects.requireNonNull(
                equipmentService, "Equipment service is required.");
        repository = null;
    }

    /**
     * Creates a persistent service and loads existing incidents.
     *
     * @param equipmentService equipment registry loaded from the same database
     * @param repository incident database storage
     * @throws SQLException if stored incidents cannot be loaded
     */
    public IncidentService(
            EquipmentService equipmentService,
            SqliteIncidentRepository repository) throws SQLException {

        this.equipmentService = Objects.requireNonNull(
                equipmentService, "Equipment service is required.");

        this.repository = Objects.requireNonNull(
                repository, "Incident repository is required.");

        for (Incident incident : repository.findAll()) {
            if (!equipmentService.containsEquipment(
                    incident.getEquipmentId())) {
                throw new SQLException(
                        "Stored incident references missing equipment: "
                                + incident.getEquipmentId());
            }

            incidents.put(incident.getId(), incident);
        }
    }

    /**
     * Opens an incident for registered equipment.
     *
     * <p>Incidents must be entered chronologically for each equipment item.
     * A new incident may start exactly when a previous incident ended.
     *
     * @param equipmentId affected equipment identifier
     * @param description observed symptoms
     * @param startedAt time downtime began
     * @return a snapshot of the newly created incident
     * @throws IllegalArgumentException if supplied data is invalid or the
     *         start time precedes a previous incident's restoration
     * @throws IllegalStateException if an active incident exists or saving fails
     */
    public Incident openIncident(
            String equipmentId, String description, Instant startedAt) {

        if (!equipmentService.containsEquipment(equipmentId)) {
            throw new IllegalArgumentException("Equipment ID not found.");
        }

        if (startedAt == null) {
            throw new IllegalArgumentException("Start time is required.");
        }

        String normalizedId = equipmentId.trim();

        for (Incident incident : incidents.values()) {
            if (!incident.getEquipmentId().equals(normalizedId)) {
                continue;
            }

            if (incident.getStatus() != IncidentStatus.RESOLVED) {
                throw new IllegalStateException(
                        "This equipment already has an active incident.");
            }

            // Compare against every completed incident rather than relying
            // on map order, which may differ after loading from the database.
            if (startedAt.isBefore(incident.getResolvedAt())) {
                throw new IllegalArgumentException(
                        "New incidents must start at or after all previous "
                                + "incidents for this equipment were resolved.");
            }
        }

        Incident incident = new Incident(
                UUID.randomUUID().toString(),
                normalizedId,
                description,
                startedAt);

        saveAndStore(incident);
        return copyOf(incident);
    }

    /**
     * Moves an open incident into active troubleshooting.
     *
     * @param incidentId incident to update
     * @throws IllegalArgumentException if the incident does not exist
     * @throws IllegalStateException if the transition or save fails
     */
    public void startWork(String incidentId) {
        Incident updated = copyOf(getRequiredIncident(incidentId));
        updated.startWork();
        saveAndStore(updated);
    }

    /**
     * Records restoration and corrective action.
     *
     * @param incidentId incident to resolve
     * @param restoredAt time equipment service was restored
     * @param notes corrective action taken
     * @throws IllegalArgumentException if the incident or supplied data is invalid
     * @throws IllegalStateException if already resolved or saving fails
     */
    public void resolveIncident(
            String incidentId, Instant restoredAt, String notes) {

        Incident updated = copyOf(getRequiredIncident(incidentId));
        updated.resolve(restoredAt, notes);
        saveAndStore(updated);
    }

    /**
     * Returns independent snapshots of the stored incidents.
     *
     * @return incident snapshots that callers may inspect safely
     */
    public List<Incident> getAllIncidents() {
        List<Incident> snapshots = new ArrayList<>();

        for (Incident incident : incidents.values()) {
            snapshots.add(copyOf(incident));
        }

        return snapshots;
    }

    private void saveAndStore(Incident incident) {
        if (repository != null) {
            try {
                repository.save(incident);
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "Incident could not be saved to the database.",
                        exception);
            }
        }

        // Replace the in-memory record only after persistence succeeds.
        incidents.put(incident.getId(), incident);
    }

    private Incident getRequiredIncident(String incidentId) {
        Incident incident = incidentId == null
                ? null : incidents.get(incidentId.trim());

        if (incident == null) {
            throw new IllegalArgumentException("Incident ID not found.");
        }

        return incident;
    }

    private static Incident copyOf(Incident original) {
        Incident copy = new Incident(
                original.getId(),
                original.getEquipmentId(),
                original.getDescription(),
                original.getStartedAt());

        if (original.getStatus() == IncidentStatus.IN_PROGRESS) {
            copy.startWork();
        } else if (original.getStatus() == IncidentStatus.RESOLVED) {
            copy.resolve(
                    original.getResolvedAt(),
                    original.getResolutionNotes());
        }

        return copy;
    }
}