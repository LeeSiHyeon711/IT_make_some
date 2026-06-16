package com.itmakesome.pickupmemo.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 타임스탬프/파일명 포맷 유틸 (설계서 7장 TimeFormat).
 *
 * - 로그 라인:  `yyyy-MM-dd HH:mm:ss`  (설계서 5-2 예시 `[2026-06-15 14:33:12]`)
 * - 파일명:     `yyyyMMdd_HHmmss`       (설계서 4-4 `pickupmemo_log_<timestamp>.txt`)
 *
 * SimpleDateFormat 인스턴스는 스레드 안전하지 않으므로 [ThreadLocal]로 분리해
 * 접근성/알림 서비스의 동시 호출에서도 안전하게 재사용한다.
 */
object TimeFormat {

    private const val LOG_PATTERN = "yyyy-MM-dd HH:mm:ss"
    private const val FILE_PATTERN = "yyyyMMdd_HHmmss"

    private val logFormat = ThreadLocal.withInitial {
        SimpleDateFormat(LOG_PATTERN, Locale.US)
    }
    private val fileFormat = ThreadLocal.withInitial {
        SimpleDateFormat(FILE_PATTERN, Locale.US)
    }

    /** 로그 라인용 시각 문자열. 예: `2026-06-15 14:33:12` */
    fun formatTimestamp(millis: Long): String = logFormat.get()!!.format(Date(millis))

    /** 내보내기 파일명용 시각 문자열. 예: `20260615_143312` */
    fun formatFileStamp(millis: Long): String = fileFormat.get()!!.format(Date(millis))

    /**
     * 내보내기 파일명 전체를 만든다. 예: `pickupmemo_log_20260615_143312.txt`
     * (설계서 4-1 / 4-4 파일명 규칙)
     */
    fun exportFileName(millis: Long): String = "pickupmemo_log_${formatFileStamp(millis)}.txt"

    /**
     * 로그 라인용 시각 문자열(`yyyy-MM-dd HH:mm:ss`)을 epoch millis로 역변환한다.
     * 앱 재시작 시 LogFileStore가 파일의 `[타임스탬프]` 헤더를 LogEntry로 복원할 때 사용한다(설계서 5-3).
     * 형식이 맞지 않으면 null을 반환한다(파싱이 아닌 육안 분석이 목적이므로 관대하게 처리).
     */
    fun parseTimestamp(text: String): Long? = try {
        logFormat.get()!!.parse(text.trim())?.time
    } catch (e: java.text.ParseException) {
        null
    }
}
