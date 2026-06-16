package com.itmakesome.pickupmemo.data

/**
 * 로그 1건의 출처 종류 (설계서 5-1).
 *
 * - [ACCESSIBILITY] : 접근성 서비스가 화면 텍스트/desc/viewId를 수집한 로그
 * - [NOTIFICATION]  : 알림 수신기가 시스템 알림(제목/내용)을 수집한 로그
 * - [SYSTEM]        : 앱 자체가 남기는 시스템/상태 로그 (예비)
 *
 * [displayName] 은 로그 텍스트 포맷(설계서 5-2)의 `Type:` 라인에 그대로 쓰인다.
 * 예) `Type: Accessibility`
 */
enum class LogType(val displayName: String) {
    ACCESSIBILITY("Accessibility"),
    NOTIFICATION("Notification"),
    SYSTEM("System");

    companion object {
        /**
         * 파일에서 읽은 `Type:` 라인 값(displayName)을 LogType으로 역변환한다.
         * 알 수 없는 값은 [SYSTEM]으로 처리한다. (파싱이 아닌 육안 분석이 목적이므로 관대하게 처리)
         */
        fun fromDisplayName(name: String): LogType =
            entries.firstOrNull { it.displayName.equals(name.trim(), ignoreCase = true) } ?: SYSTEM
    }
}
