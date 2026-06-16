package com.itmakesome.pickupmemo.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.itmakesome.pickupmemo.data.LogEntry
import com.itmakesome.pickupmemo.data.LogRepository
import com.itmakesome.pickupmemo.data.LogType

/**
 * 접근성 수집 서비스 (설계서 4-2 / 6-2 / 6-3, 이슈 C-1 #6).
 *
 * 시스템이 [AccessibilityService]를 바인딩·유지하므로 별도 포그라운드 서비스가 필요 없다(설계서 2-3).
 * 사용자가 OS 설정에서 PickUpMemo 접근성 권한을 켜 두는 동안 시스템이 본 서비스를 살려 둔다.
 *
 * 수집 흐름(설계서 4-2):
 * 1. [onAccessibilityEvent] 로 화면 텍스트 변화 이벤트 수신
 *    (config: typeWindowStateChanged | typeWindowContentChanged | typeViewTextChanged)
 * 2. 이벤트의 source(없으면 [getRootInActiveWindow]) 노드 트리를 순회하며
 *    - 화면 텍스트(`text`)
 *    - ContentDescription(`contentDescription`)
 *    - View ID(`viewIdResourceName`)
 *    를 추출한다. WindowStateChanged 이벤트면 Activity 클래스명도 함께 기록한다.
 * 3. Package Name과 함께 [LogEntry](type=[LogType.ACCESSIBILITY])로 묶어 [LogRepository.add] 한다.
 *
 * 로그 본문 형식(설계서 5-2):
 *   `교촌치킨 죽전점 | desc=업체상호 | id=tv_store_name`
 * 한 화면에 여러 노드가 있으면 노드별 세그먼트를 ` / ` 로 이어 한 줄 본문으로 만든다.
 *
 * 노이즈/성능 가드:
 * - 자기 앱(PickUpMemo) 화면 이벤트는 수집에서 제외한다(검증 대상은 외부 앱 화면).
 * - 한 이벤트에서 수집하는 세그먼트는 [MAX_SEGMENTS_PER_EVENT]개로 제한해 거대한 로그/무한 순회를 방지한다.
 */
class ScreenAccessibilityService : AccessibilityService() {

    /**
     * 서비스가 시스템에 연결되면 호출된다. 파일 저장소가 준비되도록 [LogRepository.initialize]를
     * 멱등 호출한다(이미 초기화돼 있으면 무시됨). 이후 [add]가 파일 미러까지 기록할 수 있다.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        LogRepository.initialize(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString()
        if (packageName.isNullOrBlank()) return
        // 자기 앱 화면은 수집 제외(검증 대상은 배민커넥트 등 외부 앱 화면).
        if (packageName == applicationContext.packageName) return

        val segments = LinkedHashSet<String>()

        // WindowStateChanged 이벤트에는 현재 Activity 클래스명이 실린다(설계서 6-3 Activity 수집).
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val activity = event.className?.toString()
            if (!activity.isNullOrBlank()) {
                segments.add("activity=$activity")
            }
        }

        val root: AccessibilityNodeInfo? = event.source ?: rootInActiveWindow
        if (root != null) {
            collectNode(root, segments)
        }

        if (segments.isEmpty()) return

        LogRepository.add(
            LogEntry(
                timestampMillis = System.currentTimeMillis(),
                type = LogType.ACCESSIBILITY,
                packageName = packageName,
                title = null, // 접근성 로그는 Text 라인으로 직렬화(설계서 5-2)
                body = segments.joinToString(" / ")
            )
        )
    }

    /**
     * 노드 트리를 깊이 우선으로 순회하며 text/contentDescription/viewIdResourceName 세그먼트를 [out]에 모은다.
     * text·desc 가 모두 비어 있는 노드(레이아웃 컨테이너 등)는 건너뛴다.
     * [out] 크기가 [MAX_SEGMENTS_PER_EVENT]에 도달하면 더 순회하지 않는다.
     */
    private fun collectNode(node: AccessibilityNodeInfo, out: LinkedHashSet<String>) {
        if (out.size >= MAX_SEGMENTS_PER_EVENT) return

        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val rawId = node.viewIdResourceName?.trim().orEmpty()
        // "패키지:id/tv_store_name" → "tv_store_name" 로 축약(설계서 5-2 예시 형식).
        val id = if (rawId.isNotEmpty()) rawId.substringAfterLast('/') else ""

        if (text.isNotEmpty() || desc.isNotEmpty()) {
            val parts = ArrayList<String>(3)
            if (text.isNotEmpty()) parts.add(text)
            if (desc.isNotEmpty()) parts.add("desc=$desc")
            if (id.isNotEmpty()) parts.add("id=$id")
            out.add(parts.joinToString(" | "))
            if (out.size >= MAX_SEGMENTS_PER_EVENT) return
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            collectNode(child, out)
            if (out.size >= MAX_SEGMENTS_PER_EVENT) return
        }
    }

    override fun onInterrupt() {
        // 시스템이 접근성 피드백 중단을 알릴 때 호출. 수집형 서비스라 별도 처리 불필요(설계서 2-3).
    }

    private companion object {
        /** 한 이벤트에서 수집하는 최대 세그먼트 수(거대한 로그/무한 순회 방지). */
        const val MAX_SEGMENTS_PER_EVENT = 200
    }
}
