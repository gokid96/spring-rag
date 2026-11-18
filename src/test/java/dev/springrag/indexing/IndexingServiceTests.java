package dev.springrag.indexing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 바뀐 문서만 임베딩, 사라진 문서 삭제, 출처 장애 시 유지.
 */
class IndexingServiceTests {

	private final MemoryVectorStore vectors = new MemoryVectorStore();

	private final MemoryIndexStore states = new MemoryIndexStore();

	private final FakeSource source = new FakeSource();

	private final IndexingService indexing = new IndexingService(List.of(this.source), this.vectors, this.states);

	@Test
	void unchangedDocumentsAreNotEmbeddedAgain() {
		this.source.put("a", "휴가", "포털에서 신청");
		this.source.put("b", "출장", "결재 후 출발");

		assertThat(this.indexing.sync(false)).isEqualTo(new SyncReport(2, 2, 0, 0, 0));
		int added = this.vectors.addCalls;

		assertThat(this.indexing.sync(false)).isEqualTo(new SyncReport(2, 0, 2, 0, 0));
		assertThat(this.vectors.addCalls).isEqualTo(added);
	}

	@Test
	void changedDocumentReplacesItsChunks() {
		this.source.put("a", "휴가", "포털에서 신청");
		this.indexing.sync(false);

		this.source.put("a", "휴가", "메일로 신청");
		assertThat(this.indexing.sync(false)).isEqualTo(new SyncReport(1, 1, 0, 0, 0));

		assertThat(this.vectors.texts()).containsExactly("메일로 신청");
	}

	@Test
	void removedDocumentIsDeleted() {
		this.source.put("a", "휴가", "포털에서 신청");
		this.source.put("b", "출장", "결재 후 출발");
		this.indexing.sync(false);

		this.source.docs.remove("b");
		assertThat(this.indexing.sync(false)).isEqualTo(new SyncReport(1, 0, 1, 1, 0));

		assertThat(this.vectors.texts()).containsExactly("포털에서 신청");
		assertThat(this.states.entries).containsOnlyKeys("a");
	}

	@Test
	void failingOrEmptySourceKeepsTheIndex() {
		this.source.put("a", "휴가", "포털에서 신청");
		this.indexing.sync(false);

		this.source.fail = true;
		assertThat(this.indexing.sync(false).failedSources()).isEqualTo(1);
		this.source.fail = false;
		this.source.docs.clear();
		assertThat(this.indexing.sync(false).failedSources()).isEqualTo(1);

		assertThat(this.vectors.texts()).containsExactly("포털에서 신청");
	}

	@Test
	void fullSyncEmbedsEverything() {
		this.source.put("a", "휴가", "포털에서 신청");
		this.indexing.sync(false);

		assertThat(this.indexing.sync(true)).isEqualTo(new SyncReport(1, 1, 0, 0, 0));
		assertThat(this.vectors.texts()).containsExactly("포털에서 신청");
	}

	static final class FakeSource implements DocumentSource {

		final Map<String, SourceDocument> docs = new LinkedHashMap<>();

		boolean fail;

		void put(String id, String title, String text) {
			this.docs.put(id, new SourceDocument(id, title, text));
		}

		@Override
		public String name() {
			return "fake";
		}

		@Override
		public List<SourceDocument> list() {
			if (this.fail) {
				throw new IllegalStateException("down");
			}
			return List.copyOf(this.docs.values());
		}

	}

	static final class MemoryVectorStore implements VectorStore {

		final Map<String, Document> documents = new LinkedHashMap<>();

		int addCalls;

		List<String> texts() {
			return this.documents.values().stream().map(Document::getText).toList();
		}

		@Override
		public void add(List<Document> documents) {
			this.addCalls++;
			documents.forEach(document -> this.documents.put(document.getId(), document));
		}

		@Override
		public void delete(List<String> idList) {
			idList.forEach(this.documents::remove);
		}

		@Override
		public void delete(Filter.Expression filterExpression) {
		}

		@Override
		public List<Document> similaritySearch(SearchRequest request) {
			return List.of();
		}

	}

	static final class MemoryIndexStore implements IndexStore {

		final Map<String, Entry> entries = new HashMap<>();

		@Override
		public Map<String, Entry> findBySource(String source) {
			Map<String, Entry> found = new LinkedHashMap<>();
			this.entries.values().stream().filter(entry -> entry.source().equals(source))
				.forEach(entry -> found.put(entry.sourceId(), entry));
			return found;
		}

		@Override
		public void save(Entry entry) {
			this.entries.put(entry.sourceId(), new Entry(entry.sourceId(), entry.source(), entry.title(),
					entry.fingerprint(), new ArrayList<>(entry.vectorIds())));
		}

		@Override
		public void delete(String sourceId) {
			this.entries.remove(sourceId);
		}

	}

}
