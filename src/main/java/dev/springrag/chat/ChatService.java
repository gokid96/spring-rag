package dev.springrag.chat;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

/**
 * 질문 답변. 같은 대화 id 는 이전 대화 기억.
 */
@Service
class ChatService {

	private final ChatClient chatClient;

	ChatService(ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	Flux<String> answer(String conversationId, String question) {
		return this.chatClient.prompt()
			.user(question)
			.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
			.stream()
			.content();
	}

}
