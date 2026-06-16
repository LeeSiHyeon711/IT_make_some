package com.itmakesome.pickupmemo.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.itmakesome.pickupmemo.R
import com.itmakesome.pickupmemo.databinding.ActivityAccessibilityTestBinding

/**
 * 데모 - 모의 배차 화면 (설계서 4-1 ③, 이슈 G-2 #14).
 *
 * 목적: 실제 배달 앱의 배차 화면처럼 보이는 고정 텍스트 화면을 띄워,
 * 접근성 서비스(ScreenAccessibilityService, #6)가 이 화면의 텍스트
 * (업체 상호·교촌치킨 죽전점·전달지 주소·배차 수락·거절)를 수집하는지
 * 사람이 눈으로 확인하게 한다.
 *
 * 평범한 TextView/Button만 사용하므로 접근성 트리(text/contentDescription)에
 * 그대로 노출된다. 버튼은 실제 동작이 아니며 클릭 시 안내 토스트만 띄운다(설계서 4-1 ③ 149행).
 */
class AccessibilityTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccessibilityTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccessibilityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.accessibility_test_title)

        // 실제 배차 동작 아님 - 토스트로 데모임을 알린다.
        binding.btnAccept.setOnClickListener {
            Toast.makeText(this, R.string.toast_acc_test_accept, Toast.LENGTH_SHORT).show()
        }
        binding.btnReject.setOnClickListener {
            Toast.makeText(this, R.string.toast_acc_test_reject, Toast.LENGTH_SHORT).show()
        }
    }
}
