package dev.springrag.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * 질의 경로: 대화 메모리 → 벡터 검색 → 문맥 추가 → LLM. Spring AI 부품 조립.
 */
@Configuration(proxyBeanMethods = false)
class ChatConfiguration {

	@Bean
	ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, VectorStore vectorStore,
			@Value("${rag.prompt.system}") Resource systemPrompt, @Value("${rag.chat.top-k:5}") int topK)
			throws IOException {
		RetrievalAugmentationAdvisor retrieval = RetrievalAugmentationAdvisor.builder()
			.documentRetriever(VectorStoreDocumentRetriever.builder().vectorStore(vectorStore).topK(topK).build())
			// 문서 없어도 답변. 운영 시 false + 빈 문맥 안내 권장
			.queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(true).build())
			.build();
		return builder.defaultSystem(systemPrompt.getContentAsString(StandardCharsets.UTF_8))
			.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(), retrieval)
			.build();
	}

}
