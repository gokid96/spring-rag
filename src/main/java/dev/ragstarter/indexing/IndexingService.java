package dev.ragstarter.indexing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

/**
 * 출처 문서 분할 → 벡터 저장소. 같은 {@code source_id} 이전 조각 삭제 후 추가 (재실행해도 중복 없음).
 *
 * <p>
 * TODO: 바뀐 문서만 다시 임베딩(지문 비교), 출처에서 사라진 문서 지우기, 임베딩 배치 크기 · 간격
 */
@Service
public class IndexingService {

	private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

	static final String SOURCE_ID = "source_id";

	static final String TITLE = "title";

	private final List<DocumentSource> sources;

	private final VectorStore vectorStore;

	private final TokenTextSplitter splitter = TokenTextSplitter.builder().build();

	IndexingService(List<DocumentSource> sources, VectorStore vectorStore) {
		this.sources = sources;
		this.vectorStore = vectorStore;
	}

	/** @return 넣은 조각 수 */
	public synchronized int indexAll() {
		int documents = 0;
		int chunks = 0;
		for (DocumentSource source : this.sources) {
			for (DocumentSource.SourceDocument doc : source.list()) {
				chunks += index(doc);
				documents++;
			}
		}
		log.info("색인 끝: 문서 {}개, 조각 {}개", documents, chunks);
		return chunks;
	}

	int index(DocumentSource.SourceDocument doc) {
		this.vectorStore.delete(new FilterExpressionBuilder().eq(SOURCE_ID, doc.id()).build());
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(SOURCE_ID, doc.id());
		metadata.put(TITLE, doc.title());
		List<Document> chunks = this.splitter.split(Document.builder().text(doc.text()).metadata(metadata).build());
		if (!chunks.isEmpty()) {
			this.vectorStore.add(chunks);
		}
		return chunks.size();
	}

}
