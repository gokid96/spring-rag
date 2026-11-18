package dev.ragstarter.indexing;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.core.ParameterizedTypeReference;

/**
 * 외부 API JSON 을 Map 으로 읽을 때 쓰는 도우미.
 */
final class Json {

	static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
	};

	private Json() {
	}

	static Map<?, ?> map(@Nullable Object value) {
		return (value instanceof Map<?, ?> map) ? map : Map.of();
	}

	static List<?> list(@Nullable Object value) {
		return (value instanceof List<?> list) ? list : List.of();
	}

	static String text(@Nullable Object value) {
		return (value == null) ? "" : value.toString();
	}

	/** 경로를 따라 내려간 값. 예: path(page, "body", "storage", "value") */
	static @Nullable Object path(@Nullable Object value, String... keys) {
		Object current = value;
		for (String key : keys) {
			current = map(current).get(key);
		}
		return current;
	}

}
