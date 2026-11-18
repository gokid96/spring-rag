package dev.springrag.indexing;

/**
 * 색인 결과.
 *
 * @param documents 출처에서 받은 문서
 * @param embedded 새로 임베딩한 문서
 * @param unchanged 바뀌지 않아 건너뛴 문서
 * @param deleted 출처에서 사라져 지운 문서
 * @param failedSources 목록을 받지 못한 출처 (그 출처는 지우지 않음)
 */
public record SyncReport(int documents, int embedded, int unchanged, int deleted, int failedSources) {
}
