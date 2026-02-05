-- Dynamic Documents table with JSONB for schemaless data storage
CREATE TABLE IF NOT EXISTS dynamic_documents (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(255) NOT NULL,
    data JSONB DEFAULT '{}',
    version BIGINT DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE,
    latest_request_id VARCHAR(255),
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE,
    last_modified_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_dynamic_documents_table_name ON dynamic_documents(table_name);
CREATE INDEX IF NOT EXISTS idx_dynamic_documents_table_name_not_deleted ON dynamic_documents(table_name, is_deleted);
CREATE INDEX IF NOT EXISTS idx_dynamic_documents_last_modified ON dynamic_documents(table_name, last_modified_at);

-- GIN index for JSONB queries
CREATE INDEX IF NOT EXISTS idx_dynamic_documents_data ON dynamic_documents USING GIN (data);

-- Sequence Checkpoints table for tracking change streams
CREATE TABLE IF NOT EXISTS sequence_checkpoints (
    id VARCHAR(255) PRIMARY KEY,
    collection_name VARCHAR(255) NOT NULL UNIQUE,
    sequence BIGINT DEFAULT 0,
    resume_token TEXT,
    last_updated BIGINT
);

CREATE INDEX IF NOT EXISTS idx_sequence_checkpoints_collection ON sequence_checkpoints(collection_name);
