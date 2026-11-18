package dev.springrag.indexing;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 출처 API 429 · 5xx 재시도.
 */
class ThrottleTests {

	@Test
	void retriesTooManyRequests() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("http://wiki")
			.requestInterceptor(new Throttle(Duration.ZERO));
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		HttpHeaders retryNow = new HttpHeaders();
		retryNow.add("Retry-After", "0");
		server.expect(requestTo("http://wiki/api")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(retryNow));
		server.expect(requestTo("http://wiki/api")).andRespond(withStatus(HttpStatus.BAD_GATEWAY).headers(retryNow));
		server.expect(requestTo("http://wiki/api")).andRespond(withSuccess("ok", null));

		assertThat(builder.build().get().uri("/api").retrieve().body(String.class)).isEqualTo("ok");
		server.verify();
	}

	@Test
	void backoffWithoutRetryAfter() {
		assertThat(Throttle.retryAfterMs(null, 0)).isEqualTo(1000);
		assertThat(Throttle.retryAfterMs(null, 3)).isEqualTo(8000);
		assertThat(Throttle.retryAfterMs("5", 0)).isEqualTo(5000);
	}

}
