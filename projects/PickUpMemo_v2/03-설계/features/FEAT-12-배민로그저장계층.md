# FEAT-12 — 배민 로그 저장 계층 + 접근성 서비스 훅업

- 매칭 이슈: #12
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md` (v2.1 개선 추가 설계 — 개선요청 3, 저장 계층)

## 1. 목적
배민커넥트(`com.woowahan.bros`)에서 발생한 접근성 텍스트만 **누적 기록**하는 저장 계층을 만들고, 접근성 서비스(FEAT-07)에 보관 로직을 끼운다. 무한 누적을 막기 위해 **링버퍼(최대 1000건)** 로 상한을 두고 오래된 것을 자동 삭제한다. 조회/내보내기 UI는 FEAT-13이 이 계층을 사용한다.

## 2. 범위
### 구현할 것
- `data/BaeminLog.kt`: Room Entity(`baemin_logs` 테이블)
- `data/BaeminLogDao.kt`: insert + 링버퍼 trim + 전체조회 + count + clear
- `data/BaeminLogRepository.kt`: 싱글톤(suspend save/getAll/clear, 상한 `MAX_LOGS=1000`)
- `data/AppDatabase.kt` 수정: entities에 `BaeminLog` 추가, version 1→2, `baeminLogDao()` 추가, **Migration(1→2)** 정의(memos 테이블 보존)
- `data/MemoRepository.kt` 수정: Room 빌더에 `.addMigrations(AppDatabase.MIGRATION_1_2)` 추가 + DB 공유 접근자 `requireDatabase()` 추가
- `service/PickupAccessibilityService.kt` 수정: 배민 이벤트면(매칭 여부와 무관) 조립된 텍스트를 `BaeminLogRepository.save(...)`로 저장(가벼운 중복 억제 포함)

### 구현하지 않을 것
- 조회 화면 / 내보내기 / MainActivity 진입 버튼 → FEAT-13
- 운영 매칭(추출/매칭/팝업/화이트리스트) 동작 변경 — 로그 저장은 **추가**일 뿐, 기존 extract→match→popup 라인은 그대로 둔다
- 배민 외 패키지 로그 저장 금지(`TARGET_PACKAGE`에 도달한 이벤트만 저장됨 — 기존 필터가 이미 보장)

## 3. 입력 / 출력
### 입력
- `PickupAccessibilityService`가 배민 이벤트에서 조립한 `fullText`, `event.eventType`, `pkg`
### 출력
- Room DB `pickupmemo.db` 테이블 `baemin_logs`에 최대 1000건 누적(초과 시 가장 오래된 것 자동 삭제)

## 4. 동작 흐름
1. `onServiceConnected()`에 `BaeminLogRepository.init(applicationContext)` 추가(멱등).
2. `onAccessibilityEvent`에서 기존 패키지 필터를 통과(`pkg == TARGET_PACKAGE`)하고 `fullText`를 조립한 직후, `maybeLogBaemin(pkg, event.eventType, fullText)`를 호출한다. **그 아래 기존 extract→match→popup 라인은 변경하지 않는다.**
3. `maybeLogBaemin`: blank면 skip. 직전 저장 텍스트와 동일하고 `LOG_DEDUP_MS`(3000ms) 이내면 skip(content-changed 이벤트 폭주로 인한 동일 텍스트 중복 적재 방지). 통과 시 `serviceScope.launch { BaeminLogRepository.save(pkg, typeName, fullText) }`.
4. `BaeminLogRepository.save`: `insert` 후 `trimTo(MAX_LOGS)`로 1000건 초과분(오래된 것) 삭제.

## 5. 수정 예상 파일
- 신규: `.../data/BaeminLog.kt`, `.../data/BaeminLogDao.kt`, `.../data/BaeminLogRepository.kt`
- 수정: `.../data/AppDatabase.kt`, `.../data/MemoRepository.kt`, `.../service/PickupAccessibilityService.kt`

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
// BaeminLog.kt
@Entity(tableName = "baemin_logs")
data class BaeminLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val capturedAt: Long,          // epoch millis
    val packageName: String,       // 항상 com.woowahan.bros
    val eventType: String,         // AccessibilityEvent.eventTypeToString(...)
    val text: String               // 조립된 화면 텍스트(fullText)
)

// BaeminLogDao.kt
@Dao
interface BaeminLogDao {
    @Insert suspend fun insert(log: BaeminLog): Long
    @Query("DELETE FROM baemin_logs WHERE id NOT IN " +
           "(SELECT id FROM baemin_logs ORDER BY capturedAt DESC, id DESC LIMIT :cap)")
    suspend fun trimTo(cap: Int)
    @Query("SELECT * FROM baemin_logs ORDER BY capturedAt DESC, id DESC")
    suspend fun getAll(): List<BaeminLog>
    @Query("SELECT COUNT(*) FROM baemin_logs") suspend fun count(): Int
    @Query("DELETE FROM baemin_logs") suspend fun clear()
}
```
```kotlin
// AppDatabase.kt (수정)
@Database(entities = [Memo::class, BaeminLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao
    abstract fun baeminLogDao(): BaeminLogDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `baemin_logs` (" +
                    "`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                    "`capturedAt` INTEGER NOT NULL, " +
                    "`packageName` TEXT NOT NULL, " +
                    "`eventType` TEXT NOT NULL, " +
                    "`text` TEXT NOT NULL)"
                )
            }
        }
    }
}
```
> ★ 마이그레이션은 `memos` 테이블을 보존한다(`fallbackToDestructiveMigration` 금지 — 기존 사용자의 저장 메모가 지워지면 안 됨). CREATE TABLE 컬럼 정의는 Room이 생성하는 스키마와 일치해야 하므로 위 SQL을 그대로 사용한다.

```kotlin
// MemoRepository.kt (수정 포인트)
db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pickupmemo.db")
    .addMigrations(AppDatabase.MIGRATION_1_2)
    .build()
// ...
fun requireDatabase(): AppDatabase = db   // BaeminLogRepository/FEAT-13이 같은 DB 인스턴스 공유
```
```kotlin
// BaeminLogRepository.kt
object BaeminLogRepository {
    const val MAX_LOGS = 1000
    private fun dao(): BaeminLogDao = MemoRepository.requireDatabase().baeminLogDao()
    fun init(context: Context) { MemoRepository.init(context) }  // DB 빌드 보장(멱등)
    suspend fun save(packageName: String, eventType: String, text: String) =
        withContext(Dispatchers.IO) {
            dao().insert(BaeminLog(
                capturedAt = System.currentTimeMillis(),
                packageName = packageName, eventType = eventType, text = text))
            dao().trimTo(MAX_LOGS)
        }
    suspend fun getAll(): List<BaeminLog> = withContext(Dispatchers.IO) { dao().getAll() }
    suspend fun count(): Int = withContext(Dispatchers.IO) { dao().count() }
    suspend fun clear() = withContext(Dispatchers.IO) { dao().clear() }
}
```
```kotlin
// PickupAccessibilityService.kt 추가 부분 (기존 매칭 라인은 보존)
// onServiceConnected: 기존 코드에 한 줄 추가
BaeminLogRepository.init(applicationContext)

// onAccessibilityEvent: fullText 조립 직후 호출 (extract 라인 위)
val fullText = segments.joinToString(" / ")
maybeLogBaemin(pkg, event.eventType, fullText)         // ← 추가
val candidate = StoreExtractor.extract(fullText) ?: return  // 기존 유지
// ...이하 기존 동일...

private var lastLoggedText: String? = null
private var lastLoggedAt: Long = 0L
private fun maybeLogBaemin(pkg: String, eventType: Int, fullText: String) {
    if (fullText.isBlank()) return
    val now = System.currentTimeMillis()
    if (fullText == lastLoggedText && now - lastLoggedAt < LOG_DEDUP_MS) return
    lastLoggedText = fullText
    lastLoggedAt = now
    val typeName = AccessibilityEvent.eventTypeToString(eventType)
    serviceScope.launch { BaeminLogRepository.save(pkg, typeName, fullText) }
}
// companion: const val LOG_DEDUP_MS = 3000L
```
- 신규 import 필요: `androidx.room.migration.Migration`, `androidx.sqlite.db.SupportSQLiteDatabase`(AppDatabase), 서비스의 `BaeminLogRepository`.

## 7. 예외 처리
- 마이그레이션 누락 시 Room이 `IllegalStateException`. → 반드시 `addMigrations(MIGRATION_1_2)` 추가. destructive 금지.
- 저장은 `serviceScope`(IO)에서 비동기 → 접근성 콜백(메인) 지연 없음. DB 예외는 코루틴 내에서 격리(서비스 크래시 없음).
- 동일 화면 content-changed 폭주: `LOG_DEDUP_MS` 가드로 동일 텍스트 3초 내 중복 저장 방지.
- 링버퍼: `trimTo`가 매 insert 후 1000건 초과분을 삭제(무한 누적 차단).

## 8. 완료 조건
- 빌드 성공(KSP가 BaeminLog/Dao 코드 생성).
- 기존 메모(memos)가 보존된 채 앱이 정상 실행(마이그레이션 성공).
- 배민커넥트 화면 진입 시 `baemin_logs`에 텍스트가 누적되고, 1000건을 넘지 않는다.
- 배민 외 패키지 이벤트로는 로그가 저장되지 않는다.

## 9. 테스트 방법
1. (기존 v2 설치본이 있으면) 업그레이드 설치 후 기존 메모가 그대로 보이는지 확인(마이그레이션 검증).
2. 접근성 권한 ON 후 배민커넥트 화면 사용 → (FEAT-13 조회 화면 또는 임시 로그) 로그 적재 확인.
3. 카카오톡/카카오맵 사용 → 로그 미적재 확인.
4. (장시간/반복) 건수가 1000을 넘지 않고 오래된 것이 사라지는지 확인.

## 10. 금지 사항
- 운영 매칭(extract→match→popup) 라인 변경·화이트리스트 우회 금지(로그 저장은 추가만).
- `fallbackToDestructiveMigration` 사용 금지(기존 메모 보존).
- 조회/내보내기 UI 선구현 금지(FEAT-13).
- 새 라이브러리 추가 금지(Room/coroutines만). INTERNET 금지.
