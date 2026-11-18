package dev.springrag.chat;

import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code POST /api/chat} 본문 {@code {"question", "conversation_id"?}} → SSE {@code token {text}} … {@code done
 * {conversation_id}} 또는 {@code error {message}}.
 *
 * <p>
 * TODO: 대화 id 를 요청에서 받지 말고 세션(session 모듈)에서 정하기
 */
@RestController
class ChatController {

	private final ChatService chat;

	ChatController(ChatService chat) {
		this.chat = chat;
	}

	@PostMapping(path = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	Flux<ServerSentEvent<Map<String, Object>>> chat(@RequestBody ChatRequest request) {
		String question = (request.question() == null) ? "" : request.question().strip();
		if (question.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "질문을 입력해 주세요.");
		}
		String conversationId = (request.conversationId() != null) ? request.conversationId()
				: UUID.randomUUID().toString();
		return this.chat.answer(conversationId, question)
			.map(text -> event("token", Map.of("text", text)))
			.concatWith(Mono.just(event("done", Map.of("conversation_id", conversationId))))
			.onErrorResume(error -> Mono.just(event("error", Map.of("message", "답변을 만드는 중 문제가 생겼습니다."))));
	}

	private static ServerSentEvent<Map<String, Object>> event(String name, Map<String, Object> data) {
		return ServerSentEvent.builder(data).event(name).build();
	}

	record ChatRequest(@Nullable String question,
			@com.fasterxml.jackson.annotation.JsonProperty("conversation_id") @Nullable String conversationId) {
	}

}
