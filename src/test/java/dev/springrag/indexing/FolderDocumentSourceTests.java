package dev.springrag.indexing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 폴더 출처: 지원 파일만 읽음.
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

}
