package dev.springrag.indexing;

import java.io.IOException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 출처 API 호출 간격 · 재시도. 요청 사이 최소 간격, 429 · 5xx 는 {@code Retry-After}(없으면 점점 길게) 후 재시도.
 */
final class Throttle implements ClientHttpRequestInterceptor {

	private static final Logger log = LoggerFactory.getLogger(Throttle.class);

	static final int MAX_RETRIES = 5;

	private final long intervalNanos;

	private long last;

	Throttle(Duration minInterval) {
		this.intervalNanos = minInterval.toNanos();
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		for (int attempt = 0;; attempt++) {
			waitTurn();
			ClientHttpResponse response = execution.execute(request, body);
			HttpStatusCode status = response.getStatusCode();
			boolean retryable = status.value() == 429 || status.is5xxServerError();
			if (!retryable || attempt >= MAX_RETRIES) {
				return response;
			}
			long waitMs = retryAfterMs(response.getHeaders().getFirst("Retry-After"), attempt);
			response.close();
			log.warn("{} {} → {}, {}ms 후 재시도 ({}/{})", request.getMethod(), request.getURI().getPath(), status.value(),
					waitMs, attempt + 1, MAX_RETRIES);
			sleep(waitMs * 1_000_000);
		}
	}

	private synchronized void waitTurn() {
		long wait = this.last + this.intervalNanos - System.nanoTime();
		if (wait > 0) {
			sleep(wait);
		}
		this.last = System.nanoTime();
	}

	static long retryAfterMs(String header, int attempt) {
		if (header != null) {
			try {
				return Math.min(Long.parseLong(header.strip()) * 1000, 120_000);
			}
			catch (NumberFormatException ex) {
				// HTTP 날짜 형식은 무시
			}
		}
		return Math.min(1000L << attempt, 30_000);
	}

	private static void sleep(long nanos) {
		try {
			Thread.sleep(Duration.ofNanos(nanos));
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("중단됨", ex);
		}
	}

}
