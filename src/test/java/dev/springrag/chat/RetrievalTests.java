package dev.springrag.chat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 하이브리드 검색(RRF) · 키워드 추출 · 리랭커.
 */
class RetrievalTests {

	@Test
	void fusionRanksDocumentsFoundByBothSearchesFirst() {
		List<Document> vector = List.of(doc("a", 0.9), doc("b", 0.8), doc("c", 0.7));
		List<Document> keyword = List.of(doc("c", null), doc("d", null));

		List<Document> fused = HybridDocumentRetriever.fuse(List.of(vector, keyword), 4);

		// c: 양쪽 → 맨 위. 같은 순위(b, d)는 먼저 나온 순
		assertThat(fused).extracting(Document::getId).containsExactly("c", "a", "b", "d");
		// score 는 RRF 점수 (Spring AI 문서 합치기가 score 로 다시 정렬해도 순서 유지)
		assertThat(fused.get(0).getScore()).isGreaterThan(fused.get(1).getScore());
		assertThat(fused.get(0).getMetadata()).containsEntry(HybridDocumentRetriever.VECTOR_SCORE, 0.7);
		assertThat(fused.get(3).getMetadata()).doesNotContainKey(HybridDocumentRetriever.VECTOR_SCORE);
	}

	@Test
	void keywordSearchUsesOriginalQuestionUnlessItPointsBack() {
		// 원래 질문 우선
		assertThat(HybridDocumentRetriever.keywordQuestion("AN-1042 오류 해결", "AN-1042 오류는 어떻게 해결하나요"))
			.isEqualTo("AN-1042 오류 해결");
		// 앞 대화를 가리키면 재작성한 질문
		assertThat(HybridDocumentRetriever.keywordQuestion("그럼 그건 누가 승인해?", "출장 신청은 누가 승인하나요"))
			.isEqualTo("출장 신청은 누가 승인하나요");
	}

	@Test
	void extractsSearchTerms() {
		assertThat(KeywordRetriever.keywords("AN-1042 오류는 어떻게 해결하나요?"))
			.containsExactly("an-1042", "오류", "해결하나요");
		assertThat(KeywordRetriever.keywords("휴가를 어디에서 신청해?")).containsExactly("휴가", "신청해");
		assertThat(KeywordRetriever.keywords("어떻게 해?")).isEmpty();
	}

	@Test
	void teiRerankerReordersAndKeepsTopN() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://rerank");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://rerank/rerank"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.query").value("재작성 질문"))
			.andExpect(jsonPath("$.texts.length()").value(3))
			.andRespond(withSuccess("[{\"index\":2,\"score\":0.9},{\"index\":0,\"score\":0.5},{\"index\":1,\"score\":0.1}]",
					MediaType.APPLICATION_JSON));
		HttpReranker reranker = new HttpReranker(builder.build(), RerankProperties.Api.TEI, null, 32, 2);

		List<Document> top = reranker.process(query("원래 질문", "재작성 질문"), List.of(doc("a", 0.0), doc("b", 0.0), doc("c", 0.0)));

		assertThat(top).extracting(Document::getId).containsExactly("c", "a");
		assertThat(top.get(0).getMetadata()).containsEntry(HttpReranker.RERANK_SCORE, 0.9);
		server.verify();
	}

	@Test
	void cohereFormatReranker() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://rerank/v1");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://rerank/v1/rerank"))
			.andExpect(jsonPath("$.model").value("bge-reranker-v2-m3"))
			.andExpect(jsonPath("$.documents.length()").value(2))
			.andRespond(withSuccess("{\"results\":[{\"index\":1,\"relevance_score\":0.8},{\"index\":0,\"relevance_score\":0.2}]}",
					MediaType.APPLICATION_JSON));
		HttpReranker reranker = new HttpReranker(builder.build(), RerankProperties.Api.COHERE, "bge-reranker-v2-m3", 32,
				5);

		assertThat(reranker.process(query("q", null), List.of(doc("a", 0.0), doc("b", 0.0)))).extracting(Document::getId)
			.containsExactly("b", "a");
	}

	@Test
	void rerankerFailureFallsBackToSearchOrder() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://rerank");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://rerank/rerank")).andRespond(withServerError());
		HttpReranker reranker = new HttpReranker(builder.build(), RerankProperties.Api.TEI, null, 32, 2);

		assertThat(reranker.process(query("q", null), List.of(doc("a", 0.0), doc("b", 0.0), doc("c", 0.0))))
			.extracting(Document::getId)
			.containsExactly("a", "b");
	}

	private static Query query(String text, String searchQuery) {
		Map<String, Object> context = new HashMap<>();
		if (searchQuery != null) {
			context.put(HybridDocumentRetriever.SEARCH_QUERY, searchQuery);
		}
		return Query.builder().text(text).context(context).build();
	}

	private static Document doc(String id, Double score) {
		return Document.builder().id(id).text("본문 " + id).score(score).build();
	}

}
