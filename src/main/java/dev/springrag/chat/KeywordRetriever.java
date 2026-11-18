package dev.springrag.chat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 키워드 검색 (PostgreSQL 전문 검색, {@code vector_store.keyword_search}). 오류 코드 · 제품 코드 · 메뉴 이름처럼
 * 임베딩이 놓치기 쉬운 정확한 낱말 보완.
 *
 * <p>
 * 낱말 하나라도 맞으면 후보. 모두 맞는 조각 먼저, 다음 {@code ts_rank} 순. 질문 말투 낱말은 빼고 한국어 조사 몇 개는 뗌.
 * 형태소 분석은 없음 (문서 쪽 "휴가를" 과 질문 "휴가" 는 안 맞음).
 */
final class KeywordRetriever {

	private static final TypeReference<Map<String, Object>> METADATA = new TypeReference<>() {
	};

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Pattern TERM = Pattern.compile("[\\p{L}\\p{N}]+(?:[-_][\\p{L}\\p{N}]+)*");

	private static final Pattern KOREAN_PARTICLE = Pattern
		.compile("(?<=[가-힣]{2})(에서|으로|부터|까지|은|는|을|를|의|이|가|에|와|과|도)$");

	private static final Set<String> QUESTION_WORDS = Set.of("어떻게", "어디", "어디서", "어디에서", "뭐", "뭔가요", "무엇",
			"알려줘", "알려주세요", "해주세요", "해줘", "주세요", "하나요", "하죠", "있나요", "대해", "관련", "방법", "절차", "문의",
			"안내", "설명", "그리고", "what", "how", "where", "when", "why", "which", "the", "is", "are", "do", "does",
			"can", "please", "about", "for", "and", "with");

	static final int MAX_TERMS = 12;

	private static final String SEARCH = """
			SELECT id, content, metadata
			FROM vector_store
			CROSS JOIN (
			    SELECT websearch_to_tsquery('simple', :anyTerms) AS any_terms,
			           plainto_tsquery('simple', :allTerms) AS all_terms
			) q
			WHERE keyword_search @@ q.any_terms
			ORDER BY (keyword_search @@ q.all_terms) DESC, ts_rank(keyword_search, q.any_terms) DESC, id
			LIMIT :topK
			""";

	private final NamedParameterJdbcTemplate jdbc;

	private final int topK;

	KeywordRetriever(NamedParameterJdbcTemplate jdbc, int topK) {
		this.jdbc = jdbc;
		this.topK = topK;
	}

	List<Document> retrieve(String question) {
		List<String> terms = keywords(question);
		if (terms.isEmpty()) {
			return List.of();
		}
		String anyTerms = terms.stream().map(term -> "\"" + term + "\"").collect(Collectors.joining(" OR "));
		return this.jdbc.query(SEARCH,
				Map.of("anyTerms", anyTerms, "allTerms", String.join(" ", terms), "topK", this.topK),
				(rs, row) -> Document.builder()
					.id(rs.getString("id"))
					.text(rs.getString("content"))
					.metadata(JSON.readValue(rs.getString("metadata"), METADATA))
					.build());
	}

	/** 질문 → 검색 낱말 (소문자, 한 글자 · 질문 말투 뺌, 조사 뗌, 최대 12개) */
	static List<String> keywords(String question) {
		return TERM.matcher(question.toLowerCase(Locale.ROOT))
			.results()
			.map(match -> match.group())
			.filter(term -> term.length() > 1 && !QUESTION_WORDS.contains(term))
			.map(term -> KOREAN_PARTICLE.matcher(term).replaceFirst(""))
			.filter(term -> !QUESTION_WORDS.contains(term))
			.distinct()
			.limit(MAX_TERMS)
			.toList();
	}

}
