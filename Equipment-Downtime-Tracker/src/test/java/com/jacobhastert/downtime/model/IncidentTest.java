package com.jacobhastert.downtime.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies incident transitions, duration calculations, and rejection
 * of invalid resolutions.
 */
class IncidentTest {

    private static final Instant START =
            Instant.parse("2026-09-09T10:15:00Z");

    private Incident incident;

    @BeforeEach
    void setUp() {
        incident = new Incident(
                "INC-001",
                "AMR-001",
                "Navigation fault",
                START);
    }

    @Test
    void newIncidentStartsOpen() {
        assertEquals(IncidentStatus.OPEN, incident.getStatus());
        assertNull(incident.getResolvedAt());
        assertNull(incident.getResolutionNotes());
    }

    @Test
    void openIncidentCanMoveToInProgress() {
        incident.startWork();

        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
    }

    @Test
    void resolvingIncidentCalculatesExactlyTwentySevenMinutes() {
        // Fixed timestamps keep this test independent of execution speed.
        incident.startWork();
        incident.resolve(
                Instant.parse("2026-09-09T10:42:00Z"),
                "Cleared obstruction and verified navigation.");

        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
        assertEquals(Duration.ofMinutes(27), incident.getDowntime());
        assertEquals(
                "Cleared obstruction and verified navigation.",
                incident.getResolutionNotes());
    }

    @Test
    void durationPreservesPartialMinutesAcrossMidnight() {
        Incident overnight = new Incident(
                "INC-002",
                "AMR-002",
                "Sensor fault",
                Instant.parse("2026-09-09T23:59:45Z"));

        overnight.resolve(
                Instant.parse("2026-09-10T00:00:15Z"),
                "Reset sensor.");

        assertEquals(Duration.ofSeconds(30), overnight.getDowntime());
    }

    @Test
    void resolutionBeforeStartIsRejectedWithoutChangingState() {
        assertThrows(IllegalArgumentException.class, () ->
                incident.resolve(
                        START.minusSeconds(1),
                        "Reset controller."));

        assertEquals(IncidentStatus.OPEN, incident.getStatus());
        assertNull(incident.getResolvedAt());
        assertNull(incident.getResolutionNotes());
    }

    @Test
    void blankResolutionNotesDoNotPartiallyResolveIncident() {
        incident.startWork();

        assertThrows(IllegalArgumentException.class, () ->
                incident.resolve(START.plusSeconds(60), "   "));

        // A rejected update must leave the existing record intact.
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertNull(incident.getResolvedAt());
        assertNull(incident.getResolutionNotes());
    }

    @Test
    void resolvedIncidentCannotBeResolvedAgain() {
        Instant restoredAt = START.plusSeconds(60);
        incident.resolve(restoredAt, "Reset controller.");

        assertThrows(IllegalStateException.class, () ->
                incident.resolve(
                        START.plusSeconds(120),
                        "Different corrective action."));

        assertEquals(restoredAt, incident.getResolvedAt());
        assertEquals("Reset controller.", incident.getResolutionNotes());
    }

    @Test
    void resolvedIncidentCannotReturnToInProgress() {
        incident.resolve(START.plusSeconds(60), "Reset controller.");

        assertThrows(IllegalStateException.class, incident::startWork);
        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
    }

    @Test
    void activeIncidentHasNoFinalDowntime() {
        assertThrows(IllegalStateException.class, incident::getDowntime);
    }
}