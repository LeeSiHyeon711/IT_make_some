package com.itmakesome.pickupmemo.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.itmakesome.pickupmemo.data.LogEntry
import com.itmakesome.pickupmemo.data.LogRepository
import com.itmakesome.pickupmemo.data.LogType
import com.itmakesome.pickupmemo.util.TestNotifier

/**
 * 알림 수집 서비스 (설계서 4-2 / 6-2, 이슈 C-2 #7).
 *
 * 시스템이 [NotificationListenerService]를 바인딩·유지하므로 별도 포그라운드 서비스가 필요 없다(설계서 2-3).
 * 사용자가 OS 설정(알림 접근)에서 PickUpMemo를 켜 두는 동안 시스템이 본 서비스를 살려 두고,
 * 새 알림이 게시될 때마다 [onNotificationPosted]를 호출한다.
 *
 * 수집 흐름(설계서 4-2 / 4-2 데이터 수집 흐름):
 * 1. [onNotificationPosted] 로 시스템 알림 게시 이벤트 수신.
 * 2. 알림에서 제목(`EXTRA_TITLE`)·내용(`EXTRA_TEXT`/`EXTRA_BIG_TEXT`/`EXTRA_SUB_TEXT`)·패키지명·발생 시각(postTime) 추출.
 * 3. [LogEntry](type=[LogType.NOTIFICATION])로 묶어 [LogRepository.add] 한다.
 *
 * 로그 직렬화는 [LogEntry.toLogBlock]이 type=NOTIFICATION일 때 `Title:` / `Body:` 라인으로 처리한다(설계서 5-2).
 *
 * 노이즈 가드:
 * - 자기 앱(PickUpMemo) 알림은 원칙적으로 수집에서 제외한다(검증 대상은 배민커넥트 등 외부 앱 알림).
 * - 제목·내용이 모두 비어 있는 알림(그룹 요약 등 빈 알림)은 건너뛴다.
 *
 * ★ 정책 충돌 해소 (이슈 #13):
 *   본 데모는 "테스트 알림이 Notification Listener 로그에 기록되는지" 확인하는 것이 목적이다.
 *   따라서 자기 앱 알림 중 **테스트 알림 전용 채널([TestNotifier.TEST_CHANNEL_ID])** 로 게시된 알림만은
 *   예외로 수집을 허용한다. 그 외 자기 앱 알림(있다면)은 종전대로 제외한다.
 *   → 채널 ID가 [TestNotifier]와 본 리스너 사이의 "수집 허용" 계약 키다.
 */
class LogNotificationListenerService : NotificationListenerService() {

    /**
     * 리스너가 시스템에 연결되면 호출된다. 파일 저장소가 준비되도록 [LogRepository.initialize]를
     * 멱등 호출한다(이미 초기화돼 있으면 무시됨). 이후 [LogRepository.add]가 파일 미러까지 기록할 수 있다.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        LogRepository.initialize(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val packageName = sbn.packageName ?: return
        if (packageName.isBlank()) return
        // 자기 앱 알림은 수집 제외(검증 대상은 외부 앱 알림).
        // 단, 테스트 알림 전용 채널만은 예외로 통과시킨다(이슈 #13 정책 충돌 해소).
        if (packageName == applicationContext.packageName &&
            sbn.notification?.channelId != TestNotifier.TEST_CHANNEL_ID
        ) {
            return
        }

        val extras: Bundle = sbn.notification?.extras ?: Bundle.EMPTY

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()

        // 본문은 BigText(펼친 내용) → Text → SubText 순으로 가장 풍부한 값을 택한다.
        val text = firstNonBlank(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
        )

        // 제목·내용이 모두 비면 수집할 의미가 없으므로 건너뛴다(그룹 요약 등).
        if (title.isEmpty() && text.isEmpty()) return

        LogRepository.add(
            LogEntry(
                // 알림 발생 시각(postTime). 0이면(드물게) 현재 시각으로 대체.
                timestampMillis = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis(),
                type = LogType.NOTIFICATION,
                packageName = packageName,
                title = title,
                body = text
            )
        )
    }

    /** 후보 CharSequence들 중 첫 번째로 비어 있지 않은 값을 trim 해 반환한다. 모두 비면 빈 문자열. */
    private fun firstNonBlank(vararg candidates: CharSequence?): String {
        for (c in candidates) {
            val s = c?.toString()?.trim().orEmpty()
            if (s.isNotEmpty()) return s
        }
        return ""
    }
}
