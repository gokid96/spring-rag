/**
 * 색인 모듈. 문서 분할 → 임베딩 → 벡터 저장소.
 *
 * <p>
 * 기본 출처: 폴더({@link dev.springrag.indexing.FolderDocumentSource}). 기타 출처: {@code DocumentSource} 빈 추가.
 *
 * <p>
 * TODO: 변경 문서만 재색인, 삭제 문서 반영, 예약 색인
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package dev.springrag.indexing;
