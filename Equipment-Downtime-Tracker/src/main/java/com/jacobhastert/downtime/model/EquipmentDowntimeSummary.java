package com.jacobhastert.downtime.model;

import java.time.Duration;

/**
 * Immutable report row for one equipment item.
 *
 * @param equipmentId equipment identifier
 * @param equipmentName equipment display name
 * @param resolvedIncidents number of completed incidents
 * @param activeIncidents number of open or in-progress incidents
 * @param completedDowntime total duration of resolved incidents
 */
public record EquipmentDowntimeSummary(
        String equipmentId,
        String equipmentName,
        int resolvedIncidents,
        int activeIncidents,
        Duration completedDowntime) {
}