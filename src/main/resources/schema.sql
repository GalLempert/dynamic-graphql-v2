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
    last_modified_at TIMESTAMP WITH TIME ZONE,
    sequence_number BIGINT NOT NULL DEFAULT 0
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_dynamic_documents_table_name ON dynamic_documents(table_name);
CREATE INDEX IF NOT EXISTS idx_dynamic_documents_table_name_not_deleted ON dynamic_documents(table_name, is_deleted);
CREATE INDEX IF NOT EXISTS idx_dynamic_documents_last_modified ON dynamic_documents(table_name, last_modified_at);
CREATE INDEX IF NOT EXISTS idx_dynamic_documents_sequence ON dynamic_documents(table_name, sequence_number);

-- GIN index for JSONB queries
CREATE INDEX IF NOT EXISTS idx_dynamic_documents_data ON dynamic_documents USING GIN (data);

-- Sequence for auto-incrementing sequence_number on insert/update
CREATE SEQUENCE IF NOT EXISTS dynamic_documents_seq_num START WITH 1 INCREMENT BY 1;

-- Function to update sequence_number on insert or update
CREATE OR REPLACE FUNCTION update_sequence_number()
RETURNS TRIGGER AS $$
BEGIN
    NEW.sequence_number := nextval('dynamic_documents_seq_num');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to automatically set sequence_number on insert or update
DROP TRIGGER IF EXISTS trg_update_sequence_number ON dynamic_documents;
CREATE TRIGGER trg_update_sequence_number
    BEFORE INSERT OR UPDATE ON dynamic_documents
    FOR EACH ROW
    EXECUTE FUNCTION update_sequence_number();
