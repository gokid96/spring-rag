package dev.ragstarter.indexing;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * BookStack · Confluence 출처: 목록 · 페이지 넘김 · 본문 추출. HTTP 는 가짜 서버.
 */
class RemoteSourcesTests {

	@Test
	void bookStackReadsShelfBooksAndPagesSkippingDrafts() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://wiki.test")
			.defaultHeader("Authorization", "Token id:secret");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://wiki.test/api/shelves/7"))
			.andExpect(header("Authorization", "Token id:secret"))
			.andRespond(json("{\"books\":[{\"id\":3,\"name\":\"매뉴얼\"}]}"));
		server.expect(requestTo("https://wiki.test/api/pages?filter%5Bbook_id%5D=3&count=500&offset=0"))
			.andRespond(json("{\"data\":[{\"id\":10,\"name\":\"설치\",\"draft\":false},"
					+ "{\"id\":11,\"name\":\"초안\",\"draft\":true}],\"total\":2}"));
		server.expect(requestTo("https://wiki.test/api/pages/10/export/markdown"))
			.andRespond(withSuccess("# 설치\n설치 방법".getBytes(java.nio.charset.StandardCharsets.UTF_8),
					MediaType.APPLICATION_OCTET_STREAM));

		List<DocumentSource.SourceDocument> docs = new BookStackDocumentSource(builder.build(), List.of(7L)).list();

		assertThat(docs).containsExactly(new DocumentSource.SourceDocument("bookstack:10", "설치", "# 설치\n설치 방법"));
		server.verify();
	}

	@Test
	void confluenceCloudUsesV2WithCursorPaging() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://acme.atlassian.net/wiki");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://acme.atlassian.net/wiki/api/v2/spaces?keys=HR"))
			.andRespond(json("{\"results\":[{\"id\":\"98\",\"key\":\"HR\"}]}"));
		server.expect(requestTo(
				"https://acme.atlassian.net/wiki/api/v2/spaces/98/pages?body-format=storage&status=current&limit=250"))
			.andRespond(json("{\"results\":[" + page("1", "휴가", "<p>휴가 <b>규정</b></p>") + "],"
					+ "\"_links\":{\"next\":\"/wiki/api/v2/spaces/98/pages?cursor=abc%3D&limit=250\"}}"));
		server.expect(requestTo("https://acme.atlassian.net/wiki/api/v2/spaces/98/pages?cursor=abc%3D&limit=250"))
			.andRespond(json("{\"results\":[" + page("2", "출장", "<ac:structured-macro>x</ac:structured-macro><p>출장비</p>")
					+ "],\"_links\":{}}"));

		List<DocumentSource.SourceDocument> docs = new ConfluenceDocumentSource(builder.build(),
				"https://acme.atlassian.net/wiki", ConfluenceDocumentSource.Deployment.CLOUD, List.of("HR"))
			.list();

		assertThat(docs).extracting(DocumentSource.SourceDocument::id).containsExactly("confluence:1", "confluence:2");
		assertThat(docs.get(0).text()).isEqualTo("휴가 규정");
		assertThat(docs.get(1).text()).contains("출장비").doesNotContain("<");
		server.verify();
	}

	@Test
	void confluenceDataCenterUsesV1WithStartPaging() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://wiki.acme.test");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		StringBuilder full = new StringBuilder();
		for (int i = 0; i < ConfluenceDocumentSource.DATACENTER_LIMIT; i++) {
			full.append(i == 0 ? "" : ",").append(page("p" + i, "제목" + i, "<p>본문" + i + "</p>"));
		}
		server.expect(method(HttpMethod.GET))
			.andExpect(requestTo(
					"https://wiki.acme.test/rest/api/content?spaceKey=DEV&type=page&status=current&expand=body.storage&limit=50&start=0"))
			.andRespond(json("{\"results\":[" + full + "]}"));
		server.expect(requestTo(
				"https://wiki.acme.test/rest/api/content?spaceKey=DEV&type=page&status=current&expand=body.storage&limit=50&start=50"))
			.andRespond(json("{\"results\":[" + page("last", "마지막", "<p>끝</p>") + "]}"));

		List<DocumentSource.SourceDocument> docs = new ConfluenceDocumentSource(builder.build(),
				"https://wiki.acme.test", ConfluenceDocumentSource.Deployment.DATACENTER, List.of("DEV"))
			.list();

		assertThat(docs).hasSize(51);
		assertThat(docs.getLast().id()).isEqualTo("confluence:last");
		server.verify();
	}

	@Test
	void nextLinkDropsTheWikiPrefix() {
		assertThat(ConfluenceDocumentSource.nextPath("/wiki/api/v2/x?cursor=1")).isEqualTo("/api/v2/x?cursor=1");
		assertThat(ConfluenceDocumentSource.nextPath("")).isNull();
	}

	private static String page(String id, String title, String html) {
		return "{\"id\":\"" + id + "\",\"title\":\"" + title + "\",\"body\":{\"storage\":{\"value\":\""
				+ html.replace("\"", "\\\"") + "\"}}}";
	}

	private static org.springframework.test.web.client.ResponseCreator json(String body) {
		return withSuccess(body, MediaType.APPLICATION_JSON);
	}

}
