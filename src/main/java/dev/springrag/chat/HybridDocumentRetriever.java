package dev.springrag.chat;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

/**
 * 벡터 검색 + 키워드 검색 → RRF(Reciprocal Rank Fusion). 키워드 검색이 없으면 벡터 검색만.
 *
 * <p>
 * 벡터 검색은 재작성한 질문, 키워드 검색은 원래 질문. 원래 질문이 "그럼 그거는?" 처럼 앞 대화를 가리키면 재작성한 질문.
 */
final class HybridDocumentRetriever implements DocumentRetriever {

	/** 질의 문맥 키: 원래 질문 */
	static final String ORIGINAL_QUESTION = "rag_original_question";

	/** 질의 문맥 키: 검색에 쓴 질문 (재작성 뒤). 리랭커가 씀 */
	static final String SEARCH_QUERY = "rag_search_query";

	/** 문서 메타데이터 키: 벡터 유사도 (키워드로만 찾았으면 없음) */
	static final String VECTOR_SCORE = "vector_score";

	private static final int RANK_CONSTANT = 60;

	private static final Pattern CONTEXT_REFERENCE = Pattern
		.compile("^(그럼|그러면|그거|그것|그|이|해당|위|앞서|아까|방금|then|that|it|this)(?=\\s|$)", Pattern.CASE_INSENSITIVE);

	private final DocumentRetriever vector;

	private final @Nullable KeywordRetriever keyword;

	private final int topK;

	HybridDocumentRetriever(DocumentRetriever vector, @Nullable KeywordRetriever keyword, int topK) {
		this.vector = vector;
		this.keyword = keyword;
		this.topK = topK;
	}

	@Override
	public List<Document> retrieve(Query query) {
		put(query, SEARCH_QUERY, query.text());
		List<Document> vectorResults = this.vector.retrieve(query);
		if (this.keyword == null) {
			return fuse(List.of(vectorResults), this.topK);
		}
		String original = (query.context().get(ORIGINAL_QUESTION) instanceof String text) ? text : null;
		return fuse(List.of(vectorResults, this.keyword.retrieve(keywordQuestion(original, query.text()))), this.topK);
	}

	/** 키워드 검색 질문: 원래 질문. 비었거나 앞 대화를 가리키면 재작성한 질문 */
	static String keywordQuestion(@Nullable String original, String rewritten) {
		return (original != null && !original.isBlank() && !CONTEXT_REFERENCE.matcher(original.strip()).find())
				? original : rewritten;
	}

	/**
	 * 순위 r 마다 1/(60 + r) 을 더해 높은 순.
	 *
	 * <p>
	 * {@code score} 를 RRF 점수로 바꿈. Spring AI 문서 합치기({@code ConcatenationDocumentJoiner})가 {@code score} 로
	 * 다시 정렬하므로, 벡터 유사도를 두면 RRF 순서가 깨지고 키워드로만 찾은 조각이 맨 뒤로 밀림. 벡터 유사도는 메타데이터
	 * {@link #VECTOR_SCORE}.
	 */
	static List<Document> fuse(List<List<Document>> rankings, int topK) {
		Map<String, Document> documents = new LinkedHashMap<>();
		Map<String, Double> scores = new LinkedHashMap<>();
		for (List<Document> results : rankings) {
			for (int index = 0; index < results.size(); index++) {
				Document document = results.get(index);
				documents.putIfAbsent(document.getId(), document);
				scores.merge(document.getId(), 1.0 / (RANK_CONSTANT + index + 1), Double::sum);
			}
		}
		return documents.values()
			.stream()
			.sorted(Comparator.comparingDouble((Document document) -> scores.get(document.getId())).reversed())
			.limit(topK)
			.map(document -> withScore(document, scores.get(document.getId())))
			.toList();
	}

	private static Document withScore(Document document, double score) {
		Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
		if (document.getScore() != null) {
			metadata.put(VECTOR_SCORE, document.getScore());
		}
		return document.mutate().metadata(metadata).score(score).build();
	}

	static void put(Query query, String key, Object value) {
		try {
			query.context().put(key, value);
		}
		catch (UnsupportedOperationException ex) {
			// 어드바이저 밖에서 직접 만든 질의 (문맥 변경 불가)
		}
	}

}
