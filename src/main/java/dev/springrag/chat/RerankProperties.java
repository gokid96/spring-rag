package dev.springrag.chat;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 리랭커 ({@code rag.rerank.*}). url 이 비면 끔.
 *
 * @param url 리랭커 주소. {@code /rerank} 를 붙여 호출
 * @param api {@code tei} (Hugging Face TEI) 또는 {@code cohere} (Cohere · Jina · vLLM 형식)
 * @param model {@code cohere} 형식일 때 모델 이름
 * @param apiKey 있으면 {@code Authorization: Bearer}
 * @param maxBatch 한 번에 보내는 문서 수
 */
@ConfigurationProperties("rag.rerank")
record RerankProperties(@Nullable String url, @DefaultValue("tei") Api api, @Nullable String model,
		@Nullable String apiKey, @DefaultValue("32") int maxBatch, @DefaultValue("30s") Duration timeout) {

	enum Api {

		TEI, COHERE

	}

	boolean enabled() {
		return this.url != null && !this.url.isBlank();
	}

}
