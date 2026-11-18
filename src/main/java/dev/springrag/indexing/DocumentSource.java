package dev.springrag.indexing;

import java.util.List;

/**
 * 색인 문서 출처. 빈으로 등록하면 {@link IndexingService} 가 사용.
 */
public interface DocumentSource {

	/** 이 출처의 모든 문서 */
	List<SourceDocument> list();

	/**
	 * @param id 출처 안에서 유일한 id
	 * @param text 본문 (Markdown · 글)
	 */
	record SourceDocument(String id, String title, String text) {
	}

}
