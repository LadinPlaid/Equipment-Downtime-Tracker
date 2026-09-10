package com.jacobhastert.downtime.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Records a downtime event for one equipment item.
 *
 * New incidents are open. Resolving an incident records its restoration
 * time and corrective action. Resolved incidents cannot be changed.
 */
public class Incident {

    private final String id;
    private final String equipmentId;
    private final String description;
    private final Instant startedAt;

    private IncidentStatus status = IncidentStatus.OPEN;
    private Instant resolvedAt;
    private String resolutionNotes;

    /**
     * Creates an open incident.
     *
     * @param id unique incident identifier
     * @param equipmentId identifier of the affected equipment
     * @param description observed symptoms
     * @param startedAt time the downtime began
     * @throws IllegalArgumentException if required information is missing
     */
    public Incident(
            String id,
            String equipmentId,
            String description,
            Instant startedAt) {

        this.id = requireText(id, "Incident ID");
        this.equipmentId = requireText(equipmentId, "Equipment ID");
        this.description = requireText(description, "Description");

        if (startedAt == null) {
            throw new IllegalArgumentException("Start time is required.");
        }

        this.startedAt = startedAt;
    }

    /**
     * Moves an open incident into active troubleshooting.
     *
     * @throws IllegalStateException if the incident is not open
     */
    public void startWork() {
        if (status != IncidentStatus.OPEN) {
            throw new IllegalStateException(
                    "Only open incidents can move to in progress.");
        }

        status = IncidentStatus.IN_PROGRESS;
    }

    /**
     * Resolves an active incident and records the corrective action.
     *
     * @param restoredAt time equipment service was restored
     * @param notes description of the corrective action
     * @throws IllegalStateException if the incident is already resolved
     * @throws IllegalArgumentException if the time or notes are invalid
     */
    public void resolve(Instant restoredAt, String notes) {
        if (status == IncidentStatus.RESOLVED) {
            throw new IllegalStateException("Incident is already resolved.");
        }

        if (restoredAt == null || restoredAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "Restoration time must be at or after the start time.");
        }

        String validatedNotes = requireText(notes, "Resolution notes");

        // Validate all inputs before changing the incident's state.
        resolvedAt = restoredAt;
        resolutionNotes = validatedNotes;
        status = IncidentStatus.RESOLVED;
    }

    /**
     * Returns completed downtime without rounding away partial minutes.
     *
     * @return duration between failure and restoration
     * @throws IllegalStateException if the incident is still active
     */
    public Duration getDowntime() {
        if (status != IncidentStatus.RESOLVED) {
            throw new IllegalStateException(
                    "Downtime is not final until the incident is resolved.");
        }

        return Duration.between(startedAt, resolvedAt);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value.trim();
    }

    public String getId() {
        return id;
    }

    public String getEquipmentId() {
        return equipmentId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    /** @return restoration time, or null while the incident is active */
    public Instant getResolvedAt() {
        return resolvedAt;
    }

    /** @return corrective action, or null while the incident is active */
    public String getResolutionNotes() {
        return resolutionNotes;
    }
}