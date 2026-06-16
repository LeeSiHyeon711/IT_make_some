package com.itmakesome.pickupmemo.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.itmakesome.pickupmemo.R

/**
 * 테스트 알림 발생 유틸 (설계서 7장 TestNotifier, 이슈 G-1 #13).
 *
 * 책임(설계서 7장):
 * - 알림 채널 생성(API 26+ 필수) — [ensureChannel].
 * - 런타임 POST_NOTIFICATIONS 권한 보유 여부 판정 — [hasPostPermission] (API 33+에서만 의미).
 *   실제 권한 "요청"은 Activity 컨텍스트가 필요하므로 [com.itmakesome.pickupmemo.MainActivity]가 담당한다.
 * - 고정 내용 테스트 알림 발생 — [showTestNotification].
 *   제목: "신규 배달 요청" / 내용: "교촌치킨 죽전점 / 배달료 3,900원" (설계서 9장 G-1, PRD 데모 시나리오).
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ★ 정책 충돌 해소 (이슈 #13 핵심):
 *   [com.itmakesome.pickupmemo.service.LogNotificationListenerService]는 노이즈 가드로
 *   "자기 앱(PickUpMemo) 알림"을 수집에서 제외한다(#7). 그러나 본 데모의 목적은
 *   "테스트 알림이 Notification Listener 로그에 기록되는지" 확인하는 것이다.
 *   따라서 이 테스트 알림만은 **전용 채널([TEST_CHANNEL_ID])** 로 발생시키고, 리스너는
 *   자기 앱 제외 규칙에서 이 채널만 예외로 통과시켜 로그에 남긴다.
 *   → 채널 ID가 두 컴포넌트의 "수집 허용" 계약 키 역할을 한다.
 * ─────────────────────────────────────────────────────────────────────────────
 */
object TestNotifier {

    /**
     * 테스트 알림 전용 채널 ID.
     * 리스너([com.itmakesome.pickupmemo.service.LogNotificationListenerService])가
     * 자기 앱 제외 규칙의 예외 키로 사용한다(이 채널 알림만 수집 허용).
     */
    const val TEST_CHANNEL_ID = "pickupmemo_test_delivery"

    /** 테스트 알림 고정 ID(재발생 시 갱신). */
    private const val TEST_NOTIFICATION_ID = 1001

    /**
     * 알림 채널을 보장 생성한다(멱등). API 26+에서 채널 없이 게시하면 알림이 표시되지 않으므로
     * 게시 직전 반드시 호출한다. 동일 ID로 다시 호출해도 안전하다.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(TEST_CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            TEST_CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * POST_NOTIFICATIONS 런타임 권한 보유 여부.
     * API 33 미만은 런타임 권한 개념이 없으므로 항상 true(선언만으로 게시 가능, 설계서 6-1).
     */
    fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 고정 내용 테스트 알림을 발생시킨다(설계서 9장 G-1).
     * 채널을 보장 생성한 뒤 [TEST_CHANNEL_ID] 채널로 게시한다.
     *
     * @return 게시에 성공하면 true. 권한이 없으면(API 33+ 미허가) 게시하지 않고 false.
     */
    fun showTestNotification(context: Context): Boolean {
        ensureChannel(context)
        if (!hasPostPermission(context)) return false

        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            // 이론상 hasPostPermission 통과 후엔 발생하지 않지만 방어적으로 처리.
            false
        }
    }
}
