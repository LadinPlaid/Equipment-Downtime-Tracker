# Equipment Downtime Tracker

## Overview

The Equipment Downtime Tracker is a Java console application that records equipment incidents, tracks troubleshooting progress, and summarizes completed downtime.

Inspired by my experience supporting automated equipment, this independent project applies object-oriented programming, database persistence, and automated testing to an operational problem.

The application uses SQLite to preserve equipment and incident records between sessions. Demonstrations use fictional equipment and incident data.

## Features

### Equipment Management

- Register equipment with a unique ID, name, type, and location.
- View registered equipment.
- Reject duplicate equipment IDs and blank required fields.
- Save equipment records between application sessions.

### Incident Tracking

- Open incidents for registered equipment.
- Record symptoms and incident start times.
- Move incidents through Open, In Progress, and Resolved statuses.
- Save corrective-action notes and restoration times.
- Calculate completed downtime from incident timestamps.
- Restore incident history when the application restarts.

### Validation and Data Integrity

- Prevent multiple active incidents for the same equipment.
- Require incidents to be entered chronologically for each equipment item.
- Reject restoration times earlier than incident start times.
- Prevent changes to resolved incidents through the service.
- Preserve the previous in-memory incident state when a database save fails.
- Enforce equipment references and active-incident uniqueness in SQLite.

### Downtime Reporting

- Display resolved incident counts by equipment.
- Display active incident counts separately.
- Calculate total downtime from resolved incidents.
- Include equipment that has no recorded incidents.
- Display elapsed time in hours, minutes, and seconds.

## Project Structure

### Model

**Equipment.java**  
Represents equipment and validates its required information.

**Incident.java**  
Represents a downtime event and manages valid lifecycle transitions and duration calculations.

**IncidentStatus.java**  
Defines the Open, In Progress, and Resolved statuses.

**EquipmentDowntimeSummary.java**  
Provides an immutable report row containing incident counts and completed downtime for one equipment item.

### Services

**EquipmentService.java**  
Manages equipment registration, duplicate-ID validation, and equipment persistence.

**IncidentService.java**  
Manages incident creation, status updates, chronological validation, and persistence. Uses copies to prevent failed saves from changing stored in-memory records.

**DowntimeReportService.java**  
Generates equipment summaries while separating active incidents from completed downtime.

### Repositories

**SqliteEquipmentRepository.java**  
Creates the equipment table and handles equipment storage and retrieval.

**SqliteIncidentRepository.java**  
Creates incident storage, saves lifecycle updates, and reconstructs incidents from database records.

### Application

**Main.java**  
Initializes database-backed services and provides the interactive console menu.

**pom.xml**  
Defines the Java version, dependencies, and Maven build and test configuration.

## Technologies Used

- Java 17
- Maven
- SQLite
- JDBC
- JUnit 5
- Eclipse IDE
- Object-Oriented Programming
- Java Time API
- Unit and Integration Testing

## Running the Application

### Requirements

- JDK 17 or later
- Eclipse IDE with Maven support
- Internet access for the initial dependency download

### Setup

1. Clone or download this repository.
2. In Eclipse, select **File → Import → Maven → Existing Maven Projects**.
3. Select the project directory containing `pom.xml`.
4. Finish the import and allow Maven to download dependencies.
5. Open `Main.java` in `com.jacobhastert.downtime`.
6. Select **Run As → Java Application**.

The application creates or opens `downtime.db` in its working directory and prints the full database path at startup. Use the same working directory between runs to access the same records.

### Menu Options

1. Add equipment
2. View equipment
3. Open incident
4. View incidents
5. Start troubleshooting
6. Resolve incident
7. Exit
8. View downtime summary

Incident IDs are generated automatically. Use the displayed ID when updating or resolving an incident.

## Example Workflow

1. Register fictional equipment such as `AMR-001`.
2. Open an incident describing a navigation fault.
3. Move the incident to In Progress.
4. Resolve it with corrective-action notes.
5. View the downtime summary.
6. Restart the application to confirm the records remain available.

The console records the current time when incidents are opened and resolved.

## Testing

The project includes unit tests for application rules and integration tests using temporary SQLite databases.

Test scenarios include:

- Equipment registration and duplicate-ID rejection.
- Incident status transitions.
- Exact downtime calculations and midnight boundaries.
- Rejection of invalid resolution times.
- Prevention of overlapping incident timelines.
- Equipment and incident persistence after reopening the database.
- Database relationship and uniqueness constraints.
- Preservation of existing state after failed writes.
- Separation of active incidents from completed downtime totals.

### Run in Eclipse

Right-click `src/test/java` and select **Run As → JUnit Test**.

### Run with Maven

With Maven installed, run the following from the directory containing `pom.xml`:

```text
mvn clean verify
```

In Eclipse, the equivalent is **Run As → Maven build…**, with `clean verify` entered as the goals.

## Current Limitations

- The application supports one running instance managing the database.
- Incidents must be entered chronologically for each equipment item.
- The console does not support entering historical timestamps.
- Reports cover all recorded history without date filters.
- Active incident durations are excluded from completed downtime.
- Equipment IDs are case-sensitive.
- The application does not include user accounts or a graphical interface.

Local database files and generated build output are excluded from version control.

## Future Improvements

- Date-range filters for incident history and reports.
- CSV export of downtime summaries.
- A graphical or web interface.
- Historical incident entry with interval-overlap validation.
- Equipment editing and retirement workflows.

## What I Learned

This project extended my experience with Java beyond in-memory applications by introducing persistent storage, SQL constraints, and database integration tests.

Separating models, services, repositories, and console interaction helped keep responsibilities clear. Implementing incident transitions and downtime reports reinforced the importance of validating business rules and preserving accurate timestamps.

Testing failed database writes also demonstrated why application state should change only after persistence succeeds.

## Author

**Jacob Hastert**  
Computer Science Student  
Southern New Hampshire University  

[GitHub](https://github.com/LadinPlaid)

Independent portfolio project inspired by experience supporting automated equipment.
