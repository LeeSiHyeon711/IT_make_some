package com.itmakesome.pickupmemo.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.itmakesome.pickupmemo.service.LogNotificationListenerService
import com.itmakesome.pickupmemo.service.ScreenAccessibilityService

/**
 * 접근성/알림 접근 권한 활성 여부 판정 유틸 (설계서 7장 PermissionChecker, 이슈 C-3 #8).
 *
 * 두 수집 서비스([ScreenAccessibilityService] / [LogNotificationListenerService])는 시스템 바인드 서비스라
 * 코드로 직접 켜고 끌 수 없다. 사용자가 OS 설정에서 권한 토글을 ON 해야 시스템이 바인딩한다(설계서 2-3 / 4-3).
 * 따라서 앱은 "지금 우리 서비스가 OS 설정에서 활성화돼 있는지"를 **시스템 설정값을 읽어 판정**만 한다.
 *
 * 판정 방식(설계서 7장):
 * - 접근성: `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` 문자열을
 *   `:` 구분으로 파싱해 우리 서비스 [ComponentName]이 포함됐는지 확인한다.
 * - 알림 접근: [NotificationManagerCompat.getEnabledListenerPackages]에 우리 패키지가 포함됐는지로 1차 판정하고,
 *   보강으로 `Settings.Secure.enabled_notification_listeners`에서 우리 리스너 컴포넌트 자체를 확인한다.
 *
 * 설정 화면 이동 헬퍼(설계서 4-3):
 * - 접근성: [Settings.ACTION_ACCESSIBILITY_SETTINGS]
 * - 알림 접근: [Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS]
 * MainActivity(D-1 #9)가 onResume 갱신 시 본 판정을 호출하고, 버튼으로 설정 Intent를 띄운다.
 */
object PermissionChecker {

    /** Settings.Secure에 직접 노출되지 않은 알림 리스너 키(상수). 일부 API 레벨에서 상수 미공개라 직접 정의. */
    private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

    /**
     * 접근성 수집 서비스([ScreenAccessibilityService])가 OS 설정에서 활성화돼 있으면 true.
     *
     * `ENABLED_ACCESSIBILITY_SERVICES`는 `"pkgA/.SvcA:pkgB/.SvcB"` 형태의 `:` 구분 목록이다.
     * 우리 서비스 [ComponentName]을 flatten 형태(짧은/긴 표기 모두)로 비교해 포함 여부를 판정한다.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ScreenAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (component in splitter) {
            if (matchesComponent(component, expected)) return true
        }
        return false
    }

    /**
     * 알림 접근(Notification Listener) 권한이 우리 앱에 부여돼 있으면 true.
     *
     * 1차: [NotificationManagerCompat.getEnabledListenerPackages]에 우리 패키지가 있으면 활성으로 본다.
     * 2차(보강): `Settings.Secure.enabled_notification_listeners`에서 우리 리스너 컴포넌트 자체를 확인한다.
     * (한 패키지가 여러 리스너를 가질 수 있어 컴포넌트 단위 확인을 함께 둔다.)
     */
    fun isNotificationAccessEnabled(context: Context): Boolean {
        val packageName = context.packageName
        if (NotificationManagerCompat.getEnabledListenerPackages(context).contains(packageName)) {
            return true
        }

        val expected = ComponentName(context, LogNotificationListenerService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            ENABLED_NOTIFICATION_LISTENERS
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (component in splitter) {
            if (matchesComponent(component, expected)) return true
        }
        return false
    }

    /** 시스템 "접근성" 설정 화면으로 이동하는 Intent (설계서 4-3 시나리오 A). */
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 시스템 "알림 접근" 설정 화면으로 이동하는 Intent (설계서 4-3). */
    fun notificationAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * 설정 목록의 한 항목 문자열(`pkg/.Service` 또는 `pkg/pkg.full.Service`)이
     * [expected] 컴포넌트와 같은지 비교한다. flatten 짧은/긴 표기를 모두 허용하고
     * `unflattenFromString` 으로도 한 번 더 대조해 표기 차이에 견고하게 한다.
     */
    private fun matchesComponent(entry: String, expected: ComponentName): Boolean {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.equals(expected.flattenToString(), ignoreCase = true)) return true
        if (trimmed.equals(expected.flattenToShortString(), ignoreCase = true)) return true
        val parsed = ComponentName.unflattenFromString(trimmed) ?: return false
        return parsed == expected
    }
}
