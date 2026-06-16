package com.itmakesome.pickupmemo

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.itmakesome.pickupmemo.data.LogRepository
import com.itmakesome.pickupmemo.databinding.ActivityMainBinding
import com.itmakesome.pickupmemo.ui.AccessibilityTestActivity
import com.itmakesome.pickupmemo.ui.LogViewActivity
import com.itmakesome.pickupmemo.util.LogExporter
import com.itmakesome.pickupmemo.util.PermissionChecker
import com.itmakesome.pickupmemo.util.TestNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 메인 / 허브 화면 (설계서 4-1 ① / 7장, 이슈 D-1 #9).
 *
 * 책임(설계서 7장 "MainActivity"):
 * - 권한·서비스 상태 표시(onResume 갱신): 접근성 서비스 실행 여부 / 접근성 권한 / 알림 접근 권한.
 *   판정은 [PermissionChecker](#8)에 위임한다. 시스템 설정에서 권한을 켜고 돌아오면([onResume])
 *   즉시 재판정해 화면을 갱신한다(설계서 4-3 시나리오 A).
 * - 설정 화면 이동 버튼 2종: 접근성 설정 / 알림 접근 설정 ([PermissionChecker]의 Intent 헬퍼 사용).
 * - 다른 화면으로 가는 버튼 디스패치: 로그 보기([LogViewActivity]) / 접근성 테스트([AccessibilityTestActivity]).
 *
 * 앱 시작 시 [LogRepository.initialize] 호출(설계서 5-3 ①). 멱등이므로 수집 서비스가 먼저 초기화했어도 안전하다.
 *
 * "로그 내보내기"는 E-1(#10)에서 [LogExporter]에 연결된다(compaction→export txt→FileProvider→ACTION_SEND).
 * "테스트 알림 생성"은 G-1(#13)에서 [TestNotifier]에 연결된다(채널 생성 + 런타임 POST_NOTIFICATIONS + 고정 알림).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 내보내기 파일 IO를 메인 스레드 밖에서 처리하기 위한 스코프(설계서 2-1 코루틴 방침). onDestroy에서 취소. */
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * POST_NOTIFICATIONS 런타임 권한 요청 런처(API 33+, 설계서 6-1).
     * 권한이 허가되면 곧바로 테스트 알림을 발생시키고, 거부되면 안내 토스트를 띄운다.
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                postTestNotification()
            } else {
                Toast.makeText(this, R.string.toast_notification_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 앱 시작 시 로그 저장소 초기화(설계서 5-3 ①). 멱등 호출.
        LogRepository.initialize(applicationContext)

        // 설정 화면 이동(설계서 4-3) — 돌아오면 onResume이 상태를 갱신한다.
        binding.btnAccessibilitySettings.setOnClickListener {
            startSettings(PermissionChecker.accessibilitySettingsIntent())
        }
        binding.btnNotificationSettings.setOnClickListener {
            startSettings(PermissionChecker.notificationAccessSettingsIntent())
        }

        // 다른 Activity 호출(설계서 4-1 허브). 대상 화면 본문은 후속 이슈에서 채워진다.
        binding.btnAccessibilityTest.setOnClickListener {
            startActivity(Intent(this, AccessibilityTestActivity::class.java))
        }
        binding.btnLogView.setOnClickListener {
            startActivity(Intent(this, LogViewActivity::class.java))
        }

        // 로그 내보내기(E-1 #10): compaction→export txt→FileProvider→ACTION_SEND 공유 시트.
        binding.btnLogExport.setOnClickListener { exportLog() }

        // 테스트 알림 생성(G-1 #13): 채널 생성 + 런타임 권한 처리 + 고정 내용 알림(TestNotifier).
        binding.btnTestNotification.setOnClickListener { onTestNotificationClick() }
    }

    /**
     * "테스트 알림 생성" 버튼 처리(설계서 9장 G-1).
     * API 33+에서 POST_NOTIFICATIONS 권한이 없으면 런타임 권한을 요청하고,
     * 권한이 이미 있거나(또는 API 33 미만이면) 곧바로 테스트 알림을 발생시킨다.
     *
     * 발생한 알림은 [TestNotifier.TEST_CHANNEL_ID] 전용 채널을 통해 게시되어,
     * 알림 접근 권한이 켜져 있으면 LogNotificationListenerService가 자기 앱 제외 예외로
     * 이를 수집해 로그에 남긴다(이슈 #13 정책 충돌 해소).
     */
    private fun onTestNotificationClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !TestNotifier.hasPostPermission(this)
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        postTestNotification()
    }

    /** 테스트 알림을 실제로 발생시키고 결과를 토스트로 안내한다. */
    private fun postTestNotification() {
        val posted = TestNotifier.showTestNotification(this)
        val msgRes = if (posted) R.string.toast_test_notification_posted
        else R.string.toast_notification_permission_denied
        Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
    }

    /**
     * 로그 내보내기(설계서 4-4 시나리오 C). [LogExporter.export]가 compaction 후 export txt를 만들고
     * FileProvider content:// URI를 반환하면, ACTION_SEND 공유 시트를 띄운다.
     * 파일 IO는 IO 디스패처에서, 공유 시트 실행은 메인 스레드에서 수행한다.
     */
    private fun exportLog() {
        uiScope.launch {
            val uri = try {
                LogExporter.export(applicationContext)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.toast_export_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val shareIntent = LogExporter.buildShareIntent(uri)
            try {
                startActivity(Intent.createChooser(shareIntent, getString(R.string.export_chooser_title)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, R.string.toast_export_no_app, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 화면이 다시 보일 때마다 권한/서비스 상태를 재판정해 표시한다(설계서 4-1 "onResume마다 갱신").
     * 사용자가 시스템 설정에서 권한을 토글하고 뒤로 오면 즉시 반영된다.
     */
    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val accessibilityOn = PermissionChecker.isAccessibilityEnabled(this)
        val notificationOn = PermissionChecker.isNotificationAccessEnabled(this)

        // 접근성 서비스는 권한이 켜진 동안 시스템이 바인딩·유지하므로(설계서 2-3),
        // "실행 중/중지"와 "권한 허가/미허가"는 동일한 시스템 신호(접근성 활성)로 판정한다.
        binding.tvAccessibilityServiceStatus.text = statusLine(
            getString(R.string.status_accessibility_service),
            if (accessibilityOn) getString(R.string.status_running) else getString(R.string.status_stopped)
        )
        binding.tvAccessibilityPermissionStatus.text = statusLine(
            getString(R.string.status_accessibility_permission),
            grantedLabel(accessibilityOn)
        )
        binding.tvNotificationAccessStatus.text = statusLine(
            getString(R.string.status_notification_access),
            grantedLabel(notificationOn)
        )
    }

    private fun grantedLabel(granted: Boolean): String =
        if (granted) getString(R.string.status_granted) else getString(R.string.status_denied)

    private fun statusLine(label: String, value: String): String = "$label : $value"

    /** 시스템 설정 화면으로 이동. 기기에 해당 설정 액션이 없으면 토스트로 안내(크래시 방지). */
    private fun startSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.toast_not_implemented, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        // 진행 중인 내보내기 코루틴을 정리한다(화면 종료 시 누수 방지).
        uiScope.cancel()
        super.onDestroy()
    }
}
