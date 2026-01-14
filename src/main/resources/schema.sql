-- Factory Machine Events Table
CREATE TABLE IF NOT EXISTS machine_events (
    id BIGSERIAL PRIMARY KEY,
    machine_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    status VARCHAR(50),
    temperature DECIMAL(10, 2),
    pressure DECIMAL(10, 2),
    vibration DECIMAL(10, 2),
    error_code VARCHAR(50),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for optimizing common queries
CREATE INDEX IF NOT EXISTS idx_machine_events_machine_id ON machine_events(machine_id);
CREATE INDEX IF NOT EXISTS idx_machine_events_event_type ON machine_events(event_type);
CREATE INDEX IF NOT EXISTS idx_machine_events_timestamp ON machine_events(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_machine_events_status ON machine_events(status);
CREATE INDEX IF NOT EXISTS idx_machine_events_machine_timestamp ON machine_events(machine_id, timestamp DESC);

-- Composite index for common query patterns
CREATE INDEX IF NOT EXISTS idx_machine_events_composite ON machine_events(machine_id, event_type, timestamp DESC);

-- Comments for documentation
COMMENT ON TABLE machine_events IS 'Stores factory machine events and telemetry data';
COMMENT ON COLUMN machine_events.id IS 'Primary key, auto-generated';
COMMENT ON COLUMN machine_events.machine_id IS 'Unique identifier for the machine';
COMMENT ON COLUMN machine_events.event_type IS 'Type of event (e.g., START, STOP, ERROR, WARNING)';
COMMENT ON COLUMN machine_events.timestamp IS 'When the event occurred';
COMMENT ON COLUMN machine_events.status IS 'Current machine status (e.g., RUNNING, IDLE, ERROR)';
COMMENT ON COLUMN machine_events.temperature IS 'Machine temperature in Celsius';
COMMENT ON COLUMN machine_events.pressure IS 'Machine pressure in PSI';
COMMENT ON COLUMN machine_events.vibration IS 'Machine vibration level';
COMMENT ON COLUMN machine_events.error_code IS 'Error code if event_type is ERROR';
COMMENT ON COLUMN machine_events.error_message IS 'Detailed error message';
COMMENT ON COLUMN machine_events.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN machine_events.updated_at IS 'Record last update timestamp';
