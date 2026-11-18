package dev.springrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring RAG. 하위 패키지 = 모듈 (Spring Modulith).
 *
 * <pre>
 * model     모델 연결 (외부 API · Bifrost · Ollama 를 설정만으로 교체)
 * chat      질문 → 검색 → 답변 스트리밍 (POST /api/chat)
 * indexing  문서 넣기 (TODO: 출처 연결)
 * session   세션 · 권한 (TODO)
 * </pre>
 */
@SpringBootApplication
public class SpringRagApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringRagApplication.class, args);
	}

}
