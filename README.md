# SOC EPS License Management Platform

## 1. Project Overview

This project implements an EPS-based license management and enforcement subsystem for a multi-tenant SOC/SIEM platform.

The system is split into two main planes:

- Control Plane: manages licenses, usage APIs, alerts, reports and audit logs.
- Data Plane: receives event batches, checks tenant quota, enforces EPS limit and updates usage counters.

## 2. MVP Scope

### Control Plane

Implemented by `management-api-service`.

Responsibilities:

- License CRUD
- License expiring-soon API
- Usage API
- Alert API
- Report CSV
- Audit log
- Sync tenant quota to Redis

### Data Plane

Implemented by `collector-service`.

Responsibilities:

- Receive JSON batch events
- Read tenant quota from Redis
- Apply token bucket
- Accept/drop events based on EPS quota
- Update Redis EPS counters
- Trigger usage alerts

### Mocked Components

The following production components are mocked in this MVP:

- Real Agent
- API Gateway
- Ingestion Gateway
- Downstream SOC pipeline

Instead, this project uses:

- `event-producer` as mock Agent/Ingestion Gateway
- mock downstream inside Collector

## 3. Architecture

```text
event-producer
      |
      v
collector-service  ---> Redis
      |
      v
mock downstream

frontend
      |
      v
management-api-service ---> PostgreSQL
                       ---> Redis
```

## 4. Services

| Service | Port | Description |
|---|---:|---|
| management-api-service | 8080 | Control Plane API |
| collector-service | 8081 | Data Plane Collector |
| frontend | 3000 | Dashboard UI |
| postgres | 5432 | Main database |
| redis | 6379 | Quota cache, token bucket, EPS counters |
| event-producer | - | Mock event generator |

## 5. Tech Stack

- Java 25
- Spring Boot
- Maven
- PostgreSQL
- Redis
- React + Vite + TypeScript
- Docker Compose
- Swagger/OpenAPI

## 6. Run Project

From the root directory:

```bash
docker compose up --build
```

To stop:

```bash
docker compose down
```

To stop and remove volumes:

```bash
docker compose down -v
```

## 7. Health Checks

Management API:

```bash
curl http://localhost:8080/api/v1/health
```

Collector Service:

```bash
curl http://localhost:8081/api/v1/health
```

Expected response example:

```json
{
  "service": "collector-service",
  "status": "ok",
  "redis": "PONG",
  "timestamp": "2026-06-17T10:00:00Z"
}
```

## 8. Swagger UI

Management API Swagger:

```text
http://localhost:8080/swagger-ui.html
```

Collector Service Swagger:

```text
http://localhost:8081/swagger-ui.html
```

## 9. Frontend

Frontend URL:

```text
http://localhost:3000
```

## 10. Docker Compose Services

```text
postgres
redis
management-api-service
collector-service
frontend
event-producer
```

## 11. Current Phase Status

Phase 1 focuses on project skeleton and infrastructure setup.

Completed:

- Created management-api-service
- Created collector-service
- Created frontend
- Created event-producer
- Added Dockerfile for backend services
- Added Dockerfile for frontend
- Added Dockerfile for event-producer
- Added docker-compose.yml
- Verified Docker Compose startup
- Verified health endpoints
- Verified Swagger UI
- Verified frontend page
- Verified event-producer heartbeat

Not implemented yet:

- License CRUD
- Audit log
- Quota sync to Redis
- Collector batch event API
- Token bucket
- EPS Redis counters
- Alerts
- Usage APIs
- CSV report
- Real dashboard

## 12. Next Phase

Phase 2 will implement the Control Plane foundation:

- Database schema
- License entity
- License CRUD APIs
- Audit log
- Sync tenant EPS quota to Redis