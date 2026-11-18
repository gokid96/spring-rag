-- 키워드 검색 (하이브리드). 제목 가중치 A, 본문 B. 'simple' 사전: 형태소 분석 없이 낱말 그대로
ALTER TABLE vector_store ADD COLUMN keyword_search tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(metadata ->> 'title', '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(content, '')), 'B')
    ) STORED;

CREATE INDEX vector_store_keyword_search_idx ON vector_store USING gin (keyword_search);
