-- 색인 상태. 지문(fingerprint)이 같으면 다시 임베딩하지 않음
CREATE TABLE indexed_document (
    source_id   text        PRIMARY KEY,
    source      text        NOT NULL,
    title       text        NOT NULL,
    fingerprint text        NOT NULL,
    vector_ids  text[]      NOT NULL,
    indexed_at  timestamptz NOT NULL
);

CREATE INDEX indexed_document_source_idx ON indexed_document (source);
