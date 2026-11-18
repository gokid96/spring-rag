/**
 * 채팅 모듈. 질문 → (후속 질문 재작성) → 벡터 + 키워드 검색 → RRF → 리랭커 → LLM 답변 스트리밍.
 *
 * <p>
 * TODO: 세션별 검색 범위(session), 개인정보 가리기, 금칙어, 턴 기록
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package dev.springrag.chat;
