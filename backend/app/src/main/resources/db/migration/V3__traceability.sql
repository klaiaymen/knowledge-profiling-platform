CREATE TABLE trace_entity (
    id UUID PRIMARY KEY,
    source_type VARCHAR(40) NOT NULL,
    external_id VARCHAR(500) NOT NULL,
    repository VARCHAR(300) NOT NULL DEFAULT '',
    title VARCHAR(1000) NOT NULL,
    path VARCHAR(2000) NOT NULL DEFAULT '',
    url VARCHAR(2000),
    content TEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_type, external_id)
);
CREATE INDEX trace_entity_path_idx ON trace_entity(path);
CREATE INDEX trace_entity_repository_idx ON trace_entity(repository);

CREATE TABLE trace_link (
    id BIGSERIAL PRIMARY KEY,
    repository VARCHAR(300) NOT NULL DEFAULT '',
    from_entity UUID NOT NULL REFERENCES trace_entity(id) ON DELETE CASCADE,
    to_entity UUID NOT NULL REFERENCES trace_entity(id) ON DELETE CASCADE,
    relationship VARCHAR(40) NOT NULL,
    justification TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (from_entity, to_entity, relationship)
);
CREATE INDEX trace_link_from_idx ON trace_link(from_entity);
CREATE INDEX trace_link_to_idx ON trace_link(to_entity);

CREATE TABLE trace_vector_chunk (
    entity_id UUID NOT NULL REFERENCES trace_entity(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    vector_id VARCHAR(100) NOT NULL UNIQUE,
    PRIMARY KEY(entity_id, chunk_index)
);

CREATE TABLE trace_connector_state (
    connector VARCHAR(80) PRIMARY KEY,
    last_success_at TIMESTAMPTZ,
    last_item_count INTEGER NOT NULL DEFAULT 0
);
