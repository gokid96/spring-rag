package dev.springrag.indexing;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 선택 출처 설정. {@code enabled=true} 일 때만 사용.
 */
final class SourceProperties {

	private SourceProperties() {
	}

	/**
	 * @param baseUrl 예 https://wiki.acme.com
	 * @param token API 토큰 {@code <id>:<secret>}
	 * @param shelves 색인할 shelf id
	 * @param minInterval 요청 간격 (BookStack API 기본 한도 분당 180회)
	 */
	@ConfigurationProperties("rag.bookstack")
	record BookStack(@DefaultValue("false") boolean enabled, @DefaultValue("") String baseUrl,
			@DefaultValue("") String token, @DefaultValue List<Long> shelves,
			@DefaultValue("400ms") Duration minInterval) {
	}

	/**
	 * @param baseUrl Cloud 예 https://acme.atlassian.net/wiki, Data Center 예 https://wiki.acme.com
	 * @param email Cloud 계정 이메일 (Data Center 는 비움)
	 * @param token Cloud: API 토큰, Data Center: 개인 액세스 토큰
	 * @param spaces 색인할 space 키
	 * @param minInterval 요청 간격
	 */
	@ConfigurationProperties("rag.confluence")
	record Confluence(@DefaultValue("false") boolean enabled, @DefaultValue("") String baseUrl,
			@DefaultValue("cloud") ConfluenceDocumentSource.Deployment deployment, @DefaultValue("") String email,
			@DefaultValue("") String token, @DefaultValue List<String> spaces,
			@DefaultValue("200ms") Duration minInterval) {
	}

}
