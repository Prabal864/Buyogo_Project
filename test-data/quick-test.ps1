# Factory Events API - Quick Test Script
# PowerShell version for Windows

$BASE_URL = "http://localhost:9021"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Factory Events API - Quick Test Suite" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: Batch Ingestion - Valid Events
Write-Host "Test 1: Batch Event Ingestion (Valid)" -ForegroundColor Yellow
Write-Host "----------------------------------------"
$body1 = @'
[{
  "eventId": "quick-test-001",
  "eventTime": "2026-01-16T10:30:00.000Z",
  "receivedTime": "2026-01-16T10:30:05.123Z",
  "machineId": "machine-quick",
  "lineId": "line-quick",
  "factoryId": "factory-test",
  "durationMs": 5000,
  "defectCount": 2
},{
  "eventId": "quick-test-002",
  "eventTime": "2026-01-16T10:31:00.000Z",
  "receivedTime": "2026-01-16T10:31:03.456Z",
  "machineId": "machine-quick",
  "lineId": "line-quick",
  "factoryId": "factory-test",
  "durationMs": 6000,
  "defectCount": 1
}]
'@
$response1 = Invoke-RestMethod -Uri "$BASE_URL/events/batch" -Method Post -Body $body1 -ContentType "application/json"
$response1 | ConvertTo-Json -Depth 10
Write-Host ""

# Test 2: Batch Ingestion - Duplicate Events
Write-Host "Test 2: Batch Event Ingestion (Duplicates)" -ForegroundColor Yellow
Write-Host "----------------------------------------"
$body2 = @'
[{
  "eventId": "quick-test-001",
  "eventTime": "2026-01-16T10:30:00.000Z",
  "receivedTime": "2026-01-16T10:30:05.123Z",
  "machineId": "machine-quick",
  "lineId": "line-quick",
  "factoryId": "factory-test",
  "durationMs": 5000,
  "defectCount": 2
}]
'@
$response2 = Invoke-RestMethod -Uri "$BASE_URL/events/batch" -Method Post -Body $body2 -ContentType "application/json"
$response2 | ConvertTo-Json -Depth 10
Write-Host ""

# Test 3: Batch Ingestion - Invalid Events
Write-Host "Test 3: Batch Event Ingestion (Invalid)" -ForegroundColor Yellow
Write-Host "----------------------------------------"
$body3 = @'
[{
  "eventId": "quick-test-invalid-001",
  "eventTime": "2026-01-16T10:30:00.000Z",
  "receivedTime": "2026-01-16T10:30:02.789Z",
  "machineId": "machine-quick",
  "lineId": "line-quick",
  "factoryId": "factory-test",
  "durationMs": -100,
  "defectCount": 2
}]
'@
  "eventId": "quick-test-invalid-001",
  "eventTime": "2026-01-16T10:30:00.000Z",
  "machineId": "machine-quick",
  "lineId": "line-quick",
  "factoryId": "factory-test",
  "durationMs": -100,
  "defectCount": 2
}]
'@
$response3 = Invoke-RestMethod -Uri "$BASE_URL/events/batch" -Method Post -Body $body3 -ContentType "application/json"
$response3 | ConvertTo-Json -Depth 10
Write-Host ""

# Test 4: Machine Statistics
Write-Host "Test 4: Machine Statistics" -ForegroundColor Yellow
Write-Host "----------------------------------------"
$params4 = @{
    machineId = "machine-quick"
    start = "2026-01-16T10:00:00.000Z"
    end = "2026-01-16T11:00:00.000Z"
}
$queryString4 = ($params4.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "&"
$response4 = Invoke-RestMethod -Uri "$BASE_URL/stats?$queryString4" -Method Get
$response4 | ConvertTo-Json -Depth 10
Write-Host ""

# Test 5: Top Defect Lines
Write-Host "Test 5: Top Defect Lines" -ForegroundColor Yellow
Write-Host "----------------------------------------"
$params5 = @{
    factoryId = "factory-test"
    from = "2026-01-16T10:00:00.000Z"
    to = "2026-01-16T11:00:00.000Z"
    limit = 5
}
$queryString5 = ($params5.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "&"
$response5 = Invoke-RestMethod -Uri "$BASE_URL/stats/top-defect-lines?$queryString5" -Method Get
$response5 | ConvertTo-Json -Depth 10
Write-Host ""

# Test 6: Empty Batch
Write-Host "Test 6: Empty Batch" -ForegroundColor Yellow
Write-Host "----------------------------------------"
$body6 = "[]"
$response6 = Invoke-RestMethod -Uri "$BASE_URL/events/batch" -Method Post -Body $body6 -ContentType "application/json"
$response6 | ConvertTo-Json -Depth 10
Write-Host ""

Write-Host "=========================================" -ForegroundColor Green
Write-Host "  All Tests Complete!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green

