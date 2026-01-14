# Factory Events Processing System

A Spring Boot 3.x application for processing and managing factory machine events with PostgreSQL database.

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker and Docker Compose (for local PostgreSQL)

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Java Version**: 17
- **Database**: PostgreSQL 15
- **Build Tool**: Maven
- **Key Dependencies**:
  - Spring Web
  - Spring Data JPA
  - Spring Validation
  - Lombok
  - PostgreSQL Driver
  - HikariCP (Connection Pooling)

## Project Structure

```
factory-events/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/buyogo/factoryevents/
│   │   │       └── FactoryEventsApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── schema.sql
│   └── test/
│       └── java/
│           └── com/buyogo/factoryevents/
├── docker-compose.yml
├── pom.xml
└── README.md
```

## Getting Started

### 1. Start PostgreSQL Database

```bash
docker-compose up -d
```

This will start PostgreSQL on `localhost:5432` with:
- Database: `factory_events`
- Username: `postgres`
- Password: `postgres`

### 2. Build the Project

```bash
mvn clean install
```

### 3. Run the Application

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

## Database Schema

The application uses a `machine_events` table with the following structure:

- `id`: Primary key (auto-generated)
- `machine_id`: Machine identifier
- `event_type`: Type of event (START, STOP, ERROR, WARNING, etc.)
- `timestamp`: Event occurrence time
- `status`: Machine status (RUNNING, IDLE, ERROR)
- `temperature`: Machine temperature (Celsius)
- `pressure`: Machine pressure (PSI)
- `vibration`: Machine vibration level
- `error_code`: Error code for ERROR events
- `error_message`: Detailed error description
- `created_at`: Record creation timestamp
- `updated_at`: Record update timestamp

### Indexes

The schema includes optimized indexes for:
- Machine ID lookups
- Event type filtering
- Timestamp-based queries
- Status filtering
- Composite queries (machine_id + event_type + timestamp)

## Configuration

Key configurations in `application.properties`:

- **Server Port**: 8080
- **Database URL**: `jdbc:postgresql://localhost:5432/factory_events`
- **Connection Pool**: HikariCP with 10 max connections, 5 minimum idle
- **JPA**: Hibernate with SQL logging enabled for debugging

## Development

### Stopping the Database

```bash
docker-compose down
```

### Removing Database Volume

```bash
docker-compose down -v
```

## Next Steps

This is the foundational structure. You can now add:
- Entity classes
- Repository interfaces
- Service layer
- REST controllers
- Business logic
- Tests
