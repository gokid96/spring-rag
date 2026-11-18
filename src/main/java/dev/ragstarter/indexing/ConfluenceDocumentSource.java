package dev.ragstarter.indexing;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.client.RestClient;

/**
 * Confluence 출처. 설정한 space 의 현재 페이지를 가져와 본문(storage 형식 XHTML)에서 글자만 뽑음.
 *
 * <ul>
 * <li>id: {@code confluence:<page id>}</li>
 * <li>Cloud: REST API v2 ({@code /api/v2/spaces/{id}/pages}), base-url 예 {@code https://acme.atlassian.net/wiki},
 * 인증 Basic (이메일 : API 토큰)</li>
 * <li>Data Center · Server: REST API v1 ({@code /rest/api/content}), base-url 예 {@code https://wiki.acme.com},
 * 인증 Bearer (개인 액세스 토큰)</li>
 * </ul>
 */
public class ConfluenceDocumentSource implements DocumentSource {

	private static final Logger log = LoggerFactory.getLogger(ConfluenceDocumentSource.class);

	static final int CLOUD_LIMIT = 250;

	static final int DATACENTER_LIMIT = 50;

	public enum Deployment {

		CLOUD, DATACENTER

	}

	private final RestClient http;

	private final String baseUrl;

	private final Deployment deployment;

	private final List<String> spaces;

	/**
	 * @param http base-url 과 인증 헤더를 넣은 클라이언트
	 * @param baseUrl http 의 base-url (다음 페이지 주소 조립용)
	 * @param spaces 색인할 space 키 (예: HR, DEV)
	 */
	public ConfluenceDocumentSource(RestClient http, String baseUrl, Deployment deployment, List<String> spaces) {
		this.http = http;
		this.baseUrl = baseUrl.replaceAll("/+$", "");
		this.deployment = deployment;
		this.spaces = List.copyOf(spaces);
	}

	@Override
	public List<SourceDocument> list() {
		List<SourceDocument> documents = new ArrayList<>();
		for (String space : this.spaces) {
			List<Map<?, ?>> pages = (this.deployment == Deployment.CLOUD) ? cloudPages(space) : dataCenterPages(space);
			for (Map<?, ?> page : pages) {
				String text = FolderDocumentSource.htmlText(Json.text(Json.path(page, "body", "storage", "value")));
				if (!text.isBlank()) {
					documents.add(new SourceDocument("confluence:" + Json.text(page.get("id")),
							Json.text(page.get("title")), text));
				}
			}
		}
		log.info("Confluence space {}: 페이지 {}개", this.spaces, documents.size());
		return documents;
	}

	/** v2: space 키 → id, 이후 cursor 로 페이지 넘김 ({@code _links.next}) */
	private List<Map<?, ?>> cloudPages(String spaceKey) {
		Map<String, Object> spaces = this.http.get().uri("/api/v2/spaces?keys={key}", spaceKey).retrieve().body(Json.MAP);
		List<?> found = Json.list(Json.path(spaces, "results"));
		if (found.isEmpty()) {
			throw new IllegalStateException("Confluence space 를 찾을 수 없음: " + spaceKey);
		}
		String spaceId = Json.text(Json.map(found.getFirst()).get("id"));
		List<Map<?, ?>> pages = new ArrayList<>();
		Map<String, Object> body = this.http.get()
			.uri("/api/v2/spaces/{id}/pages?body-format=storage&status=current&limit={limit}", spaceId, CLOUD_LIMIT)
			.retrieve()
			.body(Json.MAP);
		while (true) {
			Json.list(Json.path(body, "results")).forEach(page -> pages.add(Json.map(page)));
			String next = nextPath(Json.text(Json.path(body, "_links", "next")));
			if (next == null) {
				return pages;
			}
			body = this.http.get().uri(URI.create(this.baseUrl + next)).retrieve().body(Json.MAP);
		}
	}

	/** v1: start · limit 로 페이지 넘김 */
	private List<Map<?, ?>> dataCenterPages(String spaceKey) {
		List<Map<?, ?>> pages = new ArrayList<>();
		while (true) {
			Map<String, Object> body = this.http.get()
				.uri("/rest/api/content?spaceKey={key}&type=page&status=current&expand=body.storage&limit={limit}&start={start}",
						spaceKey, DATACENTER_LIMIT, pages.size())
				.retrieve()
				.body(Json.MAP);
			List<?> results = Json.list(Json.path(body, "results"));
			results.forEach(page -> pages.add(Json.map(page)));
			if (results.size() < DATACENTER_LIMIT) {
				return pages;
			}
		}
	}

	/**
	 * Cloud 의 {@code _links.next} 는 사이트 기준 경로 ({@code /wiki/api/v2/...}). base-url 이 {@code /wiki} 로 끝나므로
	 * 앞의 {@code /wiki} 를 떼고 상대 경로로 씀.
	 */
	static @Nullable String nextPath(String next) {
		if (next.isBlank()) {
			return null;
		}
		return next.startsWith("/wiki/") ? next.substring("/wiki".length()) : next;
	}

}
