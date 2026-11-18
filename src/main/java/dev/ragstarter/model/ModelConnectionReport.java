package dev.ragstarter.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 시 연결된 모델 서버 로그 (키 제외).
 */
@Component
class ModelConnectionReport implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ModelConnectionReport.class);

	private final String chat;

	private final String embedding;

	ModelConnectionReport(@Value("${spring.ai.openai.chat.base-url}") String chatUrl,
			@Value("${spring.ai.openai.chat.model}") String chatModel,
			@Value("${spring.ai.openai.embedding.base-url}") String embeddingUrl,
			@Value("${spring.ai.openai.embedding.model}") String embeddingModel) {
		this.chat = chatModel + " @ " + chatUrl;
		this.embedding = embeddingModel + " @ " + embeddingUrl;
	}

	@Override
	public void run(ApplicationArguments args) {
		log.info("채팅 모델: {}", this.chat);
		log.info("임베딩 모델: {}", this.embedding);
	}

	String chat() {
		return this.chat;
	}

	String embedding() {
		return this.embedding;
	}

}
