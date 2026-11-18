package dev.ragstarter.indexing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;

/**
 * 기본 출처: 폴더 ({@code RAG_DOCS_PATH}), 하위 폴더 포함.
 *
 * <ul>
 * <li>md · txt: UTF-8 그대로, html: 태그 제거, pdf: PDFBox</li>
 * <li>docx · pptx · xlsx · hwp: Apache Tika 가 classpath 에 있을 때만
 * ({@code build.gradle.kts} 의 spring-ai-tika-document-reader 줄).</li>
 * <li>id: 폴더 내 상대 경로, 제목: 파일 이름</li>
 * <li>읽기 실패 파일은 건너뜀 (로그)</li>
 * </ul>
 */
public class FolderDocumentSource implements DocumentSource {

	private static final Logger log = LoggerFactory.getLogger(FolderDocumentSource.class);

	static final Set<String> PLAIN = Set.of("md", "markdown", "txt");

	static final Set<String> HTML = Set.of("html", "htm");

	static final Set<String> TIKA = Set.of("docx", "doc", "pptx", "ppt", "xlsx", "xls", "hwp", "hwpx", "odt", "rtf");

	private static final String TIKA_READER = "org.springframework.ai.reader.tika.TikaDocumentReader";

	static final boolean TIKA_PRESENT = ClassUtils.isPresent(TIKA_READER, FolderDocumentSource.class.getClassLoader());

	private final Path root;

	public FolderDocumentSource(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	@Override
	public List<SourceDocument> list() {
		if (!Files.isDirectory(this.root)) {
			log.warn("문서 폴더가 없습니다: {} (RAG_DOCS_PATH)", this.root);
			return List.of();
		}
		List<Path> files;
		try (Stream<Path> walk = Files.walk(this.root)) {
			files = walk.filter(Files::isRegularFile).filter(path -> supported(path)).sorted().toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("문서 폴더를 읽지 못했습니다: " + this.root, ex);
		}
		List<SourceDocument> documents = new ArrayList<>(files.size());
		for (Path file : files) {
			try {
				String text = read(file);
				if (!text.isBlank()) {
					documents.add(new SourceDocument(id(file), title(file), text));
				}
			}
			catch (RuntimeException | IOException ex) {
				log.warn("파일을 건너뜁니다 {}: {}", file, ex.getMessage());
			}
		}
		return documents;
	}

	String id(Path file) {
		return this.root.relativize(file).toString().replace('\\', '/');
	}

	static String title(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return (dot > 0) ? name.substring(0, dot) : name;
	}

	static boolean supported(Path file) {
		String ext = extension(file);
		return PLAIN.contains(ext) || HTML.contains(ext) || ext.equals("pdf") || (TIKA_PRESENT && TIKA.contains(ext));
	}

	private static String read(Path file) throws IOException {
		String ext = extension(file);
		if (PLAIN.contains(ext)) {
			return Files.readString(file, StandardCharsets.UTF_8);
		}
		if (HTML.contains(ext)) {
			return htmlText(Files.readString(file, StandardCharsets.UTF_8));
		}
		Resource resource = new FileSystemResource(file);
		DocumentReader reader = ext.equals("pdf") ? new PagePdfDocumentReader(resource) : tika(resource);
		return reader.get()
			.stream()
			.map(Document::getText)
			.filter(text -> text != null && !text.isBlank())
			.collect(Collectors.joining("\n"));
	}

	/** 태그 · 스크립트를 지운 글자 */
	static String htmlText(String html) {
		return html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
			.replaceAll("(?s)<[^>]+>", " ")
			.replace("&nbsp;", " ")
			.replace("&lt;", "<")
			.replace("&gt;", ">")
			.replace("&amp;", "&")
			.replaceAll("[ \\t]+", " ")
			.strip();
	}

	private static DocumentReader tika(Resource resource) {
		try {
			return (DocumentReader) ClassUtils.forName(TIKA_READER, FolderDocumentSource.class.getClassLoader())
				.getConstructor(Resource.class)
				.newInstance(resource);
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Tika 읽기 도구를 만들지 못했습니다", ex);
		}
	}

	private static String extension(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return (dot < 0) ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

}
