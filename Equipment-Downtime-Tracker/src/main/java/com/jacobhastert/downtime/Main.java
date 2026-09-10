package com.jacobhastert.downtime;

import java.util.List;
import java.util.Scanner;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Duration;

import com.jacobhastert.downtime.model.Equipment;
import com.jacobhastert.downtime.model.Incident;
import com.jacobhastert.downtime.model.IncidentStatus;
import com.jacobhastert.downtime.model.EquipmentDowntimeSummary;
import com.jacobhastert.downtime.service.EquipmentService;
import com.jacobhastert.downtime.service.IncidentService;
import com.jacobhastert.downtime.service.DowntimeReportService;
import com.jacobhastert.downtime.repository.SqliteIncidentRepository;
import com.jacobhastert.downtime.repository.SqliteEquipmentRepository;

public class Main {

	// handles the menu and UI
    public static void main(String[] args) {
    	Path databaseFile = Path.of("downtime.db").toAbsolutePath().normalize();

    	EquipmentService equipmentService;
    	IncidentService incidentService;

    	try {
    	    // Load equipment first because incidents reference equipment IDs.
    	    SqliteEquipmentRepository equipmentRepository =
    	            new SqliteEquipmentRepository(databaseFile);

    	    equipmentService = new EquipmentService(equipmentRepository);

    	    SqliteIncidentRepository incidentRepository =
    	            new SqliteIncidentRepository(databaseFile);

    	    incidentService = new IncidentService(
    	            equipmentService, incidentRepository);

    	} catch (SQLException exception) {
    	    System.err.println("Unable to initialize the application database.");
    	    System.err.println("Database location: " + databaseFile);
    	    System.err.println("Details: " + exception.getMessage());
    	    return;
    	}

    	System.out.println("Application database: " + databaseFile);
    	DowntimeReportService reportService =
    	        new DowntimeReportService(equipmentService, incidentService);

        try (Scanner scanner = new Scanner(System.in)) {
            boolean running = true;

            while (running) {
            	System.out.println("\nEquipment Downtime Tracker");
            	System.out.println("1. Add equipment");
            	System.out.println("2. View equipment");
            	System.out.println("3. Open incident");
            	System.out.println("4. View incidents");
            	System.out.println("5. Start troubleshooting");
            	System.out.println("6. Resolve incident");
            	System.out.println("7. Exit");
            	System.out.println("8. View downtime summary");
            	System.out.print("Select an option: ");

                if (!scanner.hasNextLine()) {
                    break;
                }

                String choice = scanner.nextLine().trim();

                try {
                    switch (choice) {
                        case "1":
                            addEquipment(scanner, equipmentService);
                            break;

                        case "2":
                            displayEquipment(equipmentService);
                            break;

                        case "3":
                            String equipmentId = readLine(scanner, "Equipment ID: ");
                            String description = readLine(scanner, "Symptoms: ");

                            Incident incident = incidentService.openIncident(
                                    equipmentId, description, Instant.now());

                            System.out.println("Incident opened: " + incident.getId());
                            break;

                        case "4":
                            displayIncidents(incidentService);
                            break;

                        case "5":
                            incidentService.startWork(
                                    readLine(scanner, "Incident ID: "));
                            System.out.println("Incident is now in progress.");
                            break;

                        case "6":
                            String incidentId = readLine(scanner, "Incident ID: ");
                            String notes = readLine(scanner, "Corrective action: ");

                            incidentService.resolveIncident(
                                    incidentId, Instant.now(), notes);

                            System.out.println("Incident resolved.");
                            break;

                        case "7":
                            running = false;
                            break;
                            
                        case "8":
                        	displayDowntimeSummary(reportService);
                        	break;

                        default:
                            System.out.println("Choose an option from 1 to 8.");
                    }
                } catch (IllegalArgumentException | IllegalStateException exception) {
                    System.out.println("Could not complete action: "
                            + exception.getMessage());
                }
            }
        }

        System.out.println("Goodbye!");
    }

    // adds equipment to the database
    private static void addEquipment(
            Scanner scanner, EquipmentService equipmentService) {

        String id = readLine(scanner, "Equipment ID: ");
        String name = readLine(scanner, "Equipment name: ");
        String type = readLine(scanner, "Equipment type: ");
        String location = readLine(scanner, "Location: ");

        Equipment equipment = new Equipment(id, name, type, location);
        equipmentService.addEquipment(equipment);

        System.out.println("Equipment added successfully.");
    }

    private static String readLine(Scanner scanner, String prompt) {
        System.out.print(prompt);

        if (!scanner.hasNextLine()) {
            throw new IllegalStateException(
                    "Input ended. Closing the application.");
        }

        return scanner.nextLine();
    }

    // manages records and duplicate IDs
    private static void displayEquipment(
            EquipmentService equipmentService) {

        List<Equipment> equipment = equipmentService.getAllEquipment();

        if (equipment.isEmpty()) {
            System.out.println("No equipment has been registered.");
            return;
        }

        System.out.println("\nID | Name | Type | Location");

        for (Equipment item : equipment) {
            System.out.println(item);
        }
    }
    /**
     * Displays incident history and completed downtime.
     * Active incidents do not yet have a final duration.
     */
    private static void displayIncidents(IncidentService incidentService) {
        List<Incident> incidents = incidentService.getAllIncidents();

        if (incidents.isEmpty()) {
            System.out.println("No incidents have been recorded.");
            return;
        }

        for (Incident incident : incidents) {
            System.out.println("\nIncident: " + incident.getId());
            System.out.println("Equipment: " + incident.getEquipmentId());
            System.out.println("Status: " + incident.getStatus());
            System.out.println("Symptoms: " + incident.getDescription());
            System.out.println("Started: " + incident.getStartedAt());

            if (incident.getStatus() == IncidentStatus.RESOLVED) {
                System.out.println("Restored: " + incident.getResolvedAt());
                System.out.println("Corrective action: "
                        + incident.getResolutionNotes());
                System.out.println("Downtime: "
                        + incident.getDowntime().getSeconds() + " seconds");
            }
        }
    }
    /**
     * Displays all-time completed downtime and active incident counts.
     */
    private static void displayDowntimeSummary(
            DowntimeReportService reportService) {

        List<EquipmentDowntimeSummary> summaries =
                reportService.generateSummary();

        if (summaries.isEmpty()) {
            System.out.println("Register equipment before generating a report.");
            return;
        }

        System.out.println("\nALL-TIME EQUIPMENT DOWNTIME SUMMARY");
        System.out.println("Active incidents are excluded from completed downtime.");

        for (EquipmentDowntimeSummary summary : summaries) {
            System.out.println(
                    "\n" + summary.equipmentId() + " | " + summary.equipmentName());

            System.out.println(
                    "Resolved incidents: " + summary.resolvedIncidents());

            System.out.println(
                    "Active incidents: " + summary.activeIncidents());

            System.out.println(
                    "Completed downtime: "
                            + formatDuration(summary.completedDowntime()));
        }
    }

    /**
     * Formats elapsed time using total hours rather than wrapping at 24 hours.
     * Subsecond precision is omitted only for display.
     */
    private static String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        return String.format("%dh %02dm %02ds", hours, minutes, seconds);
    }
}