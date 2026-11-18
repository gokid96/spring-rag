package dev.springrag.chat;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * 질의 경로. Spring AI 부품 조립.
 *
 * <pre>
 * 대화 메모리 → (후속 질문이면 재작성) → 벡터 검색 + 키워드 검색 → RRF → 리랭커 또는 상위 N → 문맥 추가 → LLM
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RerankProperties.class)
class ChatConfiguration {

	@Bean
	ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory,
			@Value("${rag.prompt.system}") Resource systemPrompt) throws IOException {
		return builder.defaultSystem(systemPrompt.getContentAsString(StandardCharsets.UTF_8))
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
			.build();
	}

	@Bean
	HybridDocumentRetriever hybridDocumentRetriever(VectorStore vectorStore, NamedParameterJdbcTemplate jdbc,
			@Value("${rag.search.vector-top-k:20}") int vectorTopK,
			@Value("${rag.search.keyword.enabled:true}") boolean keywordEnabled,
			@Value("${rag.search.keyword.top-k:20}") int keywordTopK,
			@Value("${rag.search.candidates:20}") int candidates) {
		VectorStoreDocumentRetriever vector = VectorStoreDocumentRetriever.builder()
			.vectorStore(vectorStore)
			.topK(vectorTopK)
			.build();
		return new HybridDocumentRetriever(vector, keywordEnabled ? new KeywordRetriever(jdbc, keywordTopK) : null,
				candidates);
	}

	/** 리랭커 ({@code rag.rerank.url}) 또는 검색 순서 상위 N 건 */
	@Bean
	DocumentPostProcessor documentPostProcessor(RerankProperties rerank, @Value("${rag.chat.top-k:5}") int topK) {
		if (!rerank.enabled()) {
			return (query, documents) -> HttpReranker.top(documents, topK);
		}
		HttpClient client = HttpClient.newBuilder().connectTimeout(rerank.timeout()).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
		factory.setReadTimeout(rerank.timeout());
		RestClient.Builder http = RestClient.builder()
			.requestFactory(factory)
			.baseUrl(rerank.url().strip().replaceAll("/+$", ""));
		if (rerank.apiKey() != null && !rerank.apiKey().isBlank()) {
			http.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + rerank.apiKey());
		}
		return new HttpReranker(http.build(), rerank.api(), rerank.model(), rerank.maxBatch(), topK);
	}

	/** 첫 질문: 그대로 검색 */
	@Bean
	RetrievalAugmentationAdvisor firstTurnRetrieval(HybridDocumentRetriever retriever,
			DocumentPostProcessor postProcessor) {
		return retrieval(List.of(), retriever, postProcessor);
	}

	/** 후속 질문: 앞 대화를 넣어 혼자 읽히는 질문으로 재작성 후 검색 ({@code rag.chat.rewrite}) */
	@Bean
	RetrievalAugmentationAdvisor followUpRetrieval(ChatClient.Builder builder, HybridDocumentRetriever retriever,
			DocumentPostProcessor postProcessor, @Value("${rag.chat.rewrite:true}") boolean rewrite) {
		List<QueryTransformer> transformers = new ArrayList<>();
		if (rewrite) {
			// 재작성 호출은 기본 어드바이저 없는 별도 ChatClient (Builder 빈은 주입마다 새것)
			transformers.add(CompressionQueryTransformer.builder().chatClientBuilder(builder).build());
		}
		return retrieval(transformers, retriever, postProcessor);
	}

	private static RetrievalAugmentationAdvisor retrieval(List<QueryTransformer> transformers,
			HybridDocumentRetriever retriever, DocumentPostProcessor postProcessor) {
		return RetrievalAugmentationAdvisor.builder()
			.queryTransformers(transformers)
			.documentRetriever(retriever)
			.documentPostProcessors(postProcessor)
			// 문서 없어도 답변. 운영 시 false + 빈 문맥 안내 권장
			.queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(true).build())
			.build();
	}

}
