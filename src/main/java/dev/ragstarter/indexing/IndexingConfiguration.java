package dev.ragstarter.indexing;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 출처 조립. 기타 출처(위키 · DB · 드라이브)는 {@link DocumentSource} 빈 등록 시 함께 색인.
 */
@Configuration(proxyBeanMethods = false)
class IndexingConfiguration {

	@Bean
	@ConditionalOnProperty(name = "rag.docs.enabled", havingValue = "true", matchIfMissing = true)
	FolderDocumentSource folderDocumentSource(@Value("${rag.docs.path}") String path) {
		return new FolderDocumentSource(Path.of(path));
	}

	/** {@code rag.index.on-startup=true} 면 기동 직후 색인 */
	@Bean
	@ConditionalOnProperty(name = "rag.index.on-startup", havingValue = "true")
	ApplicationListener<ApplicationReadyEvent> indexOnStartup(IndexingService indexing) {
		return event -> indexing.indexAll();
	}

}
