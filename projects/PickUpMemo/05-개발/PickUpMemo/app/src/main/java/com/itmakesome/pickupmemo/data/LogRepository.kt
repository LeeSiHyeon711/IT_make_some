package com.itmakesome.pickupmemo.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 로그 저장소 싱글톤 (설계서 5-3 / 7장 LogRepository).
 *
 * **인메모리 [ArrayDeque]<[LogEntry]>(최대 [MAX_ENTRIES]건)를 "건수 한도의 단일 기준(source of truth)"으로 삼는다.**
 * deque의 **앞(index 0)이 항상 최신**이며(설계서 4-2 "앞이 최신"), 추가 시 5,000건을 초과하면 가장 오래된 항목(뒤)을 즉시 drop한다.
 * 파일([LogFileStore])은 인메모리의 **영속 미러**다.
 *
 * 본 이슈(B-3 / #5) 책임 범위:
 * - [initialize] : 앱 시작 시 파일 로드 → deque 적재(마지막 [MAX_ENTRIES]건만) → 파일 compaction(설계서 5-3 ①).
 * - [add]        : deque 앞에 추가(초과분 drop) + 파일 1블록 append(코루틴, IO 스레드) + 히스테리시스 초과 시 compaction(설계서 5-3 ④).
 * - [query]      : 최신순 스냅샷 조회(+ 패키지명 필터). LogViewActivity(F-1/F-2)에서 사용.
 * - [clear]      : 인메모리 + 파일 전체 삭제(설계서 5-3 ③).
 * - [compactNow] / [requestCompaction] : deque 기준 파일 재작성. 내보내기 직전(설계서 5-3 ②)에 LogExporter(E-1)가 사용.
 *
 * 동시성: 접근성/알림 두 서비스가 동시에 [add]를 호출할 수 있으므로 deque 접근은 모두 [lock]으로 직렬화한다.
 * 파일 IO는 [scope](IO 디스패처)에서 처리해 호출 스레드(메인/서비스 콜백)를 막지 않는다(설계서 2-1 코루틴 방침).
 *
 * 수집 서비스 본문(C-1 #6 / C-2 #7), 조회 UI(F #11·#12), 내보내기 파일/공유(E-1 #10)는 본 이슈 범위가 아니며,
 * 이 저장소가 제공하는 [add]/[query]/[clear]/[compactNow] API를 각 이슈에서 호출한다.
 */
object LogRepository {

    /** 건수 한도 — 인메모리 deque와 파일 compaction 모두 [LogFileStore.MAX_ENTRIES]를 단일 기준으로 공유한다. */
    const val MAX_ENTRIES = LogFileStore.MAX_ENTRIES

    private val lock = Any()

    /** index 0 = 최신, 마지막 = 가장 오래됨. [MAX_ENTRIES]건을 넘지 않게 유지한다. */
    private val deque = ArrayDeque<LogEntry>()

    /** 파일 미러. [initialize] 이전에는 null(인메모리만 동작, 파일 IO는 조용히 건너뜀). */
    @Volatile
    private var fileStore: LogFileStore? = null

    /** 파일 IO 전용 스코프(앱 생명 동안 유지). 단일 디스패처가 아니므로 deque는 [lock]으로 별도 보호. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized = false

    /**
     * 앱 시작 시 1회 호출(여러 번 호출해도 안전 — 멱등). 파일을 읽어 deque에 적재한다.
     *
     * 파일은 오래된 → 최신 순서이므로(설계서 5-2/5-3), 마지막 [MAX_ENTRIES]건만 남기고
     * 앞 초과분은 무시한다(설계서 5-3 "마지막 5,000블록만 적재"). 적재 후 파일을 한 번 compaction해
     * 이전 실행에서 누적된 초과분을 5,000건으로 정리한다(설계서 5-3 ①).
     */
    fun initialize(context: Context) {
        synchronized(lock) {
            if (initialized) return
            val store = LogFileStore(context.applicationContext)
            fileStore = store

            val loaded = store.readAll() // 오래된 → 최신
            deque.clear()
            val start = maxOf(0, loaded.size - MAX_ENTRIES)
            // 최신(loaded 끝)부터 addLast → deque 앞이 최신이 되도록 적재.
            for (i in loaded.lastIndex downTo start) {
                deque.addLast(loaded[i])
            }
            initialized = true
        }
        // 로드 직후 파일 정리(설계서 5-3 ①). IO 스레드에서 비동기 수행.
        requestCompaction()
    }

    /**
     * 로그 1건을 추가한다(설계서 4-2 흐름).
     * - 인메모리: deque 앞에 추가, [MAX_ENTRIES] 초과 시 가장 오래된 것(뒤) drop.
     * - 파일: 1블록 append(빠름). append 누적이 히스테리시스 임계를 넘으면 deque 기준으로 compaction.
     */
    fun add(entry: LogEntry) {
        synchronized(lock) {
            deque.addFirst(entry)
            while (deque.size > MAX_ENTRIES) {
                deque.removeLast()
            }
        }
        val store = fileStore ?: return
        scope.launch {
            store.append(entry)
            if (store.needsCompaction()) {
                compactNow()
            }
        }
    }

    /**
     * 현재 로그를 **최신순(index 0 = 최신)** 스냅샷 목록으로 반환한다.
     * [packageFilter]가 비어 있지 않으면 패키지명에 해당 문자열을 포함하는 로그만 반환한다(설계서 4-2 필터).
     * 반환 목록은 호출 시점 복사본이므로 이후 deque 변경의 영향을 받지 않는다.
     */
    fun query(packageFilter: String? = null): List<LogEntry> {
        synchronized(lock) {
            val term = packageFilter?.trim()
            return if (term.isNullOrEmpty()) {
                deque.toList()
            } else {
                deque.filter { it.packageName.contains(term, ignoreCase = true) }
            }
        }
    }

    /** 현재 보관 중인 로그 건수(인메모리 기준 = 단일 기준). */
    fun size(): Int = synchronized(lock) { deque.size }

    /**
     * 전체 삭제(설계서 5-3 ③). 인메모리 deque를 비우고 파일도 삭제한다.
     */
    fun clear() {
        synchronized(lock) { deque.clear() }
        val store = fileStore ?: return
        scope.launch { store.clear() }
    }

    /**
     * deque 기준으로 파일을 재작성(compaction)한다 — **IO 스레드에서 동기 수행**.
     * deque는 최신 → 오래됨 순이므로 오래된 → 최신으로 뒤집어 [LogFileStore.rewrite]에 넘긴다
     * (rewrite가 [MAX_ENTRIES]로 캡). 내보내기 직전(설계서 5-3 ②) LogExporter가 코루틴 내에서 호출한다.
     */
    fun compactNow() {
        val store = fileStore ?: return
        val ordered = synchronized(lock) {
            // deque: 최신→오래됨. 뒤집어 오래된→최신 순서의 복사본 생성.
            ArrayList(deque).apply { reverse() }
        }
        store.rewrite(ordered)
    }

    /** [compactNow]를 IO 스코프에서 비동기로 요청한다(호출 스레드를 막지 않음). */
    fun requestCompaction() {
        if (fileStore == null) return
        scope.launch { compactNow() }
    }

    /**
     * 내보내기 등에서 compaction 완료를 보장해야 할 때 사용하는 suspend 버전.
     * IO 디스패처에서 [compactNow]를 수행하고 끝날 때까지 대기한다.
     */
    suspend fun compactAndAwait() = withContext(Dispatchers.IO) { compactNow() }
}
