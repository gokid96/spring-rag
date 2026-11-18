/**
 * 색인 모듈. 문서 분할 → 임베딩 → 벡터 저장소.
 *
 * <p>
 * 기본 출처: 폴더({@link dev.springrag.indexing.FolderDocumentSource}). 기타 출처: {@code DocumentSource} 빈 추가.
 *
 * <p>
 * 바뀐 문서만 다시 임베딩, 사라진 문서 삭제. 기동 시 · 매일({@code rag.index.cron}) · {@code POST /admin/reindex}.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package dev.springrag.indexing;
