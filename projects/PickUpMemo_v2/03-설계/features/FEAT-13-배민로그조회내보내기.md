# FEAT-13 — 배민 로그 조회 + 내보내기 화면

- 매칭 이슈: #13
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md` (v2.1 개선 추가 설계 — 개선요청 3, 조회/내보내기)

## 1. 목적
FEAT-12가 누적한 배민 로그를 앱 내에서 **최신순으로 조회**하고, 텍스트 파일로 **내보내기(공유)** 하는 화면을 만든다. 전체 삭제도 제공한다. v1의 로그 조회/내보내기 패턴(FileProvider + ACTION_SEND)을 재사용한다.

## 2. 범위
### 구현할 것
- `ui/BaeminLogActivity.kt`: RecyclerView로 `BaeminLogRepository.getAll()` 최신순 표시 + [내보내기] + [전체 삭제]
- `ui/BaeminLogAdapter.kt`: RecyclerView.Adapter
- `res/layout/activity_baemin_log.xml`, `res/layout/item_baemin_log.xml`
- `util/TimeFormat.kt`: 타임스탬프/내보내기 파일명 포맷(v1 패턴 축약 이식)
- `util/BaeminLogExporter.kt`: 로그를 txt로 만들어 FileProvider URI + `ACTION_SEND` 공유 Intent 생성
- `AndroidManifest.xml`: `BaeminLogActivity` 등록 + `FileProvider` provider 추가
- `res/xml/file_paths.xml`: FileProvider 노출 경로(`cache/exports`)
- `MainActivity`: [배민 로그] 버튼 추가 → `BaeminLogActivity`
- `strings.xml`: 문자열 추가

### 구현하지 않을 것
- 저장 계층/링버퍼/서비스 훅(FEAT-12) — 여기서는 Repository API만 호출
- 패키지 필터 입력(배민 한정이라 불필요), 로그 검색/페이지네이션(MVP 밖)
- 운영 매칭·메모 기능 변경

## 3. 입력 / 출력
### 입력
- `BaeminLogRepository.getAll(): List<BaeminLog>`, `BaeminLogRepository.clear()`(FEAT-12)
### 출력
- 화면에 로그 목록 렌더링, 내보내기 시 공유 시트(txt 파일), 전체 삭제 시 목록 비움

## 4. 동작 흐름
1. MainActivity [배민 로그] → `startActivity(Intent(this, BaeminLogActivity::class.java))`.
2. `BaeminLogActivity.onCreate`: `BaeminLogRepository.init(applicationContext)`(멱등) → `refresh()`.
3. `refresh()`: `lifecycleScope.launch { val logs = BaeminLogRepository.getAll(); adapter.submit(logs); 빈 상태 안내 토글 + 건수 표시 }`. `onResume`에서도 호출.
4. [내보내기]: `lifecycleScope.launch { val uri = BaeminLogExporter.export(this@..); startActivity(Intent.createChooser(BaeminLogExporter.buildShareIntent(uri), ...)) }`. 로그 0건이면 토스트 후 return.
5. [전체 삭제]: 확인 다이얼로그 → `BaeminLogRepository.clear()` → `refresh()` → 토스트.

## 5. 수정 예상 파일
- 신규: `.../ui/BaeminLogActivity.kt`, `.../ui/BaeminLogAdapter.kt`, `.../util/TimeFormat.kt`, `.../util/BaeminLogExporter.kt`
- 신규: `res/layout/activity_baemin_log.xml`, `res/layout/item_baemin_log.xml`, `res/xml/file_paths.xml`
- 수정: `MainActivity.kt`, `res/layout/activity_main.xml`, `AndroidManifest.xml`, `res/values/strings.xml`

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
// TimeFormat.kt (v1 축약 이식)
object TimeFormat {
    private val logFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    private val fileFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US) }
    fun formatTimestamp(millis: Long): String = logFormat.get()!!.format(Date(millis))
    fun exportFileName(millis: Long): String = "baemin_log_${fileFormat.get()!!.format(Date(millis))}.txt"
}
```
```kotlin
// BaeminLogAdapter.kt — item: 시각 + eventType + text(여러 줄 허용)
class BaeminLogAdapter : RecyclerView.Adapter<BaeminLogAdapter.VH>() {
    private val items = ArrayList<BaeminLog>()
    fun submit(list: List<BaeminLog>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    // item_baemin_log.xml: tvTime(시각), tvType(이벤트 타입, 작게), tvText(본문)
}
```
```kotlin
// BaeminLogExporter.kt
object BaeminLogExporter {
    private const val EXPORTS_DIR = "exports"
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    suspend fun export(context: Context): Uri = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val dir = File(app.cacheDir, EXPORTS_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }                 // 최신 1개만 노출
        val logs = BaeminLogRepository.getAll()                  // 최신순
        val sb = StringBuilder()
        for (e in logs) {
            sb.append('[').append(TimeFormat.formatTimestamp(e.capturedAt)).append("]\n")
            sb.append("Package: ").append(e.packageName).append('\n')
            sb.append("Type: ").append(e.eventType).append('\n')
            sb.append("Text: ").append(e.text).append("\n\n")
        }
        val file = File(dir, TimeFormat.exportFileName(System.currentTimeMillis()))
        file.writeText(sb.toString())
        FileProvider.getUriForFile(app, app.packageName + FILE_PROVIDER_SUFFIX, file)
    }
    fun buildShareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
```
**`res/xml/file_paths.xml`**:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="exports" path="exports/" />
</paths>
```
**매니페스트 추가**(application 내부):
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
<activity android:name=".ui.BaeminLogActivity" android:exported="false" />
```
- `activity_baemin_log.xml`: 상단 건수 TextView + [내보내기]·[전체 삭제] 버튼 Row + RecyclerView(rvLogs) + 빈 상태 TextView(tvEmpty, "기록된 배민 로그가 없습니다").
- `androidx.core:core-ktx`(FileProvider 포함)는 이미 의존성에 있음 — 추가 라이브러리 불필요.
- 문자열 추가: `title_baemin_log`("배민 로그"), `btn_baemin_log`("배민 로그"), `btn_export`("내보내기"), `btn_clear_all`("전체 삭제"), `baemin_log_empty`, `baemin_log_count`(`총 %d건`), `dialog_clear_title`, `dialog_clear_message`, `dialog_clear_confirm`, `dialog_clear_cancel`, `toast_export_empty`("내보낼 로그가 없습니다"), `toast_cleared`, `export_chooser_title`("배민 로그 공유").

## 7. 예외 처리
- 로그 0건 내보내기: 토스트 후 return(빈 파일 공유 방지).
- FileProvider authority 불일치 시 크래시 → `${applicationId}.fileprovider`와 export의 suffix 일치 확인.
- export/공유는 IO에서 파일 생성 후 메인에서 chooser 실행. `ActivityNotFoundException`은 try/catch + 토스트.
- 전체 삭제는 확인 다이얼로그로 오조작 방지.

## 8. 완료 조건
- 빌드 성공.
- MainActivity [배민 로그] → 목록 화면 진입, 누적 로그가 최신순으로 보임(0건이면 안내).
- [내보내기] → txt 파일 공유 시트가 뜨고, 공유된 파일에 로그가 들어 있다.
- [전체 삭제] → 확인 후 목록이 비워진다.

## 9. 테스트 방법
1. (FEAT-12로 로그 적재 후) [배민 로그] 진입 → 목록·건수 확인.
2. [내보내기] → 파일/메모 앱 등으로 공유 → txt 내용(시각/Type/Text) 확인.
3. [전체 삭제] → 다이얼로그 확인 → 목록 비워짐 + "삭제됨" 토스트.
4. 로그 0건 상태에서 [내보내기] → "내보낼 로그가 없습니다" 토스트.

## 10. 금지 사항
- 저장 계층/링버퍼/서비스 훅 변경 금지(FEAT-12 API 호출만).
- INTERNET 권한·네트워크 업로드 금지(로컬 공유 Intent/파일만).
- 운영 매칭·메모 기능 변경 금지.
- 새 라이브러리 추가 금지(core-ktx FileProvider 재사용).
