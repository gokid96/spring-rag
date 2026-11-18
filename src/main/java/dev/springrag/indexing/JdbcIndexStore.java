package dev.springrag.indexing;

import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@code indexed_document} 테이블.
 */
class JdbcIndexStore implements IndexStore {

	private final JdbcClient jdbc;

	JdbcIndexStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<String, Entry> findBySource(String source) {
		Map<String, Entry> entries = new LinkedHashMap<>();
		this.jdbc.sql("SELECT source_id, source, title, fingerprint, vector_ids FROM indexed_document WHERE source = ?")
			.param(source)
			.query((rs, row) -> new Entry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
					ids(rs.getArray(5))))
			.list()
			.forEach(entry -> entries.put(entry.sourceId(), entry));
		return entries;
	}

	@Override
	public void save(Entry entry) {
		this.jdbc.sql("""
				INSERT INTO indexed_document (source_id, source, title, fingerprint, vector_ids, indexed_at)
				VALUES (:id, :source, :title, :fingerprint, string_to_array(:ids, ','), :now)
				ON CONFLICT (source_id) DO UPDATE SET source = EXCLUDED.source, title = EXCLUDED.title,
				    fingerprint = EXCLUDED.fingerprint, vector_ids = EXCLUDED.vector_ids, indexed_at = EXCLUDED.indexed_at
				""")
			.param("id", entry.sourceId())
			.param("source", entry.source())
			.param("title", entry.title())
			.param("fingerprint", entry.fingerprint())
			.param("ids", String.join(",", entry.vectorIds()))
			.param("now", OffsetDateTime.now())
			.update();
	}

	@Override
	public void delete(String sourceId) {
		this.jdbc.sql("DELETE FROM indexed_document WHERE source_id = ?").param(sourceId).update();
	}

	private static List<String> ids(Array array) throws SQLException {
		return (array == null) ? List.of() : Arrays.asList((String[]) array.getArray());
	}

}
