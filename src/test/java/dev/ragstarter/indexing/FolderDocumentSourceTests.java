package dev.ragstarter.indexing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 폴더 출처 · 색인: 지원 파일만 읽음, 재색인 시 같은 문서의 이전 조각 삭제 후 추가.
 */
class FolderDocumentSourceTests {

	@Test
	void readsSupportedFilesRecursively(@TempDir Path dir) throws IOException {
		Files.createDirectories(dir.resolve("hr"));
		Files.writeString(dir.resolve("hr/leave.md"), "# 휴가\n신청 방법", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("notes.txt"), "메모", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("page.html"), "<html><body><p>웹 문서</p></body></html>", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("image.png"), "x", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("empty.md"), "  ", StandardCharsets.UTF_8);

		List<DocumentSource.SourceDocument> docs = new FolderDocumentSource(dir).list();

		assertThat(docs).extracting(DocumentSource.SourceDocument::id)
			.containsExactly("hr/leave.md", "notes.txt", "page.html");
		assertThat(docs.get(0).title()).isEqualTo("leave");
		assertThat(docs.get(2).text()).contains("웹 문서").doesNotContain("<p>");
	}

	@Test
	void missingFolderIsEmpty(@TempDir Path dir) {
		assertThat(new FolderDocumentSource(dir.resolve("none")).list()).isEmpty();
	}

	@Test
	void reindexingReplacesTheDocumentsChunks(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve("a.md"), "휴가는 사내 포털에서 신청합니다. 팀장이 승인하면 확정됩니다.", StandardCharsets.UTF_8);
		RecordingStore store = new RecordingStore();
		IndexingService indexing = new IndexingService(List.of(new FolderDocumentSource(dir)), store);

		indexing.indexAll();
		indexing.indexAll();

		assertThat(store.deletes).hasSize(2).allSatisfy(filter -> assertThat(filter.toString()).contains("a.md"));
		assertThat(store.added).hasSize(2)
			.allSatisfy(document -> assertThat(document.getMetadata()).containsEntry("source_id", "a.md"));
	}

	static final class RecordingStore implements VectorStore {

		final List<Filter.Expression> deletes = new ArrayList<>();

		final List<Document> added = new ArrayList<>();

		@Override
		public void add(List<Document> documents) {
			this.added.addAll(documents);
		}

		@Override
		public void delete(List<String> idList) {
		}

		@Override
		public void delete(Filter.Expression filterExpression) {
			this.deletes.add(filterExpression);
		}

		@Override
		public List<Document> similaritySearch(SearchRequest request) {
			return List.of();
		}

	}

}
