package com.itmakesome.pickupmemo.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.itmakesome.pickupmemo.R
import com.itmakesome.pickupmemo.data.LogRepository
import com.itmakesome.pickupmemo.databinding.ActivityLogViewBinding

/**
 * 로그 조회 화면 (설계서 4-1 ② / 7장 LogViewActivity).
 *
 * F-1(#11) 범위:
 * - RecyclerView로 [LogRepository]의 로그를 **최신순**으로 표시한다.
 *
 * F-2(#12, 본 이슈) 범위:
 * - **패키지명 필터**: 상단 입력란에 패키지명(예: `com.sample.app`)을 넣고 [적용]을 누르면
 *   해당 패키지명을 포함하는 로그만 표시한다([LogRepository.query]에 필터어 전달). 비우고 적용하면 전체.
 * - **재시작 시 초기화(PRD 4-3 결정)**: 필터어는 액티비티 인스턴스 멤버([currentFilter])로만 보관하고
 *   어디에도 영속 저장하지 않는다. 앱(액티비티)이 새로 생성되면 빈 값에서 시작 → 자동으로 초기화된다.
 * - **전체 삭제**: 확인 다이얼로그 후 [LogRepository.clear]를 호출(인메모리 + 파일 삭제)하고 목록을 비운다.
 *
 * 갱신 시점: [onCreate] 진입 시 + [onResume]마다 [currentFilter]를 적용해 재조회한다.
 */
class LogViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogViewBinding
    private val adapter = LogAdapter()

    /** 현재 적용 중인 패키지명 필터. 빈 문자열 = 전체. 영속 저장하지 않으므로 재시작 시 자동 초기화(PRD 4-3). */
    private var currentFilter: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.log_view_title)

        // 수집 서비스가 먼저 초기화하지 않았을 수 있으므로(파일 미러 준비) 멱등 초기화를 보장한다.
        LogRepository.initialize(applicationContext)

        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = adapter

        // [적용] 버튼 또는 키보드 검색 액션으로 필터를 적용한다.
        binding.btnApplyFilter.setOnClickListener { applyFilterFromInput() }
        binding.etFilter.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyFilterFromInput()
                true
            } else {
                false
            }
        }

        // [전체 삭제] — 실수 방지를 위해 확인 다이얼로그를 거친다.
        binding.btnClearAll.setOnClickListener { confirmClearAll() }
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
    }

    /** 입력란의 필터어를 읽어 [currentFilter]에 반영하고 키보드를 내린 뒤 목록을 갱신한다. */
    private fun applyFilterFromInput() {
        currentFilter = binding.etFilter.text?.toString()?.trim().orEmpty()
        hideKeyboard()
        refreshLogs()
    }

    /** 전체 삭제 확인 다이얼로그. 확인 시 [LogRepository.clear] 호출 후 목록을 비운다. */
    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_clear_title)
            .setMessage(R.string.dialog_clear_message)
            .setNegativeButton(R.string.dialog_clear_cancel, null)
            .setPositiveButton(R.string.dialog_clear_confirm) { _, _ ->
                LogRepository.clear()
                refreshLogs()
                Toast.makeText(this, R.string.toast_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /**
     * LogRepository에서 [currentFilter] 적용 최신순 스냅샷을 가져와 목록을 갱신한다.
     * 0건이면 안내 문구를 표시한다(필터가 걸린 경우 필터 무결과 문구로 구분).
     */
    private fun refreshLogs() {
        val logs = LogRepository.query(currentFilter.ifEmpty { null }) // 최신순
        adapter.submit(logs)

        val empty = logs.isEmpty()
        binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvLogs.visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) {
            binding.tvEmpty.text = if (currentFilter.isEmpty()) {
                getString(R.string.log_view_empty)
            } else {
                getString(R.string.log_view_empty_filtered, currentFilter)
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etFilter.windowToken, 0)
    }
}
