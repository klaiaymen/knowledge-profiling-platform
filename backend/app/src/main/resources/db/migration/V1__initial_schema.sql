CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS source_document (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    location TEXT NOT NULL,
    content TEXT NOT NULL,
    source_type TEXT NOT NULL,
    embedding vector(384)
);

-- Investor profiles intentionally have no persistence table in the session-only MVP.
