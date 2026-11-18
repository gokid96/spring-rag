package dev.springrag.chat;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * 리랭커. 검색 후보를 질문과 다시 비교해 상위 N 건. 실패하면 검색 순서 상위 N 건으로 계속 답함.
 *
 * <ul>
 * <li>{@code tei}: {@code POST /rerank {query, texts}} → {@code [{index, score}]}</li>
 * <li>{@code cohere}: {@code POST /rerank {model, query, documents}} → {@code {results: [{index, relevance_score}]}}</li>
 * </ul>
 */
final class HttpReranker implements DocumentPostProcessor {

	private static final Logger log = LoggerFactory.getLogger(HttpReranker.class);

	/** 문서 메타데이터 키: 리랭커 점수 */
	static final String RERANK_SCORE = "rerank_score";

	private final RestClient http;

	private final RerankProperties.Api api;

	private final @Nullable String model;

	private final int maxBatch;

	private final int topN;

	HttpReranker(RestClient http, RerankProperties.Api api, @Nullable String model, int maxBatch, int topN) {
		this.http = http;
		this.api = api;
		this.model = model;
		this.maxBatch = maxBatch;
		this.topN = topN;
	}

	@Override
	public List<Document> process(Query query, List<Document> documents) {
		if (documents.isEmpty()) {
			return documents;
		}
		String question = (query.context().get(HybridDocumentRetriever.SEARCH_QUERY) instanceof String text
				&& !text.isBlank()) ? text : query.text();
		try {
			double[] scores = score(question, documents.stream().map(d -> Objects.requireNonNullElse(d.getText(), "")).toList());
			return IntStream.range(0, documents.size())
				.boxed()
				.sorted(Comparator.comparingDouble((Integer i) -> scores[i]).reversed())
				.limit(this.topN)
				.map(i -> withScore(documents.get(i), scores[i]))
				.toList();
		}
		catch (RuntimeException ex) {
			log.warn("리랭커 실패, 검색 순서 상위 {}건 사용: {}", this.topN, ex.toString());
			return top(documents, this.topN);
		}
	}

	private double[] score(String question, List<String> texts) {
		double[] scores = new double[texts.size()];
		Arrays.fill(scores, Double.NaN);
		for (int from = 0; from < texts.size(); from += this.maxBatch) {
			List<String> batch = texts.subList(from, Math.min(from + this.maxBatch, texts.size()));
			for (Hit hit : call(question, batch)) {
				if (hit.index() < 0 || hit.index() >= batch.size()) {
					throw new IllegalStateException("리랭커 응답 index 범위 밖: " + hit.index());
				}
				scores[from + hit.index()] = hit.score();
			}
		}
		for (double score : scores) {
			if (Double.isNaN(score)) {
				throw new IllegalStateException("리랭커 응답에 빠진 문서 있음");
			}
		}
		return scores;
	}

	private List<Hit> call(String question, List<String> batch) {
		RestClient.RequestBodySpec request = this.http.post().uri("/rerank").contentType(MediaType.APPLICATION_JSON);
		return switch (this.api) {
			case TEI -> {
				TeiHit[] hits = request.body(Map.of("query", question, "texts", batch, "truncate", true))
					.retrieve()
					.body(TeiHit[].class);
				yield Arrays.stream(Objects.requireNonNull(hits, "빈 응답")).map(h -> new Hit(h.index(), h.score())).toList();
			}
			case COHERE -> {
				Map<String, Object> body = new LinkedHashMap<>();
				if (this.model != null && !this.model.isBlank()) {
					body.put("model", this.model);
				}
				body.put("query", question);
				body.put("documents", batch);
				CohereResponse response = request.body(body).retrieve().body(CohereResponse.class);
				yield Objects.requireNonNull(response, "빈 응답")
					.results()
					.stream()
					.map(r -> new Hit(r.index(), r.relevanceScore()))
					.toList();
			}
		};
	}

	static List<Document> top(List<Document> documents, int n) {
		return List.copyOf(documents.subList(0, Math.min(n, documents.size())));
	}

	private static Document withScore(Document document, double score) {
		Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
		metadata.put(RERANK_SCORE, score);
		return document.mutate().metadata(metadata).build();
	}

	record Hit(int index, double score) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TeiHit(int index, double score) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record CohereResponse(List<CohereResult> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record CohereResult(int index, @JsonProperty("relevance_score") double relevanceScore) {
	}

}
