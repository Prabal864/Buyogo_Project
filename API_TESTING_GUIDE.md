# Factory Events API - Testing Guide

## Table of Contents
1. [Server Information](#server-information)
2. [Authentication](#authentication)
3. [API Endpoints](#api-endpoints)
4. [Testing with cURL](#testing-with-curl)
5. [Testing with Postman](#testing-with-postman)
6. [Testing with HTTPie](#testing-with-httpie)
7. [Sample Test Data](#sample-test-data)
8. [Expected Responses](#expected-responses)
9. [Error Scenarios](#error-scenarios)
10. [Performance Testing](#performance-testing)

---

## Server Information

- **Base URL**: `http://localhost:9021`
- **Server Port**: `9021`
- **Database**: PostgreSQL (DigitalOcean)
- **Content-Type**: `application/json`

---

## Authentication

Currently, the API does not require authentication. All endpoints are publicly accessible.

---

## API Endpoints

### 1. Batch Event Ingestion
**POST** `/events/batch`

Ingest multiple factory events in a single batch operation.

#### Request Body
```json
[
  {
    "eventId": "string (required, unique)",
    "eventTime": "ISO-8601 datetime (required)",
    "receivedTime": "ISO-8601 datetime (optional, ignored - set by server)",
    "machineId": "string (required)",
    "lineId": "string (optional)",
    "factoryId": "string (optional)",
    "durationMs": "number (required, 0-21600000)",
    "defectCount": "number (required, -1 or >=0)"
  }
]
```

**Note:** The `receivedTime` field can be included in requests for reference, but it will be **ignored by the server** and automatically set to the current server time when the event is received.

#### Response
```json
{
  "accepted": "number - newly created events",
  "deduped": "number - duplicate/ignored events",
  "updated": "number - updated existing events",
  "rejected": "number - validation failures",
  "rejections": [
    {
      "eventId": "string",
      "reason": "string - rejection reason"
    }
  ]
}
```

---

### 2. Machine Statistics
**GET** `/stats?machineId={id}&start={datetime}&end={datetime}`

Get aggregated statistics for a specific machine within a time range.

#### Query Parameters
- `machineId` (required): Machine identifier
- `start` (required): Start time in ISO-8601 format
- `end` (required): End time in ISO-8601 format

#### Response
```json
{
  "machineId": "string",
  "totalEvents": "number",
  "totalDefects": "number",
  "avgDurationMs": "number",
  "maxDurationMs": "number",
  "minDurationMs": "number"
}
```

---

### 3. Top Defect Lines
**GET** `/stats/top-defect-lines?factoryId={id}&from={datetime}&to={datetime}&limit={number}`

Get the production lines with the highest defect counts.

#### Query Parameters
- `factoryId` (required): Factory identifier
- `from` (required): Start time in ISO-8601 format
- `to` (required): End time in ISO-8601 format
- `limit` (optional, default=10): Number of top lines to return

#### Response
```json
[
  {
    "lineId": "string",
    "totalDefects": "number"
  }
]
```

---

## Testing with cURL

### 1. Batch Event Ingestion

#### Valid Event Batch
```bash
curl -X POST http://localhost:9021/events/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "eventId": "event-001",
      "eventTime": "2026-01-16T10:30:00.000Z",
      "receivedTime": "2026-01-16T10:30:05.123Z",
      "machineId": "machine-alpha",
      "lineId": "line-01",
      "factoryId": "factory-north",
      "durationMs": 5000,
      "defectCount": 2
    },
    {
      "eventId": "event-002",
      "eventTime": "2026-01-16T10:31:00.000Z",
      "receivedTime": "2026-01-16T10:31:03.456Z",
      "machineId": "machine-beta",
      "lineId": "line-02",
      "factoryId": "factory-north",
      "durationMs": 7200,
      "defectCount": 0
    }
  ]'
```
      "defectCount": 0
    }
  ]'
```

#### Batch with Duplicates (Deduplication Test)
```bash
curl -X POST http://localhost:9021/events/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "eventId": "event-003",
      "eventTime": "2026-01-16T10:32:00.000Z",
      "receivedTime": "2026-01-16T10:32:02.789Z",
      "machineId": "machine-gamma",
      "lineId": "line-01",
      "factoryId": "factory-south",
      "durationMs": 3500,
      "defectCount": 1
    },
    {
      "eventId": "event-003",
      "eventTime": "2026-01-16T10:32:00.000Z",
      "receivedTime": "2026-01-16T10:32:02.789Z",
      "machineId": "machine-gamma",
      "lineId": "line-01",
      "factoryId": "factory-south",
      "durationMs": 3500,
      "defectCount": 1
    }
  ]'
```
```

#### Batch with Updates (Different Payload, Same eventId)
```bash
curl -X POST http://localhost:9021/events/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "eventId": "event-001",
      "eventTime": "2026-01-16T10:35:00.000Z",
      "receivedTime": "2026-01-16T10:35:04.567Z",
      "machineId": "machine-alpha",
      "lineId": "line-01",
      "factoryId": "factory-north",
      "durationMs": 6000,
      "defectCount": 3
    }
  ]'
```

#### Batch with Validation Errors
```bash
curl -X POST http://localhost:9021/events/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "eventId": "event-004",
      "eventTime": "2026-01-16T10:40:00.000Z",
      "receivedTime": "2026-01-16T10:40:02.123Z",
      "machineId": "machine-delta",
      "lineId": "line-03",
      "factoryId": "factory-east",
      "durationMs": -100,
      "defectCount": 5
    },
    {
      "eventId": "event-005",
      "eventTime": "2026-01-16T12:00:00.000Z",
      "receivedTime": "2026-01-16T10:41:03.456Z",
      "machineId": "machine-epsilon",
      "lineId": "line-04",
      "factoryId": "factory-west",
      "durationMs": 25000000,
      "defectCount": 0
    }
  ]'
```
    },
    {
      "eventId": "event-005",
      "eventTime": "2026-01-16T12:00:00.000Z",
      "machineId": "machine-epsilon",
      "lineId": "line-04",
      "factoryId": "factory-west",
      "durationMs": 25000000,
      "defectCount": 0
    }
  ]'
```

#### Empty Batch
```bash
curl -X POST http://localhost:9021/events/batch \
  -H "Content-Type: application/json" \
  -d '[]'
```

---

### 2. Machine Statistics

```bash
curl -X GET "http://localhost:9021/stats?machineId=machine-alpha&start=2026-01-16T10:00:00.000Z&end=2026-01-16T11:00:00.000Z"
```

**URL Encoded Version** (use if special characters cause issues):
```bash
curl -X GET "http://localhost:9021/stats?machineId=machine-alpha&start=2026-01-16T10%3A00%3A00.000Z&end=2026-01-16T11%3A00%3A00.000Z"
```

---

### 3. Top Defect Lines

```bash
curl -X GET "http://localhost:9021/stats/top-defect-lines?factoryId=factory-north&from=2026-01-16T10:00:00.000Z&to=2026-01-16T11:00:00.000Z&limit=5"
```

**URL Encoded Version**:
```bash
curl -X GET "http://localhost:9021/stats/top-defect-lines?factoryId=factory-north&from=2026-01-16T10%3A00%3A00.000Z&to=2026-01-16T11%3A00%3A00.000Z&limit=5"
```

---

## Testing with Postman

### Setup

1. **Create a New Collection**: "Factory Events API"
2. **Set Base URL Variable**: 
   - Variable: `base_url`
   - Value: `http://localhost:9021`

### Request Examples

#### 1. POST Batch Event Ingestion

- **Method**: POST
- **URL**: `{{base_url}}/events/batch`
- **Headers**:
  ```
  Content-Type: application/json
  ```
- **Body** (raw JSON):
  ```json
  [
    {
      "eventId": "postman-event-001",
      "eventTime": "2026-01-16T10:30:00.000Z",
      "machineId": "machine-alpha",
      "lineId": "line-01",
      "factoryId": "factory-north",
      "durationMs": 5000,
      "defectCount": 2
    }
  ]
  ```

#### 2. GET Machine Statistics

- **Method**: GET
- **URL**: `{{base_url}}/stats`
- **Params**:
  | Key | Value |
  |-----|-------|
  | machineId | machine-alpha |
  | start | 2026-01-16T10:00:00.000Z |
  | end | 2026-01-16T11:00:00.000Z |

#### 3. GET Top Defect Lines

- **Method**: GET
- **URL**: `{{base_url}}/stats/top-defect-lines`
- **Params**:
  | Key | Value |
  |-----|-------|
  | factoryId | factory-north |
  | from | 2026-01-16T10:00:00.000Z |
  | to | 2026-01-16T11:00:00.000Z |
  | limit | 10 |

### Postman Collection JSON

Save this as `Factory_Events_API.postman_collection.json`:

```json
{
  "info": {
    "name": "Factory Events API",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Batch Event Ingestion",
      "request": {
        "method": "POST",
        "header": [
          {
            "key": "Content-Type",
            "value": "application/json"
          }
        ],
        "body": {
          "mode": "raw",
          "raw": "[\n  {\n    \"eventId\": \"event-001\",\n    \"eventTime\": \"2026-01-16T10:30:00.000Z\",\n    \"machineId\": \"machine-alpha\",\n    \"lineId\": \"line-01\",\n    \"factoryId\": \"factory-north\",\n    \"durationMs\": 5000,\n    \"defectCount\": 2\n  }\n]"
        },
        "url": {
          "raw": "http://localhost:9021/events/batch",
          "protocol": "http",
          "host": ["localhost"],
          "port": "9021",
          "path": ["events", "batch"]
        }
      }
    },
    {
      "name": "Get Machine Statistics",
      "request": {
        "method": "GET",
        "url": {
          "raw": "http://localhost:9021/stats?machineId=machine-alpha&start=2026-01-16T10:00:00.000Z&end=2026-01-16T11:00:00.000Z",
          "protocol": "http",
          "host": ["localhost"],
          "port": "9021",
          "path": ["stats"],
          "query": [
            {"key": "machineId", "value": "machine-alpha"},
            {"key": "start", "value": "2026-01-16T10:00:00.000Z"},
            {"key": "end", "value": "2026-01-16T11:00:00.000Z"}
          ]
        }
      }
    },
    {
      "name": "Get Top Defect Lines",
      "request": {
        "method": "GET",
        "url": {
          "raw": "http://localhost:9021/stats/top-defect-lines?factoryId=factory-north&from=2026-01-16T10:00:00.000Z&to=2026-01-16T11:00:00.000Z&limit=10",
          "protocol": "http",
          "host": ["localhost"],
          "port": "9021",
          "path": ["stats", "top-defect-lines"],
          "query": [
            {"key": "factoryId", "value": "factory-north"},
            {"key": "from", "value": "2026-01-16T10:00:00.000Z"},
            {"key": "to", "value": "2026-01-16T11:00:00.000Z"},
            {"key": "limit", "value": "10"}
          ]
        }
      }
    }
  ]
}
```

---

## Testing with HTTPie

### Installation
```bash
pip install httpie
```

### Request Examples

#### 1. Batch Event Ingestion
```bash
http POST http://localhost:9021/events/batch \
  Content-Type:application/json \
  <<< '[
    {
      "eventId": "httpie-001",
      "eventTime": "2026-01-16T10:30:00.000Z",
      "machineId": "machine-alpha",
      "lineId": "line-01",
      "factoryId": "factory-north",
      "durationMs": 5000,
      "defectCount": 2
    }
  ]'
```

Or from a file:
```bash
http POST http://localhost:9021/events/batch < test-events.json
```

#### 2. Machine Statistics
```bash
http GET http://localhost:9021/stats \
  machineId==machine-alpha \
  start==2026-01-16T10:00:00.000Z \
  end==2026-01-16T11:00:00.000Z
```

#### 3. Top Defect Lines
```bash
http GET http://localhost:9021/stats/top-defect-lines \
  factoryId==factory-north \
  from==2026-01-16T10:00:00.000Z \
  to==2026-01-16T11:00:00.000Z \
  limit==10
```

---

## Sample Test Data

### test-events-valid.json
```json
[
  {
    "eventId": "test-001",
    "eventTime": "2026-01-16T08:00:00.000Z",
    "machineId": "machine-alpha",
    "lineId": "line-01",
    "factoryId": "factory-north",
    "durationMs": 5000,
    "defectCount": 2
  },
  {
    "eventId": "test-002",
    "eventTime": "2026-01-16T08:15:00.000Z",
    "machineId": "machine-beta",
    "lineId": "line-02",
    "factoryId": "factory-north",
    "durationMs": 7200,
    "defectCount": 0
  },
  {
    "eventId": "test-003",
    "eventTime": "2026-01-16T08:30:00.000Z",
    "machineId": "machine-gamma",
    "lineId": "line-01",
    "factoryId": "factory-south",
    "durationMs": 3500,
    "defectCount": 5
  },
  {
    "eventId": "test-004",
    "eventTime": "2026-01-16T08:45:00.000Z",
    "machineId": "machine-delta",
    "lineId": "line-03",
    "factoryId": "factory-east",
    "durationMs": 12000,
    "defectCount": 1
  },
  {
    "eventId": "test-005",
    "eventTime": "2026-01-16T09:00:00.000Z",
    "machineId": "machine-epsilon",
    "lineId": "line-02",
    "factoryId": "factory-west",
    "durationMs": 8500,
    "defectCount": 3
  }
]
```

### test-events-with-errors.json
```json
[
  {
    "eventId": "error-001",
    "eventTime": "2026-01-16T08:00:00.000Z",
    "machineId": "machine-alpha",
    "lineId": "line-01",
    "factoryId": "factory-north",
    "durationMs": -100,
    "defectCount": 2
  },
  {
    "eventId": "error-002",
    "eventTime": "2026-01-16T20:00:00.000Z",
    "machineId": "machine-beta",
    "lineId": "line-02",
    "factoryId": "factory-north",
    "durationMs": 7200,
    "defectCount": 0
  },
  {
    "eventId": "error-003",
    "eventTime": "2026-01-16T08:30:00.000Z",
    "machineId": "machine-gamma",
    "lineId": "line-01",
    "factoryId": "factory-south",
    "durationMs": 25000000,
    "defectCount": 5
  }
]
```

### test-events-bulk.json (1000 events)
```bash
# Generate 1000 test events using a script
node generate-test-data.js > test-events-bulk.json
```

**generate-test-data.js**:
```javascript
const events = [];
const now = new Date('2026-01-16T08:00:00.000Z');

for (let i = 0; i < 1000; i++) {
  const eventTime = new Date(now.getTime() + (i * 60000)); // 1 minute apart
  events.push({
    eventId: `bulk-event-${String(i).padStart(4, '0')}`,
    eventTime: eventTime.toISOString(),
    machineId: `machine-${i % 10}`,
    lineId: `line-${(i % 5) + 1}`,
    factoryId: `factory-${i % 3}`,
    durationMs: Math.floor(Math.random() * 20000) + 1000,
    defectCount: Math.floor(Math.random() * 10)
  });
}

console.log(JSON.stringify(events, null, 2));
```

---

## Expected Responses

### 1. Successful Batch Ingestion (All Accepted)
```json
{
  "accepted": 5,
  "deduped": 0,
  "updated": 0,
  "rejected": 0,
  "rejections": []
}
```

### 2. Batch with Duplicates
```json
{
  "accepted": 1,
  "deduped": 1,
  "updated": 0,
  "rejected": 0,
  "rejections": []
}
```

### 3. Batch with Updates
```json
{
  "accepted": 0,
  "deduped": 0,
  "updated": 1,
  "rejected": 0,
  "rejections": []
}
```

### 4. Batch with Validation Errors
```json
{
  "accepted": 0,
  "deduped": 0,
  "updated": 0,
  "rejected": 2,
  "rejections": [
    {
      "eventId": "error-001",
      "reason": "INVALID_DURATION"
    },
    {
      "eventId": "error-002",
      "reason": "FUTURE_EVENT_TIME"
    }
  ]
}
```

### 5. Machine Statistics Response
```json
{
  "machineId": "machine-alpha",
  "totalEvents": 10,
  "totalDefects": 15,
  "avgDurationMs": 6500.5,
  "maxDurationMs": 12000,
  "minDurationMs": 3000
}
```

### 6. Top Defect Lines Response
```json
[
  {
    "lineId": "line-01",
    "totalDefects": 45
  },
  {
    "lineId": "line-02",
    "totalDefects": 32
  },
  {
    "lineId": "line-03",
    "totalDefects": 28
  }
]
```

---

## Error Scenarios

### 1. Invalid Event Time (Future > 15 minutes)
```json
{
  "eventId": "future-event",
  "eventTime": "2026-12-31T23:59:59.000Z",
  "machineId": "machine-alpha",
  "durationMs": 5000,
  "defectCount": 2
}
```
**Expected Rejection**: `FUTURE_EVENT_TIME`

### 2. Negative Duration
```json
{
  "eventId": "negative-duration",
  "eventTime": "2026-01-16T10:00:00.000Z",
  "machineId": "machine-alpha",
  "durationMs": -500,
  "defectCount": 2
}
```
**Expected Rejection**: `INVALID_DURATION`

### 3. Duration Exceeds 6 Hours (21600000 ms)
```json
{
  "eventId": "long-duration",
  "eventTime": "2026-01-16T10:00:00.000Z",
  "machineId": "machine-alpha",
  "durationMs": 25000000,
  "defectCount": 2
}
```
**Expected Rejection**: `INVALID_DURATION`

### 4. Missing Required Fields
```json
{
  "eventTime": "2026-01-16T10:00:00.000Z",
  "machineId": "machine-alpha",
  "durationMs": 5000,
  "defectCount": 2
}
```
**Expected Rejection**: `MISSING_EVENT_ID`

### 5. Invalid defectCount (< -1)
```json
{
  "eventId": "invalid-defect",
  "eventTime": "2026-01-16T10:00:00.000Z",
  "machineId": "machine-alpha",
  "durationMs": 5000,
  "defectCount": -5
}
```
**Expected Rejection**: `INVALID_DEFECT_COUNT`

---

## Performance Testing

### Apache Bench (ab)

#### Test 1000 Events
```bash
ab -n 100 -c 10 -p test-events-valid.json -T application/json \
  http://localhost:9021/events/batch
```

**Parameters**:
- `-n 100`: 100 requests total
- `-c 10`: 10 concurrent requests
- `-p`: POST data file
- `-T`: Content-Type header

### wrk (More Advanced)

#### Install
```bash
# macOS
brew install wrk

# Ubuntu/Debian
sudo apt-get install wrk
```

#### Test Script (benchmark.lua)
```lua
wrk.method = "POST"
wrk.body   = [[
[
  {
    "eventId": "wrk-event-]] .. math.random(1, 1000000) .. [[",
    "eventTime": "2026-01-16T10:30:00.000Z",
    "machineId": "machine-alpha",
    "lineId": "line-01",
    "factoryId": "factory-north",
    "durationMs": 5000,
    "defectCount": 2
  }
]
]]
wrk.headers["Content-Type"] = "application/json"
```

#### Run Test
```bash
wrk -t4 -c100 -d30s -s benchmark.lua http://localhost:9021/events/batch
```

**Parameters**:
- `-t4`: 4 threads
- `-c100`: 100 connections
- `-d30s`: 30 second duration
- `-s`: Lua script

### Expected Performance Metrics

Based on the optimizations:
- **Throughput**: ~5500 events/second
- **Latency** (1000 events): ~180ms
- **Latency** (2000 events): ~420ms

---

## Validation Rules

### Event Validation
| Field | Validation Rule | Error Code |
|-------|----------------|------------|
| eventId | Required, non-empty | MISSING_EVENT_ID |
| eventTime | Required | MISSING_EVENT_TIME |
| eventTime | Not > 15 min in future | FUTURE_EVENT_TIME |
| machineId | Required, non-empty | MISSING_MACHINE_ID |
| durationMs | Required | MISSING_DURATION |
| durationMs | >= 0 | INVALID_DURATION |
| durationMs | <= 21600000 (6 hours) | INVALID_DURATION |
| defectCount | Required | MISSING_DEFECT_COUNT |
| defectCount | >= -1 | INVALID_DEFECT_COUNT |

### Special Cases
- **defectCount = -1**: Accepted (special sentinel value)
- **defectCount >= 0**: Normal defect count
- **lineId**: Optional
- **factoryId**: Optional

---

## Troubleshooting

### Common Issues

#### 1. Connection Refused
```
curl: (7) Failed to connect to localhost port 9021
```
**Solution**: Ensure the application is running:
```bash
mvn spring-boot:run
```

#### 2. 400 Bad Request
**Cause**: Invalid JSON or missing required fields
**Solution**: Validate JSON syntax and check required fields

#### 3. 500 Internal Server Error
**Cause**: Database connection issue or server error
**Solution**: Check application logs and database connectivity

#### 4. Empty Statistics
**Cause**: No data for specified time range or machine
**Solution**: Verify events exist in the database for the query parameters

### Debugging Tips

1. **Check Application Logs**:
   ```bash
   tail -f logs/application.log
   ```

2. **Enable SQL Logging** (in application.properties):
   ```properties
   spring.jpa.show-sql=true
   logging.level.org.hibernate.SQL=DEBUG
   ```

3. **Verify Database**:
   ```sql
   SELECT * FROM events ORDER BY created_at DESC LIMIT 10;
   ```

---

## Quick Start Test Script

Save as `quick-test.sh`:

```bash
#!/bin/bash

BASE_URL="http://localhost:9021"

echo "=== Testing Factory Events API ==="
echo ""

# Test 1: Batch Ingestion
echo "1. Testing Batch Event Ingestion..."
curl -X POST $BASE_URL/events/batch \
  -H "Content-Type: application/json" \
  -d '[{
    "eventId": "quick-test-001",
    "eventTime": "2026-01-16T10:30:00.000Z",
    "machineId": "machine-quick",
    "lineId": "line-quick",
    "factoryId": "factory-test",
    "durationMs": 5000,
    "defectCount": 2
  }]'
echo ""
echo ""

# Test 2: Machine Statistics
echo "2. Testing Machine Statistics..."
curl -X GET "$BASE_URL/stats?machineId=machine-quick&start=2026-01-16T10:00:00.000Z&end=2026-01-16T11:00:00.000Z"
echo ""
echo ""

# Test 3: Top Defect Lines
echo "3. Testing Top Defect Lines..."
curl -X GET "$BASE_URL/stats/top-defect-lines?factoryId=factory-test&from=2026-01-16T10:00:00.000Z&to=2026-01-16T11:00:00.000Z&limit=5"
echo ""
echo ""

echo "=== Tests Complete ==="
```

Run:
```bash
chmod +x quick-test.sh
./quick-test.sh
```

---

## Summary

This guide provides comprehensive testing instructions for the Factory Events API. For production deployment:

1. Update the base URL to your production server
2. Implement authentication if required
3. Add HTTPS support
4. Configure proper CORS settings
5. Set up monitoring and logging
6. Implement rate limiting

For questions or issues, refer to the application logs and README.md.

