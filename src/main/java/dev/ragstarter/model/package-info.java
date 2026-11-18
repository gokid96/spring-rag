/**
 * 모델 연결 모듈. 코드는 Spring AI {@code ChatModel} · {@code EmbeddingModel} 만 사용, 서버는 설정으로 지정
 * ({@code application-*.yml}). 채팅 · 임베딩 서버 분리 가능.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package dev.ragstarter.model;
