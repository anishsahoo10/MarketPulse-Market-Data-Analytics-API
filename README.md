<<<<<<< HEAD
# MarketPulse - Market Data Analytics API & Dashboard

MarketPulse is a practical, production-grade REST API built with **Java 21** and **Spring Boot 3.x** that retrieves financial instrument metadata, persists daily historical market prices in **PostgreSQL**, and computes quantitative price analytics (Daily Absolute & Percentage Change, Simple Moving Average).

It includes an interactive **Web Analytics Dashboard** and an OpenAPI Swagger interface out of the box.

---

## 🏛️ System Architecture

MarketPulse follows a clean **modular monolith** design pattern:

```text
  Client (Postman / Browser / curl)
                 │
                 ▼
  REST Controllers  ──── Request validation & DTO mapping
                 │
                 ▼
  Application Services ── Business calculations (BigDecimal) & Transaction management
        │            │
        ▼            ▼
   Repositories   MarketDataProvider (Interface)
        │            │
        ▼            ▼
   PostgreSQL /   Provider HTTP Client (Alpha Vantage) or
    H2 Storage     Local Simulation Engine (Mock)
```

### Core Engineering Principles
1. **Separation of Concerns**: Controllers are thin; business calculations belong in `AnalyticsService`; database writes are isolated from third-party HTTP timeouts.
2. **Numeric Precision**: All monetary values and calculations utilize `BigDecimal` with explicit `RoundingMode.HALF_UP` to prevent floating-point inaccuracies.
3. **Idempotent Synchronization**: Re-syncing existing records executes an upsert policy preventing duplicate rows at both the service and database constraint levels (`unique(instrument_id, price_date)`).
4. **Predictable Error Contract**: Centralized `@RestControllerAdvice` translates domain exceptions into standard JSON error objects without leaking internal traces or credentials.
5. **Zero-Friction Local Evaluation**: Includes a deterministic mock market data provider so reviewers can run and test the entire system immediately without signing up for external API keys.

---

## 🚀 Prerequisites

- **Java**: OpenJDK 21 (LTS)
- **Build Tool**: Maven 3.9+ (or included `./mvnw` / `mvnw.cmd`)
- **Database**: PostgreSQL 16+ or Docker Compose (Default profile uses an embedded PostgreSQL-compatible H2 database for zero-config startup)

---

## ⚡ Quick Start

### 1. Run with Maven (Zero-Config Development Mode)

Clone the repository and run:

```bash
# On Linux / macOS
./mvnw clean spring-boot:run

# On Windows
mvnw.cmd clean spring-boot:run
```

Once started:
- 📊 **Interactive Web Dashboard**: [http://localhost:8080/](http://localhost:8080/)
- 📖 **Swagger OpenAPI UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- 🩺 **Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

### 2. Run with Docker Compose & PostgreSQL

To run MarketPulse with a dedicated PostgreSQL database container:

```bash
# Start PostgreSQL database and MarketPulse container
docker compose up -d

# Verify containers are healthy
docker compose ps
```

---

## ⚙️ Environment Configuration

Environment variables can be set in your shell or in a `.env` file (refer to `.env.example`):

| Variable | Default Value | Description |
|---|---|---|
| `PORT` | `8080` | Web server listening port |
| `SPRING_PROFILES_ACTIVE` | `default` | Active profile (`default` for embedded, `postgres` for PostgreSQL) |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `marketpulse` | Database name |
| `DB_USER` | `marketpulse_user` | Database user |
| `DB_PASSWORD` | `marketpulse_secret` | Database password |
| `MARKET_DATA_PROVIDER_TYPE` | `mock` | `mock` (built-in simulator) or `alphavantage` (real provider) |
| `MARKET_DATA_API_KEY` | `demo` | API key for Alpha Vantage |
| `PROVIDER_TIMEOUT_SECONDS` | `10` | Connect and read timeout in seconds |

---

## 📡 REST API Reference

Base path: `/api/v1`

| Method | Endpoint | Description | Status Codes |
|---|---|---|---|
| `GET` | `/instruments/{symbol}` | Get instrument metadata | `200`, `404` |
| `GET` | `/instruments/{symbol}/prices` | Query historical prices (`from`, `to` optional) | `200`, `400`, `404` |
| `POST` | `/instruments/{symbol}/sync` | Sync history from external provider | `200`, `400`, `502`, `503` |
| `GET` | `/instruments/{symbol}/analytics/daily-change` | Calculate latest 2-session daily price change | `200`, `404`, `422` |
| `GET` | `/instruments/{symbol}/analytics/moving-average` | Calculate Simple Moving Average (SMA) | `200`, `400`, `404`, `422` |
| `GET` | `/actuator/health` | Spring Boot Actuator application health | `200` |

---

### API Examples

#### 1. Synchronize Market Data
```bash
curl -X POST "http://localhost:8080/api/v1/instruments/AAPL/sync"
```
**Response (200 OK):**
```json
{
  "symbol": "AAPL",
  "status": "SUCCESS",
  "recordsReceived": 131,
  "recordsInserted": 131,
  "recordsUpdated": 0,
  "recordsRejected": 0,
  "syncedAt": "2026-10-10T16:09:41.218Z"
}
```

#### 2. Query Instrument Metadata
```bash
curl "http://localhost:8080/api/v1/instruments/AAPL"
```
**Response (200 OK):**
```json
{
  "symbol": "AAPL",
  "name": "Apple Inc.",
  "exchange": "NASDAQ",
  "currency": "USD",
  "createdAt": "2026-10-10T16:09:40.758Z",
  "updatedAt": "2026-10-10T16:09:40.758Z"
}
```

#### 3. Query Historical Prices
```bash
curl "http://localhost:8080/api/v1/instruments/AAPL/prices?from=2026-01-01&to=2026-01-31"
```
**Response (200 OK):**
```json
{
  "symbol": "AAPL",
  "from": "2026-01-01",
  "to": "2026-01-31",
  "count": 22,
  "prices": [
    {
      "date": "2026-01-02",
      "open": 248.5000,
      "high": 252.1000,
      "low": 247.9000,
      "close": 251.2500,
      "volume": 48219000
    }
  ]
}
```

#### 4. Daily Price Change Analytics
```bash
curl "http://localhost:8080/api/v1/instruments/AAPL/analytics/daily-change"
```
**Response (200 OK):**
```json
{
  "symbol": "AAPL",
  "previousTradingDate": "2026-10-08",
  "currentTradingDate": "2026-10-09",
  "previousClose": 228.1500,
  "currentClose": 231.8500,
  "absoluteChange": 3.7000,
  "percentageChange": 1.6217
}
```

#### 5. Simple Moving Average (SMA)
```bash
curl "http://localhost:8080/api/v1/instruments/AAPL/analytics/moving-average?period=20"
```
**Response (200 OK):**
```json
{
  "symbol": "AAPL",
  "period": 20,
  "asOfDate": "2026-10-09",
  "dataPointsUsed": 20,
  "simpleMovingAverage": 229.4325
}
```

#### 6. Standard Error Response Contract
```json
{
  "timestamp": "2026-10-10T16:15:30Z",
  "status": 400,
  "code": "INVALID_DATE_RANGE",
  "message": "'from' must be on or before 'to'",
  "path": "/api/v1/instruments/AAPL/prices"
}
```

---

## 🧮 Mathematical Formulas & Policies

1. **Daily Absolute Change**:
   $$\text{absoluteChange} = \text{currentClose} - \text{previousClose}$$

2. **Daily Percentage Change**:
   $$\text{percentageChange} = \left(\frac{\text{absoluteChange}}{\text{previousClose}}\right) \times 100$$
   - Uses the 2 most recent trading sessions available in the local database.
   - Evaluated using `BigDecimal` with 4 decimal places (`RoundingMode.HALF_UP`).
   - If previous close is $\le 0$, returns HTTP `422 INSUFFICIENT_HISTORY`.

3. **Simple Moving Average (SMA)**:
   $$\text{SMA}(N) = \frac{\sum_{i=1}^{N} \text{close}_i}{N}$$
   - $N$ is bounded between 2 and 200 trading sessions.
   - Calculated over sessions on or before `asOfDate`.
   - If fewer than $N$ sessions exist, returns HTTP `422 INSUFFICIENT_HISTORY`.

---

## 🧪 Automated Test Suite

Run the full suite of automated unit, service, web, and integration tests:

```bash
# Run all tests
./mvnw clean test
```

### Coverage Highlights:
- **`AnalyticsServiceTest`**: Precision rounding, daily change, SMA formulas, period boundaries, zero-division safeguards, and insufficient history exceptions.
- **`PriceServiceTest`**: Idempotent upsert verification, duplicate prevention, invalid date ranges, row sanity checks (e.g. `high >= low`), and provider failure isolation.
- **`InstrumentControllerTest`**: `MockMvc` contract verification, JSON shapes, HTTP status codes (`400`, `404`, `422`), and parameter validations.
- **`MarketDataProviderTest`**: Provider error payload handling, timeout resistance, and synthetic data validity.
- **`MarketpulseApplicationTests`**: End-to-end integration test validating context bootstrapping, database schema migrations, and full sync-to-analytics lifecycle.

---

## 🖥️ Web Dashboard Highlights

MarketPulse includes an analytics web interface at `http://localhost:8080/`:
- **Real-Time Health Pill**: Live polling of Spring Boot Actuator status.
- **Instant Ticker Lookup**: Quick selector pills for popular tickers (`AAPL`, `MSFT`, `GOOGL`, `NVDA`, `AMZN`, `TSLA`).
- **Interactive Price & Volume Chart**: Powered by Chart.js with responsive time range filters (1M, 3M, 6M, All).
- **Interactive SMA Calculator**: Immediate calculation with preset periods (7D, 20D, 50D) or custom period inputs.
- **Live Sync Button**: Visual feedback on records received, inserted, and updated.
- **API Inspector Drawer**: Live view of raw JSON payloads returned from endpoints for interviewers and reviewers.

---

## 📄 License
This project is open-source and available under the Apache 2.0 License.
=======
# MarketPulse-Market-Data-Analytics-API
>>>>>>> 25342492952853d89e744bacbbd2b652fdebd06a
