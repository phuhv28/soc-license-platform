# SOC EPS License Management Platform

A multi-tenant SOC/SIEM platform for managing EPS (Events Per Second) licenses, enforcing usage quotas in real-time, and providing operational dashboards with alerting capabilities.

---

## Table of Contents

- [System Flow](#system-flow)
- [Architecture](#architecture)
- [Services](#services)
- [Tech Stack](#tech-stack)
- [Redis Key Schema](#redis-key-schema)
- [Database Schema](#database-schema)
- [API Reference](#api-reference)
- [Getting Started](#getting-started)
- [Testing](#testing)
- [Manual Testing Guide](#manual-testing-guide)

---

## System Flow

### Overview

The platform operates on a **Control Plane / Data Plane** split architecture. Here's how all the pieces work together:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              CONTROL PLANE                                  │
│                                                                              │
│  ┌──────────┐     ┌──────────────────────────┐     ┌──────────────────────┐  │
│  │ Frontend │────>│  Management API Service   │────>│     PostgreSQL       │  │
│  │ :3000    │<────│  :8080                    │<────│     :5432            │  │
│  └──────────┘     │                          │     └──────────────────────┘  │
│                   │  • License CRUD          │                               │
│                   │  • Tenant CRUD           │     ┌──────────────────────┐  │
│                   │  • Alert Management      │────>│       Redis          │  │
│                   │  • Usage APIs            │<────│       :6379          │  │
│                   │  • CSV Reports           │     │                      │  │
│                   │  • Audit Logging         │     │  • quota:{tenantId}  │  │
│                   │  • Schedulers            │     │  • usage counters    │  │
│                   └──────────────────────────┘     │  • token bucket      │  │
│                                                    └──────────────────────┘  │
├──────────────────────────────────────────────────────────────────────────────┤
│                               DATA PLANE                                     │
│                                                                              │
│  ┌──────────────┐     ┌──────────────────────────┐          ▲               │
│  │Event Producer│────>│   Collector Service       │──────────┘               │
│  │(Mock Agent)  │     │   :8081                   │                          │
│  └──────────────┘     │                          │                          │
│                       │  • Receive batch events  │     ┌──────────────────┐  │
│                       │  • Read quota from Redis │────>│ Mock Downstream  │  │
│                       │  • Token bucket enforce  │     │ (placeholder)    │  │
│                       │  • Update EPS counters   │     └──────────────────┘  │
│                       └──────────────────────────┘                           │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Step-by-Step Flow

#### 1. Admin tạo Tenant & License (Control Plane)

```
Admin (Frontend) → POST /api/v1/tenants         → PostgreSQL (tenants table)
Admin (Frontend) → POST /api/v1/licenses         → PostgreSQL (licenses table)
                                                  → Redis SET quota:{tenantId} = epsQuota
```

Khi admin tạo license, hệ thống **tự động sync EPS quota vào Redis** dưới key `quota:{tenantId}`. Đây là cầu nối giữa Control Plane và Data Plane — collector-service chỉ cần đọc Redis, không cần gọi API sang management-api-service.

#### 2. Event Producer gửi events (Data Plane)

```
Event Producer → POST /api/v1/collector/events/batch → Collector Service
                 {
                   "tenantId": "uuid",
                   "events": [{ eventId, eventType, timestamp, payload, metadata }, ...]
                 }
```

Event Producer mô phỏng agent/sensor thực tế, gửi batch events liên tục theo target EPS đã cấu hình. Hỗ trợ multi-tenant qua biến `TENANT_IDS`.

#### 3. Collector xử lý events (Data Plane — 4 Rules)

```
Rule 1: Redis GET quota:{tenantId}
        → Không có quota? → DROP ALL → ProcessingDecision = NO_ACTIVE_LICENSE

Rule 2: Token Bucket kiểm tra
        → Tính available tokens = old_tokens + (elapsed_seconds × quota_eps)
        → acceptableCount = min(eventCount, availableTokens)
        → Vượt quota? → DROP phần vượt → ProcessingDecision = OVER_QUOTA

Rule 3: Validate từng event (eventId, tenantId, eventType, timestamp, payload)
        → Invalid? → DROP → ProcessingDecision = PARTIAL_VALIDATION / ALL_INVALID

Rule 4: Cập nhật usage counters (LUÔN LUÔN, kể cả khi drop hết)
        → Redis INCR usage:{tenantId}:received:1m:{yyyyMMddHHmm}
        → Redis INCR usage:{tenantId}:accepted:1m:{yyyyMMddHHmm}
        → Redis INCR usage:{tenantId}:dropped:1m:{yyyyMMddHHmm}
        → Redis INCR usage:{tenantId}:*:1d:{yyyyMMdd}
```

**Token Bucket Algorithm:**
- Capacity = quota EPS (ví dụ 100 tokens)
- Refill rate = quota EPS tokens/giây
- Mỗi event tiêu thụ 1 token
- State lưu trong Redis: `bucket:{tenantId}:tokens` + `bucket:{tenantId}:last_refill_epoch_ms`

#### 4. Schedulers kiểm tra và tạo alerts (Control Plane)

```
AlertTriggerScheduler (mỗi 60 giây):
  → Scan tất cả active tenants
  → Đọc Redis: usage counters → tính currentEps = accepted_1m / 60
  → Đọc Redis: quota:{tenantId}
  → usagePercent = (currentEps / quota) × 100
  → ≥ 70%?  → Tạo USAGE_70_PERCENT alert (DEDUP: chỉ tạo nếu chưa có OPEN alert)
  → ≥ 100%? → Tạo USAGE_100_PERCENT alert
  → < 70%?  → Auto-resolve OPEN alerts

LicenseExpirationScheduler (mỗi ngày 8h sáng):
  → Query licenses: status=ACTIVE AND endDate BETWEEN today AND today+7
  → Tạo LICENSE_EXPIRING_SOON alert (DEDUP)
```

#### 5. Frontend hiển thị Dashboard (Control Plane)

```
Admin Dashboard:
  → GET /api/v1/usage/summary       → Tất cả tenants + EPS + % usage
  → GET /api/v1/alerts?status=OPEN  → Danh sách cảnh báo
  → GET /api/v1/licenses/expiring-soon → License sắp hết hạn

Tenant Dashboard:
  → GET /api/v1/usage/{tenantId}/current  → EPS hiện tại, quota, dropped today
  → GET /api/v1/usage/{tenantId}/history  → 24h time-series cho biểu đồ
  → GET /api/v1/alerts?tenantId=X         → Alerts của tenant
  → GET /api/v1/reports/usage/csv         → Export CSV báo cáo
```

#### 6. Data Flow Diagram

```
                    ┌─────────────────┐
                    │  Event Producer  │
                    │   (Mock Agent)   │
                    └────────┬────────┘
                             │ POST /events/batch
                             ▼
                    ┌─────────────────┐        ┌───────────┐
                    │    Collector     │───────>│   Redis   │
                    │    Service      │<───────│           │
                    └────────┬────────┘  R/W   │ • quota   │
                             │                  │ • tokens  │
                       accepted events          │ • usage   │
                             │                  └─────┬─────┘
                             ▼                        │ READ
                    ┌─────────────────┐        ┌──────┴──────┐
                    │ Mock Downstream │        │ Management  │
                    │  (placeholder)  │        │ API Service │
                    └─────────────────┘        └──────┬──────┘
                                                      │ R/W
                                               ┌──────┴──────┐
                                               │ PostgreSQL  │
                                               │ • tenants   │
                                               │ • licenses  │
                                               │ • alerts    │
                                               │ • audit_logs│
                                               └─────────────┘
```

---

## Architecture

### Design Decisions

| Decision | Rationale |
|---|---|
| **Control/Data Plane split** | Collector (hot path) chỉ dùng Redis, không gọi DB → latency thấp |
| **Redis là cầu nối** | Quota sync từ Control → Data Plane qua `quota:{tenantId}` key |
| **Token Bucket trong Redis** | Atomic, persistent, hỗ trợ multi-instance trong tương lai |
| **Scheduler-based alerts** | Tách alert trigger khỏi collector hot path, không ảnh hưởng throughput |
| **Usage counters dùng Redis INCR** | Atomic, non-blocking, tự động TTL để cleanup dữ liệu cũ |

### Mocked Components

Các component production được mock trong MVP:

| Production Component | Mock |
|---|---|
| Real Agent/Sensor | `event-producer` |
| API Gateway | Direct HTTP calls |
| Ingestion Gateway | Direct HTTP to collector |
| Downstream SOC pipeline | `sendToDownstream()` placeholder |

---

## Services

| Service | Port | Description |
|---|---:|---|
| management-api-service | 8080 | Control Plane — License, Tenant, Alert, Usage, Report APIs |
| collector-service | 8081 | Data Plane — Batch event processing, Token Bucket, EPS Counters |
| frontend | 3000 | React Dashboard — Admin & Tenant views |
| postgres | 5432 | PostgreSQL — Tenants, Licenses, Alerts, Audit Logs |
| redis | 6379 | Redis — Quota cache, Token Bucket state, EPS Counters |
| event-producer | — | Mock event generator (configurable EPS, multi-tenant) |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 25, Spring Boot, Spring Data JPA, Spring Data Redis |
| Build | Maven |
| Database | PostgreSQL 16 |
| Cache/State | Redis 7 |
| Frontend | React 19, TypeScript, Vite, Recharts |
| Infra | Docker, Docker Compose |
| API Docs | Swagger / OpenAPI (springdoc) |
| Testing | JUnit 5, Mockito |

---

## Redis Key Schema

| Key Pattern | Type | TTL | Description |
|---|---|---|---|
| `quota:{tenantId}` | String | None | EPS quota synced from license |
| `bucket:{tenantId}:tokens` | String | 24h | Available tokens for rate limiting |
| `bucket:{tenantId}:last_refill_epoch_ms` | String | 24h | Last refill timestamp |
| `usage:{tenantId}:received:1m:{yyyyMMddHHmm}` | String (counter) | 48h | Events received per minute |
| `usage:{tenantId}:accepted:1m:{yyyyMMddHHmm}` | String (counter) | 48h | Events accepted per minute |
| `usage:{tenantId}:dropped:1m:{yyyyMMddHHmm}` | String (counter) | 48h | Events dropped per minute |
| `usage:{tenantId}:received:1d:{yyyyMMdd}` | String (counter) | 90d | Events received per day |
| `usage:{tenantId}:accepted:1d:{yyyyMMdd}` | String (counter) | 90d | Events accepted per day |
| `usage:{tenantId}:dropped:1d:{yyyyMMdd}` | String (counter) | 90d | Events dropped per day |

---

## Database Schema

```sql
tenants (tenant_id UUID PK, name, status, created_at, updated_at)
licenses (license_id UUID PK, tenant_id FK, eps_quota, start_date, end_date, status, created_at, updated_at)
alerts (alert_id UUID PK, tenant_id FK, license_id FK nullable, alert_type, severity, status, message, threshold_percent, current_percent, triggered_at, resolved_at, created_at, updated_at)
audit_logs (audit_log_id UUID PK, actor, action, resource_type, resource_id, before_value JSONB, after_value JSONB, created_at)
```

Full schema: `apps/management-api-service/src/main/resources/db/schema_v1.sql`

---

## API Reference

### Management API (`:8080`)

#### Tenant APIs

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/tenants` | Create tenant |
| `GET` | `/api/v1/tenants` | List all tenants |
| `GET` | `/api/v1/tenants/{tenantId}` | Get tenant by ID |
| `PUT` | `/api/v1/tenants/{tenantId}` | Update tenant |
| `DELETE` | `/api/v1/tenants/{tenantId}` | Disable tenant (soft delete) |

#### License APIs

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/licenses` | Create license (syncs quota to Redis) |
| `GET` | `/api/v1/licenses` | List all licenses |
| `GET` | `/api/v1/licenses/{licenseId}` | Get license by ID |
| `GET` | `/api/v1/licenses/tenant/{tenantId}` | Get licenses by tenant |
| `PUT` | `/api/v1/licenses/{licenseId}` | Update license |
| `DELETE` | `/api/v1/licenses/{licenseId}` | Disable license (removes Redis quota) |
| `GET` | `/api/v1/licenses/expiring-soon?days=7` | Licenses expiring within N days |

#### Alert APIs

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/alerts` | List alerts (filter: `tenantId`, `status`, `alertType`) |
| `GET` | `/api/v1/alerts/{alertId}` | Get alert by ID |
| `PUT` | `/api/v1/alerts/{alertId}/resolve` | Resolve alert |
| `PUT` | `/api/v1/alerts/{alertId}/ignore` | Ignore alert |

#### Usage APIs

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/usage/{tenantId}/current` | Current EPS, quota, usage %, daily totals |
| `GET` | `/api/v1/usage/{tenantId}/history?hours=24` | Time-series data (1 data point/minute) |
| `GET` | `/api/v1/usage/summary` | All tenants summary (admin dashboard) |

#### Report APIs

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/reports/usage/csv?tenantId=X&month=2026-06` | Download monthly CSV report |

#### Other APIs

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/audit-logs` | List all audit logs |
| `GET` | `/api/v1/health` | Health check |

### Collector API (`:8081`)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/collector/events/batch` | Submit event batch for processing |
| `GET` | `/api/v1/health` | Health check |

### Swagger UI

- Management API: http://localhost:8080/swagger-ui.html
- Collector Service: http://localhost:8081/swagger-ui.html

---

## Getting Started

### Prerequisites

- Docker & Docker Compose

### Run

```bash
# Start all services
docker compose up --build

# Stop
docker compose down

# Stop and remove volumes (clean reset)
docker compose down -v
```

### Service URLs

| Service | URL |
|---|---|
| Frontend Dashboard | http://localhost:3000 |
| Management API | http://localhost:8080 |
| Collector API | http://localhost:8081 |
| Management Swagger | http://localhost:8080/swagger-ui.html |
| Collector Swagger | http://localhost:8081/swagger-ui.html |

### Environment Variables

#### Event Producer

| Variable | Default | Description |
|---|---|---|
| `TENANT_IDS` | `tenant-a` | Comma-separated tenant IDs |
| `EPS` | `100` | Target events per second |
| `BATCH_SIZE` | `50` | Events per batch |
| `COLLECTOR_URL` | `http://localhost:8081` | Collector service URL |

---

## Testing

### Unit Tests

```bash
# Management API Service (33 tests)
cd apps/management-api-service
./mvnw test

# Collector Service (14 tests)
cd apps/collector-service
./mvnw test
```

### Test Coverage

| Service | Test Classes | Tests | Status |
|---|---|---:|---|
| management-api-service | LicenseServiceTest, AlertServiceTest, UsageApiServiceTest, ReportServiceTest | 33 | ✅ All passing |
| collector-service | CollectorServiceImplTest, UsageCounterServiceTest | 14 | ✅ All passing |
| **Total** | **6 classes** | **47** | **✅** |

---

## Manual Testing Guide

### 1. Create Tenant

```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{"name": "Tenant A"}'
```

Copy the returned `tenantId`.

### 2. Create License

```bash
curl -X POST http://localhost:8080/api/v1/licenses \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "<tenant-id>",
    "epsQuota": 100,
    "startDate": "2026-06-01",
    "endDate": "2026-12-31"
  }'
```

Verify Redis quota:

```bash
docker exec -it soc-redis redis-cli GET "quota:<tenant-id>"
# Expected: "100"
```

### 3. Send Events

```bash
curl -X POST http://localhost:8081/api/v1/collector/events/batch \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "<tenant-id>",
    "events": [
      {
        "eventId": "evt-001",
        "tenantId": "<tenant-id>",
        "eventType": "firewall.connection",
        "timestamp": "2026-06-23T10:00:00Z",
        "payload": {"sourceIp": "10.0.0.1", "port": 443},
        "metadata": {"agent": "agent-1"}
      }
    ]
  }'
```

### 4. Check Usage

```bash
curl http://localhost:8080/api/v1/usage/<tenant-id>/current
```

### 5. Check Alerts

```bash
curl http://localhost:8080/api/v1/alerts?status=OPEN
```

### 6. Export CSV Report

```bash
curl -O http://localhost:8080/api/v1/reports/usage/csv?tenantId=<tenant-id>&month=2026-06
```

### 7. View Audit Logs

```bash
curl http://localhost:8080/api/v1/audit-logs
```

---

## Project Structure

```
soc-license-platform/
├── apps/
│   ├── management-api-service/    # Control Plane (Spring Boot)
│   │   └── src/main/java/com/vcs/management/
│   │       ├── tenant/            # Tenant CRUD
│   │       ├── license/           # License CRUD + Scheduler
│   │       ├── alert/             # Alert Service + Scheduler + Controller
│   │       ├── usage/             # Usage API (reads Redis counters)
│   │       ├── report/            # CSV Report generation
│   │       ├── audit/             # Audit logging
│   │       └── common/            # Enums, exceptions, Redis sync
│   │
│   ├── collector-service/         # Data Plane (Spring Boot)
│   │   └── src/main/java/com/vcs/collector/
│   │       ├── collector/         # Batch event processing
│   │       ├── quota/             # QuotaService + TokenBucketService
│   │       ├── usagecounter/      # Redis EPS counters
│   │       └── common/            # Enums, response wrapper
│   │
│   ├── frontend/                  # React Dashboard (Vite + TypeScript)
│   │   └── src/
│   │       ├── api/               # Axios API modules
│   │       ├── pages/admin/       # Admin Dashboard, Tenants, Licenses, Audit
│   │       └── pages/tenant/      # Tenant Dashboard (EPS chart, alerts)
│   │
│   └── event-producer/            # Mock Agent (Node.js + TypeScript)
│
├── docker-compose.yml
└── README.md
```
