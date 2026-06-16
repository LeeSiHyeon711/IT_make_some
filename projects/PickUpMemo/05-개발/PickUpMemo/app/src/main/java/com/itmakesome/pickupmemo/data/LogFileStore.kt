package com.itmakesome.pickupmemo.data

import android.content.Context
import com.itmakesome.pickupmemo.util.TimeFormat
import java.io.File

/**
 * 내부 저장소 단일 txt 파일에 대한 로그 IO (설계서 5-2 / 5-3 / 5-4, 7장 LogFileStore).
 *
 * 본 이슈(B-2 / #4) 책임 범위:
 * - [append] / [appendAll] : 매 건 1블록 append (성능 — 전건 재작성 금지, 설계서 5-3)
 * - [readAll]              : 파일을 파싱해 [LogEntry] 목록으로 복원 (앱 재시작 시 deque 적재용)
 * - [clear]               : 파일 비우기/삭제 (전체 삭제 시)
 * - [rewrite]             : compaction — 주어진 목록으로 파일을 전체 재작성하며 최대 [MAX_ENTRIES]건으로 정리
 * - [blockCount] / [needsCompaction] : 히스테리시스 판단 보조 (append 누적이 한도의 ~1.2배 초과 시 정리)
 *
 * 동시성: 접근성/알림 두 서비스가 동시에 add를 호출할 수 있으므로 모든 파일 접근을 [lock]으로 직렬화한다.
 * 코루틴(IO 디스패처) 래핑·인메모리 deque 관리·compaction 호출 시점 결정은 상위 LogRepository(B-3 / #5) 책임이다.
 * 내보내기 파일 생성/공유(FileProvider, ACTION_SEND)는 LogExporter(E-1 / #10) 책임으로 본 이슈 범위가 아니다.
 *
 * 저장 위치: `filesDir/pickupmemo_log.txt` (앱 전용, 외부 비노출 — 설계서 5-4)
 */
class LogFileStore(private val logFile: File) {

    constructor(context: Context) : this(File(context.filesDir, LOG_FILE_NAME))

    private val lock = Any()

    /**
     * 마지막으로 알고 있는 파일 내 블록 수. -1은 "아직 미확인".
     * append마다 증가, [readAll]/[rewrite]/[clear] 시 실제 값으로 재설정한다.
     * 이 값으로 [needsCompaction]을 매 append마다 파일 전체를 다시 읽지 않고 판단한다.
     */
    private var cachedBlockCount: Int = -1

    /** 로그 1건을 파일 끝에 1블록 append한다. 블록 사이는 빈 줄로 구분(설계서 5-2). */
    fun append(entry: LogEntry) {
        synchronized(lock) {
            ensureParent()
            logFile.appendText(entry.toLogBlock() + BLOCK_SEPARATOR)
            if (cachedBlockCount >= 0) cachedBlockCount++
        }
    }

    /** 여러 건을 한 번에 append한다(파일 1회 open). 순서는 입력 순서 그대로 기록된다. */
    fun appendAll(entries: List<LogEntry>) {
        if (entries.isEmpty()) return
        synchronized(lock) {
            ensureParent()
            val sb = StringBuilder()
            for (e in entries) sb.append(e.toLogBlock()).append(BLOCK_SEPARATOR)
            logFile.appendText(sb.toString())
            if (cachedBlockCount >= 0) cachedBlockCount += entries.size
        }
    }

    /**
     * 파일을 읽어 [LogEntry] 목록으로 복원한다(파일 기록 순서 = 오래된 → 최신).
     * 형식이 깨진 블록은 건너뛴다(육안 분석이 목적이므로 관대하게 처리, 설계서 5-2).
     * 파일이 없으면 빈 목록을 반환한다.
     */
    fun readAll(): List<LogEntry> {
        synchronized(lock) {
            if (!logFile.exists()) {
                cachedBlockCount = 0
                return emptyList()
            }
            val lines = logFile.readText().split("\n")
            val result = ArrayList<LogEntry>()

            var i = 0
            while (i < lines.size) {
                val header = HEADER_REGEX.matchEntire(lines[i].trim())
                if (header == null) {
                    i++
                    continue
                }
                val millis = TimeFormat.parseTimestamp(header.groupValues[1])
                if (millis == null) {
                    i++
                    continue
                }
                // 헤더 다음 줄부터 다음 헤더(또는 EOF) 직전까지가 한 블록의 본문 라인.
                val bodyLines = ArrayList<String>()
                i++
                while (i < lines.size && HEADER_REGEX.matchEntire(lines[i].trim()) == null) {
                    bodyLines.add(lines[i])
                    i++
                }
                parseBlock(millis, trimTrailingBlanks(bodyLines))?.let { result.add(it) }
            }
            cachedBlockCount = result.size
            return result
        }
    }

    /** 로그 파일을 비운다(존재하면 삭제). 전체 삭제 시 사용(설계서 5-3). */
    fun clear() {
        synchronized(lock) {
            if (logFile.exists()) logFile.delete()
            cachedBlockCount = 0
        }
    }

    /**
     * compaction — 주어진 목록 기준으로 파일을 전체 재작성한다(설계서 5-3).
     * 목록이 [MAX_ENTRIES]를 초과하면 **가장 오래된 것을 버리고 마지막 [MAX_ENTRIES]건만** 기록한다.
     * 호출 시점은 상위(앱 시작 후 로드, 내보내기 직전, 히스테리시스 초과 시)가 결정한다.
     *
     * @param entries 오래된 → 최신 순서의 전체 로그 목록(보통 인메모리 deque를 정렬한 결과)
     */
    fun rewrite(entries: List<LogEntry>) {
        synchronized(lock) {
            ensureParent()
            val capped =
                if (entries.size > MAX_ENTRIES) entries.subList(entries.size - MAX_ENTRIES, entries.size)
                else entries

            val sb = StringBuilder()
            for (e in capped) sb.append(e.toLogBlock()).append(BLOCK_SEPARATOR)
            logFile.writeText(sb.toString())
            cachedBlockCount = capped.size
        }
    }

    /** 현재 파일에 들어 있는 블록(로그 건) 수. 캐시가 없으면 1회 파일을 세어 갱신한다. */
    fun blockCount(): Int {
        synchronized(lock) {
            if (cachedBlockCount >= 0) return cachedBlockCount
            val count = if (!logFile.exists()) {
                0
            } else {
                logFile.useLines { seq ->
                    seq.count { HEADER_REGEX.matchEntire(it.trim()) != null }
                }
            }
            cachedBlockCount = count
            return count
        }
    }

    /**
     * 히스테리시스 판단(설계서 5-3): append 누적 블록 수가 한도의 약 1.2배([COMPACTION_THRESHOLD])를
     * 초과하면 true. 상위는 이때 인메모리 deque 기준으로 [rewrite]를 호출해 파일을 5,000건으로 정리한다.
     */
    fun needsCompaction(): Boolean = blockCount() > COMPACTION_THRESHOLD

    private fun ensureParent() {
        logFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
    }

    /** 한 블록 본문 라인들을 LogEntry로 파싱한다. 필수 필드(body)가 없으면 null. */
    private fun parseBlock(millis: Long, body: List<String>): LogEntry? {
        var pkg = ""
        var type = LogType.SYSTEM
        var title: String? = null
        var text: String? = null

        var idx = 0
        while (idx < body.size) {
            val line = body[idx]
            when {
                line.startsWith(PREFIX_PACKAGE) -> pkg = line.removePrefix(PREFIX_PACKAGE)
                line.startsWith(PREFIX_TYPE) ->
                    type = LogType.fromDisplayName(line.removePrefix(PREFIX_TYPE))
                line.startsWith(PREFIX_TITLE) -> title = line.removePrefix(PREFIX_TITLE)
                line.startsWith(PREFIX_BODY) -> {
                    text = joinFrom(line.removePrefix(PREFIX_BODY), body, idx + 1)
                    idx = body.size
                    continue
                }
                line.startsWith(PREFIX_TEXT) -> {
                    text = joinFrom(line.removePrefix(PREFIX_TEXT), body, idx + 1)
                    idx = body.size
                    continue
                }
            }
            idx++
        }

        val finalBody = text ?: return null
        return LogEntry(
            timestampMillis = millis,
            type = type,
            packageName = pkg,
            title = title,
            body = finalBody
        )
    }

    /** 본문 첫 줄(prefix 제거분)에 나머지 라인을 개행으로 이어 붙인다(멀티라인 본문 보존). */
    private fun joinFrom(first: String, lines: List<String>, from: Int): String {
        if (from >= lines.size) return first
        val sb = StringBuilder(first)
        for (j in from until lines.size) sb.append('\n').append(lines[j])
        return sb.toString()
    }

    /** 블록 구분용 빈 줄로 생긴 후행 공백 라인을 제거한다. */
    private fun trimTrailingBlanks(lines: List<String>): List<String> {
        var end = lines.size
        while (end > 0 && lines[end - 1].isBlank()) end--
        return lines.subList(0, end)
    }

    companion object {
        /** 내부 로그 파일명 (설계서 5-4). */
        const val LOG_FILE_NAME = "pickupmemo_log.txt"

        /** 건수 한도 (설계서 5-3 / PRD 4-2). */
        const val MAX_ENTRIES = 5_000

        /** 히스테리시스 임계: 한도의 약 1.2배. 초과 시 compaction(설계서 5-3). */
        const val COMPACTION_THRESHOLD = 6_000

        /** 블록 본문 뒤에 붙는 구분자(빈 줄 1개). */
        private const val BLOCK_SEPARATOR = "\n\n"

        private const val PREFIX_PACKAGE = "Package: "
        private const val PREFIX_TYPE = "Type: "
        private const val PREFIX_TITLE = "Title: "
        private const val PREFIX_BODY = "Body: "
        private const val PREFIX_TEXT = "Text: "

        /** `[yyyy-MM-dd HH:mm:ss]` 블록 헤더 식별용 정규식(본문 내 `[`와 구분). */
        private val HEADER_REGEX =
            Regex("""^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})]$""")
    }
}
