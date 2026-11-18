package dev.springrag.chat;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 질문 답변. 같은 대화 id 는 이전 대화 기억. 대화 기록이 있으면 후속 질문 경로(재작성).
 */
@Service
class ChatService {

	private final ChatClient chatClient;

	private final ChatMemory chatMemory;

	private final RetrievalAugmentationAdvisor firstTurn;

	private final RetrievalAugmentationAdvisor followUp;

	ChatService(ChatClient chatClient, ChatMemory chatMemory,
			@Qualifier("firstTurnRetrieval") RetrievalAugmentationAdvisor firstTurn,
			@Qualifier("followUpRetrieval") RetrievalAugmentationAdvisor followUp) {
		this.chatClient = chatClient;
		this.chatMemory = chatMemory;
		this.firstTurn = firstTurn;
		this.followUp = followUp;
	}

	Flux<String> answer(String conversationId, String question) {
		boolean followUp = !this.chatMemory.get(conversationId).isEmpty();
		return this.chatClient.prompt()
			.user(question)
			.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId)
				.param(HybridDocumentRetriever.ORIGINAL_QUESTION, question)
				.advisors(followUp ? this.followUp : this.firstTurn))
			.stream()
			.content();
	}

}
