package dev.ragstarter.indexing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /admin/reindex} (헤더 {@code X-Admin-Token: $RAG_ADMIN_TOKEN}). 토큰 미설정 시 차단.
 *
 * <p>
 * TODO: 운영 인증(Spring Security 등)으로 바꾸기
 */
@RestController
class IndexAdminController {

	private final IndexingService indexing;

	private final byte[] token;

	IndexAdminController(IndexingService indexing, @Value("${rag.admin.token:}") String token) {
		this.indexing = indexing;
		this.token = token.getBytes(StandardCharsets.UTF_8);
	}

	@PostMapping("/admin/reindex")
	ResponseEntity<Map<String, Object>> reindex(@RequestHeader(name = "X-Admin-Token", required = false) @Nullable String given) {
		if (this.token.length == 0 || given == null
				|| !MessageDigest.isEqual(this.token, given.getBytes(StandardCharsets.UTF_8))) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(Map.of("chunks", this.indexing.indexAll()));
	}

}
