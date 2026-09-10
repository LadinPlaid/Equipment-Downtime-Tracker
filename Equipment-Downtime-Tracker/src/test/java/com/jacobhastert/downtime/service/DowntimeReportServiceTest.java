package com.jacobhastert.downtime.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jacobhastert.downtime.model.Equipment;
import com.jacobhastert.downtime.model.EquipmentDowntimeSummary;
import com.jacobhastert.downtime.model.Incident;

/**
 * Verifies report totals, inclusion of idle equipment, and separation
 * of active incidents from completed downtime.
 */
class DowntimeReportServiceTest {

    private static final Instant START =
            Instant.parse("2026-09-09T10:15:00Z");

    private EquipmentService equipmentService;
    private IncidentService incidentService;
    private DowntimeReportService reportService;

    @BeforeEach
    void setUp() {
        equipmentService = new EquipmentService();
        incidentService = new IncidentService(equipmentService);
        reportService =
                new DowntimeReportService(equipmentService, incidentService);
    }

    @Test
    void noEquipmentProducesEmptyReport() {
        assertTrue(reportService.generateSummary().isEmpty());
    }

    @Test
    void equipmentWithoutIncidentsAppearsWithZeroTotals() {
        registerEquipment("AMR-001");

        List<EquipmentDowntimeSummary> summaries =
                reportService.generateSummary();

        assertEquals(1, summaries.size());

        EquipmentDowntimeSummary summary = summaries.get(0);

        assertEquals("AMR-001", summary.equipmentId());
        assertEquals(0, summary.resolvedIncidents());
        assertEquals(0, summary.activeIncidents());
        assertEquals(Duration.ZERO, summary.completedDowntime());
    }

    @Test
    void completedDurationsAreSummedAndActiveIncidentIsExcluded() {
        registerEquipment("AMR-001");

        Incident first = incidentService.openIncident(
                "AMR-001", "Navigation fault", START);

        incidentService.resolveIncident(
                first.getId(),
                START.plus(Duration.ofMinutes(27)),
                "Cleared obstruction.");

        Incident second = incidentService.openIncident(
                "AMR-001",
                "Sensor fault",
                START.plus(Duration.ofHours(1)));

        incidentService.resolveIncident(
                second.getId(),
                START.plus(Duration.ofHours(1))
                        .plus(Duration.ofMinutes(15)),
                "Reset sensor.");

        Incident active = incidentService.openIncident(
                "AMR-001",
                "New fault",
                START.plus(Duration.ofHours(2)));

        incidentService.startWork(active.getId());

        EquipmentDowntimeSummary summary =
                reportService.generateSummary().get(0);

        assertEquals(2, summary.resolvedIncidents());
        assertEquals(1, summary.activeIncidents());
        assertEquals(Duration.ofMinutes(42), summary.completedDowntime());
    }

    @Test
    void reportSeparatesEquipmentAndPreservesLongDurations() {
        // Register out of order to verify deterministic report ordering.
        registerEquipment("AMR-002");
        registerEquipment("AMR-001");

        Incident resolved = incidentService.openIncident(
                "AMR-001", "Extended repair", START);

        Duration elapsed = Duration.ofHours(25).plusSeconds(30);

        incidentService.resolveIncident(
                resolved.getId(),
                START.plus(elapsed),
                "Completed repair.");

        incidentService.openIncident(
                "AMR-002", "Sensor fault", START);

        List<EquipmentDowntimeSummary> summaries =
                reportService.generateSummary();

        assertEquals(2, summaries.size());

        EquipmentDowntimeSummary first = summaries.get(0);
        assertEquals("AMR-001", first.equipmentId());
        assertEquals(1, first.resolvedIncidents());
        assertEquals(0, first.activeIncidents());
        assertEquals(elapsed, first.completedDowntime());

        EquipmentDowntimeSummary second = summaries.get(1);
        assertEquals("AMR-002", second.equipmentId());
        assertEquals(0, second.resolvedIncidents());
        assertEquals(1, second.activeIncidents());
        assertEquals(Duration.ZERO, second.completedDowntime());
    }

    private void registerEquipment(String id) {
        equipmentService.addEquipment(new Equipment(
                id, "Demo Robot", "AMR", "Training Area"));
    }
}