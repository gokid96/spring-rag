package dev.springrag.indexing;

import java.util.List;
import java.util.Map;

/**
 * 색인 상태 저장소. 문서마다 지문과 벡터 id.
 */
interface IndexStore {

	/** 출처의 색인된 문서 (source_id → 상태) */
	Map<String, Entry> findBySource(String source);

	void save(Entry entry);

	void delete(String sourceId);

	record Entry(String sourceId, String source, String title, String fingerprint, List<String> vectorIds) {
	}

}
