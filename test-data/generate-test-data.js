const events = [];
const now = new Date('2026-01-16T08:00:00.000Z');

for (let i = 0; i < 1000; i++) {
  const eventTime = new Date(now.getTime() + (i * 60000)); // 1 minute apart
  const receivedTime = new Date(eventTime.getTime() + Math.floor(Math.random() * 5000)); // 0-5 seconds after event

  events.push({
    eventId: `bulk-event-${String(i).padStart(4, '0')}`,
    eventTime: eventTime.toISOString(),
    receivedTime: receivedTime.toISOString(),
    machineId: `machine-${i % 10}`,
    lineId: `line-${(i % 5) + 1}`,
    factoryId: `factory-${i % 3}`,
    durationMs: Math.floor(Math.random() * 20000) + 1000,
    defectCount: Math.floor(Math.random() * 10)
  });
}

console.log(JSON.stringify(events, null, 2));

