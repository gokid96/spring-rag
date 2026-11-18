-- 벡터 저장소 · 대화 메모리 (Spring AI 표준 테이블). 차원은 EMBEDDING_DIMENSIONS.
-- 임베딩 모델을 차원이 다른 것으로 바꾸면: DROP SCHEMA spring_rag CASCADE; 후 다시 기동 · 색인
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

CREATE TABLE vector_store (
    id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    content   text,
    metadata  json,
    embedding public.vector(${embedding_dimensions})
);

CREATE INDEX vector_store_embedding_idx ON vector_store USING hnsw (embedding public.vector_cosine_ops);

CREATE TABLE spring_ai_chat_memory (
    conversation_id varchar(36) NOT NULL,
    content         text        NOT NULL,
    type            varchar(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"     timestamp   NOT NULL,
    sequence_id     bigint      NOT NULL
);

CREATE INDEX spring_ai_chat_memory_conversation_idx ON spring_ai_chat_memory (conversation_id, sequence_id);
