package dev.springrag.indexing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * 출처 문서 → 분할 → 임베딩 → 벡터 저장소. 출처마다 대조.
 *
 * <ul>
 * <li>지문(제목 + 본문 SHA-256)이 같으면 임베딩 안 함</li>
 * <li>바뀐 문서: 새 조각을 넣은 뒤 옛 조각 삭제 (중간에 실패해도 문서가 비지 않음)</li>
 * <li>출처에서 사라진 문서: 삭제</li>
 * <li>목록을 받지 못했거나 0건이면 그 출처는 지우지 않음 (출처 장애로 전부 지워지는 것 방지)</li>
 * </ul>
 */
public class IndexingService {

	private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

	static final String SOURCE_ID = "source_id";

	static final String TITLE = "title";

	private final List<DocumentSource> sources;

	private final VectorStore vectorStore;

	private final IndexStore store;

	private final TokenTextSplitter splitter = TokenTextSplitter.builder().build();

	IndexingService(List<DocumentSource> sources, VectorStore vectorStore, IndexStore store) {
		this.sources = sources;
		this.vectorStore = vectorStore;
		this.store = store;
	}

	/**
	 * @param full true 면 지문이 같아도 다시 임베딩 (임베딩 모델을 바꿨을 때)
	 */
	public synchronized SyncReport sync(boolean full) {
		Counter counter = new Counter();
		for (DocumentSource source : this.sources) {
			syncSource(source, full, counter);
		}
		SyncReport report = new SyncReport(counter.documents, counter.embedded, counter.unchanged, counter.deleted,
				counter.failedSources);
		log.info("색인 끝: {}", report);
		return report;
	}

	private void syncSource(DocumentSource source, boolean full, Counter counter) {
		Map<String, IndexStore.Entry> known = this.store.findBySource(source.name());
		List<DocumentSource.SourceDocument> documents;
		try {
			documents = source.list();
		}
		catch (RuntimeException ex) {
			counter.failedSources++;
			log.warn("출처 {} 목록을 받지 못해 건너뜀 (기존 색인 유지): {}", source.name(), ex.toString());
			return;
		}
		if (documents.isEmpty() && !known.isEmpty()) {
			counter.failedSources++;
			log.warn("출처 {} 문서 0건. 기존 색인 {}건 유지", source.name(), known.size());
			return;
		}
		Set<String> present = new HashSet<>();
		for (DocumentSource.SourceDocument doc : documents) {
			if (!present.add(doc.id())) {
				continue;
			}
			counter.documents++;
			IndexStore.Entry previous = known.get(doc.id());
			String fingerprint = fingerprint(doc);
			if (!full && previous != null && previous.fingerprint().equals(fingerprint)) {
				counter.unchanged++;
				continue;
			}
			index(source, doc, fingerprint, previous);
			counter.embedded++;
		}
		for (IndexStore.Entry gone : known.values()) {
			if (!present.contains(gone.sourceId())) {
				remove(gone);
				counter.deleted++;
			}
		}
	}

	private void index(DocumentSource source, DocumentSource.SourceDocument doc, String fingerprint,
			IndexStore.Entry previous) {
		if (previous == null) {
			// 상태 기록 없이 남은 조각 (이전 버전 색인 등)
			this.vectorStore.delete(new FilterExpressionBuilder().eq(SOURCE_ID, doc.id()).build());
		}
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(SOURCE_ID, doc.id());
		metadata.put(TITLE, doc.title());
		List<Document> chunks = this.splitter.split(Document.builder().text(doc.text()).metadata(metadata).build());
		if (!chunks.isEmpty()) {
			this.vectorStore.add(chunks);
		}
		if (previous != null && !previous.vectorIds().isEmpty()) {
			this.vectorStore.delete(previous.vectorIds());
		}
		this.store.save(new IndexStore.Entry(doc.id(), source.name(), doc.title(), fingerprint,
				chunks.stream().map(Document::getId).toList()));
	}

	private void remove(IndexStore.Entry entry) {
		if (!entry.vectorIds().isEmpty()) {
			this.vectorStore.delete(entry.vectorIds());
		}
		this.store.delete(entry.sourceId());
	}

	static String fingerprint(DocumentSource.SourceDocument doc) {
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			sha.update(doc.title().getBytes(StandardCharsets.UTF_8));
			sha.update((byte) 0);
			sha.update(doc.text().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(sha.digest());
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static final class Counter {

		int documents;

		int embedded;

		int unchanged;

		int deleted;

		int failedSources;

	}

}
