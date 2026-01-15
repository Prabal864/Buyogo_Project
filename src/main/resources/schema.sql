-- Factory Machine Events Table
CREATE TABLE IF NOT EXISTS events (
    event_id VARCHAR(255) PRIMARY KEY,
    event_time TIMESTAMP NOT NULL,
    received_time TIMESTAMP NOT NULL,
    machine_id VARCHAR(255) NOT NULL,
    line_id VARCHAR(255),
    factory_id VARCHAR(255),
    duration_ms BIGINT NOT NULL,
    defect_count INTEGER NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for optimizing common queries
CREATE INDEX IF NOT EXISTS idx_machine_event_time ON events(machine_id, event_time);
CREATE INDEX IF NOT EXISTS idx_factory_line_time ON events(factory_id, line_id, event_time);
CREATE INDEX IF NOT EXISTS idx_event_time ON events(event_time);
CREATE INDEX IF NOT EXISTS idx_received_time ON events(received_time);

-- Comments for documentation
COMMENT ON TABLE events IS 'Stores factory machine events with deduplication support';
COMMENT ON COLUMN events.event_id IS 'Unique event identifier for deduplication';
COMMENT ON COLUMN events.event_time IS 'When the event actually occurred (used for queries)';
COMMENT ON COLUMN events.received_time IS 'When the event was received by the system';
COMMENT ON COLUMN events.machine_id IS 'Machine identifier';
COMMENT ON COLUMN events.line_id IS 'Production line identifier';
COMMENT ON COLUMN events.factory_id IS 'Factory identifier';
COMMENT ON COLUMN events.duration_ms IS 'Event duration in milliseconds';
COMMENT ON COLUMN events.defect_count IS 'Number of defects (-1 means unknown)';
COMMENT ON COLUMN events.payload_hash IS 'Hash of payload for duplicate detection';
COMMENT ON COLUMN events.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN events.updated_at IS 'Record last update timestamp';
