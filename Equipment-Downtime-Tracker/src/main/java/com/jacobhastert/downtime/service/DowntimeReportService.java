package com.jacobhastert.downtime.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.jacobhastert.downtime.model.Equipment;
import com.jacobhastert.downtime.model.EquipmentDowntimeSummary;
import com.jacobhastert.downtime.model.Incident;
import com.jacobhastert.downtime.model.IncidentStatus;

/**
 * Generates all-time downtime summaries from current service records.
 *
 * <p>Reports include equipment without incidents. Active incidents are
 * counted separately and excluded from completed downtime.
 */
public class DowntimeReportService {

    private final EquipmentService equipmentService;
    private final IncidentService incidentService;

    public DowntimeReportService(
            EquipmentService equipmentService,
            IncidentService incidentService) {

        this.equipmentService = Objects.requireNonNull(
                equipmentService, "Equipment service is required.");

        this.incidentService = Objects.requireNonNull(
                incidentService, "Incident service is required.");
    }

    /**
     * Builds one summary per registered equipment item.
     *
     * @return summaries sorted by equipment identifier
     */
    public List<EquipmentDowntimeSummary> generateSummary() {
        List<Equipment> equipment = equipmentService.getAllEquipment();

        // Read incident snapshots once so every report row uses the same data.
        List<Incident> incidents = incidentService.getAllIncidents();

        equipment.sort(Comparator.comparing(Equipment::getId));

        List<EquipmentDowntimeSummary> summaries = new ArrayList<>();

        for (Equipment item : equipment) {
            int resolvedCount = 0;
            int activeCount = 0;
            Duration completedDowntime = Duration.ZERO;

            for (Incident incident : incidents) {
                if (!incident.getEquipmentId().equals(item.getId())) {
                    continue;
                }

                if (incident.getStatus() == IncidentStatus.RESOLVED) {
                    resolvedCount++;
                    completedDowntime =
                            completedDowntime.plus(incident.getDowntime());
                } else {
                    activeCount++;
                }
            }

            summaries.add(new EquipmentDowntimeSummary(
                    item.getId(),
                    item.getName(),
                    resolvedCount,
                    activeCount,
                    completedDowntime));
        }

        return summaries;
    }
}