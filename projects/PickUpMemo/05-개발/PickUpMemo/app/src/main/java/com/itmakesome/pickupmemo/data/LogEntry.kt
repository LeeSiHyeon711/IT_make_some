package com.itmakesome.pickupmemo.data

import com.itmakesome.pickupmemo.util.TimeFormat

/**
 * 로그 1건 데이터 모델 (설계서 5-1).
 *
 * | 필드 | 설명 |
 * |------|------|
 * | [timestampMillis] | 수집 시각(epoch millis). 정렬·포맷 기준 |
 * | [type] | 로그 출처 종류 |
 * | [packageName] | 출처 앱 패키지명 (조회 화면의 필터 기준) |
 * | [title] | 알림 제목 등 (접근성 로그는 보통 null) |
 * | [body] | 핵심 텍스트(화면 텍스트/desc/viewId 묶음 또는 알림 내용) |
 *
 * 본 이슈(B-1)는 모델과 텍스트 직렬화([toLogBlock])까지만 책임진다.
 * 파일 append/read/compaction은 B-2(#4), 인메모리 캐시는 B-3(#5)에서 LogFileStore/LogRepository가 담당한다.
 */
data class LogEntry(
    val timestampMillis: Long,
    val type: LogType,
    val packageName: String,
    val title: String? = null,
    val body: String
) {

    /**
     * 설계서 5-2의 사람이 읽기 쉬운 고정 형식으로 1건을 직렬화한다.
     * 블록 사이는 빈 줄로 구분되므로, 이 함수는 후행 빈 줄 없이 블록 본문만 반환한다.
     *
     * 접근성 예시:
     * ```
     * [2026-06-15 14:33:12]
     * Package: com.sankuai.meituan.banma
     * Type: Accessibility
     * Text: 교촌치킨 죽전점 | desc=업체상호 | id=tv_store_name
     * ```
     *
     * 알림 예시 (title 존재 시 Title/Body 라인 구성):
     * ```
     * [2026-06-15 14:33:12]
     * Package: com.kakao.talk
     * Type: Notification
     * Title: 신규 배달 요청
     * Body: 교촌치킨 죽전점 / 배달료 3,900원
     * ```
     */
    fun toLogBlock(): String {
        val sb = StringBuilder()
        sb.append('[').append(TimeFormat.formatTimestamp(timestampMillis)).append(']').append('\n')
        sb.append("Package: ").append(packageName).append('\n')
        sb.append("Type: ").append(type.displayName).append('\n')
        if (type == LogType.NOTIFICATION || !title.isNullOrBlank()) {
            sb.append("Title: ").append(title.orEmpty()).append('\n')
            sb.append("Body: ").append(body)
        } else {
            sb.append("Text: ").append(body)
        }
        return sb.toString()
    }
}
