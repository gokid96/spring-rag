package dev.springrag.indexing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.client.RestClient;

/**
 * BookStack 출처. 설정한 shelf 의 book → page 를 Markdown 으로 가져옴.
 *
 * <ul>
 * <li>id: {@code bookstack:<page id>}</li>
 * <li>인증: API 토큰 ({@code Authorization: Token <id>:<secret>})</li>
 * <li>초안(draft) 페이지 제외</li>
 * </ul>
 */
public class BookStackDocumentSource implements DocumentSource {

	private static final Logger log = LoggerFactory.getLogger(BookStackDocumentSource.class);

	static final int PAGE_SIZE = 500;

	private final RestClient http;

	private final List<Long> shelves;

	/**
	 * @param http base-url 과 인증 헤더를 넣은 클라이언트
	 * @param shelves 색인할 shelf id
	 */
	public BookStackDocumentSource(RestClient http, List<Long> shelves) {
		this.http = http;
		this.shelves = List.copyOf(shelves);
	}

	@Override
	public String name() {
		return "bookstack";
	}

	@Override
	public List<SourceDocument> list() {
		List<SourceDocument> documents = new ArrayList<>();
		for (long shelf : this.shelves) {
			Map<String, Object> body = this.http.get().uri("/api/shelves/{id}", shelf).retrieve().body(Json.MAP);
			for (Object book : Json.list(Json.path(body, "books"))) {
				long bookId = Long.parseLong(Json.text(Json.map(book).get("id")));
				for (Map<?, ?> page : pages(bookId)) {
					if (Boolean.TRUE.equals(page.get("draft"))) {
						continue;
					}
					String id = Json.text(page.get("id"));
					// 내보내기는 문자 집합 없이 오므로 UTF-8 로 직접 읽음
					byte[] bytes = this.http.get()
						.uri("/api/pages/{id}/export/markdown", id)
						.retrieve()
						.body(byte[].class);
					String markdown = (bytes == null) ? "" : new String(bytes, StandardCharsets.UTF_8);
					if (!markdown.isBlank()) {
						documents.add(new SourceDocument("bookstack:" + id, Json.text(page.get("name")), markdown));
					}
				}
			}
		}
		log.info("BookStack shelf {}: 페이지 {}개", this.shelves, documents.size());
		return documents;
	}

	private List<Map<?, ?>> pages(long bookId) {
		List<Map<?, ?>> pages = new ArrayList<>();
		while (true) {
			Map<String, Object> body = this.http.get()
				.uri("/api/pages?filter[book_id]={book}&count={count}&offset={offset}", bookId, PAGE_SIZE, pages.size())
				.retrieve()
				.body(Json.MAP);
			List<?> data = Json.list(Json.path(body, "data"));
			data.forEach(page -> pages.add(Json.map(page)));
			long total = Long.parseLong(Json.text(Json.path(body, "total")).isEmpty() ? "0"
					: Json.text(Json.path(body, "total")));
			if (data.isEmpty() || pages.size() >= total) {
				return pages;
			}
		}
	}

}
