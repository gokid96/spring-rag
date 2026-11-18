/**
 * 채팅 모듈. 질문 → 벡터 검색 → LLM 답변 스트리밍.
 *
 * <p>
 * TODO: 세션별 검색 범위(session), 하이브리드 검색 · 리랭크, 개인정보 가리기, 금칙어, 턴 기록
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package dev.ragstarter.chat;
