package dev.springrag.indexing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

/**
 * 출처 조립. 켠 출처는 모두 함께 색인. 기타 출처(DB · 드라이브 등)는 {@link DocumentSource} 빈 등록.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({ SourceProperties.BookStack.class, SourceProperties.Confluence.class })
class IndexingConfiguration {

	@Bean
	IndexingService indexingService(List<DocumentSource> sources, VectorStore vectorStore, JdbcClient jdbc) {
		return new IndexingService(sources, vectorStore, new JdbcIndexStore(jdbc));
	}

	@Bean
	@ConditionalOnProperty(name = "rag.docs.enabled", havingValue = "true", matchIfMissing = true)
	FolderDocumentSource folderDocumentSource(@Value("${rag.docs.path}") String path) {
		return new FolderDocumentSource(Path.of(path));
	}

	@Bean
	@ConditionalOnProperty(name = "rag.bookstack.enabled", havingValue = "true")
	BookStackDocumentSource bookStackDocumentSource(SourceProperties.BookStack props, RestClient.Builder builder) {
		require(props.baseUrl(), "rag.bookstack.base-url");
		require(props.token(), "rag.bookstack.token");
		if (props.shelves().isEmpty()) {
			throw new IllegalStateException("rag.bookstack.shelves 비어 있음");
		}
		RestClient http = builder.baseUrl(props.baseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Token " + props.token())
			.build();
		return new BookStackDocumentSource(http, props.shelves());
	}

	@Bean
	@ConditionalOnProperty(name = "rag.confluence.enabled", havingValue = "true")
	ConfluenceDocumentSource confluenceDocumentSource(SourceProperties.Confluence props, RestClient.Builder builder) {
		require(props.baseUrl(), "rag.confluence.base-url");
		require(props.token(), "rag.confluence.token");
		if (props.spaces().isEmpty()) {
			throw new IllegalStateException("rag.confluence.spaces 비어 있음");
		}
		String auth = switch (props.deployment()) {
			case CLOUD -> {
				require(props.email(), "rag.confluence.email");
				yield "Basic " + Base64.getEncoder()
					.encodeToString((props.email() + ":" + props.token()).getBytes(StandardCharsets.UTF_8));
			}
			case DATACENTER -> "Bearer " + props.token();
		};
		RestClient http = builder.baseUrl(props.baseUrl()).defaultHeader(HttpHeaders.AUTHORIZATION, auth).build();
		return new ConfluenceDocumentSource(http, props.baseUrl(), props.deployment(), props.spaces());
	}

	/** {@code rag.index.on-startup=true} 면 기동 직후 색인 */
	@Bean
	@ConditionalOnProperty(name = "rag.index.on-startup", havingValue = "true")
	ApplicationListener<ApplicationReadyEvent> indexOnStartup(IndexingService indexing) {
		return event -> indexing.sync(false);
	}

	/** 예약 색인 ({@code rag.index.cron}, "-" 면 끔). 바뀐 문서만 */
	@Bean
	ScheduledSync scheduledSync(IndexingService indexing) {
		return new ScheduledSync(indexing);
	}

	static class ScheduledSync {

		private final IndexingService indexing;

		ScheduledSync(IndexingService indexing) {
			this.indexing = indexing;
		}

		@Scheduled(cron = "${rag.index.cron:0 0 3 * * *}")
		void run() {
			this.indexing.sync(false);
		}

	}

	private static void require(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " 비어 있음");
		}
	}

}
