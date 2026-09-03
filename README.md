# IT-Ticketing-System

A secure Spring Boot IT service desk application for managing ticket lifecycles, SLA risk, assignments, comments, CSV workflows, and audit history.

## Highlights

- Role-aware dashboard for administrators and technicians
- Ticket lifecycle: `OPEN` -> `IN_PROGRESS` -> `CLOSED`
- SLA tracking for urgent, high, medium, and low priority tickets
- Search, filtering, pagination, overdue views, and operational reports
- CSV import/export with quoted-field and multiline support
- Ticket comments and chronological activity history
- CSRF protection, session fixation protection, security headers, upload limits, and server-side validation
- REST API under `/api/tickets`
- H2 for local development and PostgreSQL-ready configuration

## Tech Stack

Java 21 | Spring Boot 4 | Spring MVC | Thymeleaf | Spring Security | Spring Data JPA | H2 | PostgreSQL | Maven

## Run Locally

Prerequisites: Java 21 or newer.

```powershell
.\mvnw.cmd spring-boot:run
```

Open http://localhost:8080 and sign in with the development credentials configured by default:

- Admin: `admin` / `admin123`
- Technician: `tech` / `tech123`

Override credentials before deployment with `APP_ADMIN_USERNAME`, `APP_ADMIN_PASSWORD`, `APP_TECH_USERNAME`, and `APP_TECH_PASSWORD`.

## Test

```powershell
.\mvnw.cmd test
```

The test suite covers authentication, authorization, CSRF enforcement, ticket visibility, lifecycle rules, validation, SLA behavior, CSV parsing, and activity history.

## PostgreSQL

Set these environment variables when running against PostgreSQL:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `DB_DRIVER_CLASS_NAME=org.postgresql.Driver`
- `DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect`

For production, use a managed PostgreSQL database, HTTPS, strong externally managed credentials, and a migration tool before changing the schema policy from the local `ddl-auto=update` default.

## Docker Compose

Build and start the application with PostgreSQL:

```powershell
docker compose up --build
```

The compose file uses placeholder passwords for local demonstration. Replace every `change-me-*` value before using it outside a local environment.

## API Examples

All API routes require authentication.

```http
GET /api/tickets
GET /api/tickets/{id}
POST /api/tickets
PUT /api/tickets/{id}/status
```

Create a ticket with JSON:

```json
{
  "title": "VPN access issue",
  "description": "Remote user cannot connect after password reset.",
  "priority": "HIGH",
  "assignee": "tech"
}
```

## Project Structure

- `controller`: MVC pages and REST endpoints
- `service`: ticket rules, SLA calculation, validation, CSV processing, and auditing
- `model`: JPA entities and domain enums
- `repository`: Spring Data persistence interfaces
- `templates` and `static`: responsive IT-Ticketing-System interface
- `src/test`: service and security regression tests
