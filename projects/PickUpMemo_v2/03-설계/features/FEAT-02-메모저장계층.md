# FEAT-02 — 메모 저장 계층 (Room)

- 매칭 이슈: #2
- 작성일: 2026-06-17
- 상위 설계서: `03-설계/설계서.md`

## 1. 목적
메모(상호명·지점명·메모내용·태그)를 기기 로컬에 영속 저장하는 데이터 계층을 만든다. CRUD + 목록 reactive 조회 + 매칭용 전체 스냅샷을 제공해 이후 UI(FEAT-03/04)와 매칭 로직(FEAT-05/07)이 공통으로 사용한다.

## 2. 범위
### 구현할 것
- Room Entity `Memo`, DAO `MemoDao`, Database `AppDatabase`
- 싱글톤 `MemoRepository` (suspend CRUD + 목록 Flow + 동기 스냅샷)
### 구현하지 않을 것
- 화면/UI → FEAT-03/04
- 추출/매칭/중복억제 → FEAT-05
- 입력 검증 UI 로직 → FEAT-04 (Repository는 받은 값을 그대로 저장)

## 3. 입력 / 출력
### 입력
- UI에서 전달되는 메모 필드 값, 매칭 로직의 전체 메모 요청
### 출력
- Room DB `pickupmemo.db` 테이블 `memos`에 영속 저장
- 목록 `Flow<List<Memo>>`(updatedAt DESC), 매칭용 `List<Memo>` 스냅샷

## 4. 동작 흐름
1. UI/서비스가 `MemoRepository.init(context)`를 앱 시작/Activity onCreate/onServiceConnected에서 멱등 호출. **`init`은 DB 초기화만 수행한다(멱등). 캐시 로딩은 하지 않는다.**
2. **초기 캐시 로딩은 각 진입점이 코루틴에서 `refreshCache()`(suspend)를 명시적으로 호출**한다. `init`(일반 함수) 내부에서 suspend 함수를 직접 호출하지 않는다.
   ```kotlin
   MemoRepository.init(applicationContext)
   lifecycleScope.launch { MemoRepository.refreshCache() }   // Activity
   ```
   서비스(AccessibilityService)에는 `lifecycleScope`가 없으므로 FEAT-07의 서비스 전용 `CoroutineScope`에서 `refreshCache()`를 호출한다.
3. 추가/수정/삭제는 suspend 함수로 DAO 호출(IO 디스패처). 내부에서 `refreshCache()`로 캐시를 갱신한다.
4. 목록 화면은 `observeAll()` Flow 구독, 매칭 로직은 `getCachedSnapshot()` 동기 호출.

## 5. 수정 예상 파일 (전부 신규)
- `.../data/Memo.kt`
- `.../data/MemoDao.kt`
- `.../data/AppDatabase.kt`
- `.../data/MemoRepository.kt`

## 6. 데이터 구조 / 함수 / 클래스
```kotlin
// Memo.kt
@Entity(tableName = "memos")
data class Memo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val storeName: String,
    val branchName: String,
    val content: String,
    val tag: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

// MemoDao.kt
@Dao
interface MemoDao {
    @Query("SELECT * FROM memos ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Memo>>
    @Query("SELECT * FROM memos ORDER BY updatedAt DESC")
    suspend fun getAll(): List<Memo>
    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getById(id: Long): Memo?
    @Insert fun insertBlocking(memo: Memo): Long          // 또는 suspend insert
    @Update suspend fun update(memo: Memo)
    @Delete suspend fun delete(memo: Memo)
}

// AppDatabase.kt
@Database(entities = [Memo::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao
}
```
```kotlin
// MemoRepository.kt — 싱글톤 object
object MemoRepository {
    private lateinit var db: AppDatabase
    @Volatile private var inited = false
    fun init(context: Context) {            // DB 초기화만 (멱등). 캐시 로딩 안 함.
        if (inited) return
        synchronized(this) {
            if (inited) return
            db = Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "pickupmemo.db"
            ).build()
            inited = true
        }
    }
    fun observeAll(): Flow<List<Memo>> = db.memoDao().observeAll()
    suspend fun add(storeName: String, branchName: String, content: String, tag: String?): Long
    suspend fun update(memo: Memo)
    suspend fun delete(memo: Memo)
    suspend fun getById(id: Long): Memo?
    // 매칭 로직(FEAT-05/07)이 접근성 콜백 스레드에서 빠르게 읽도록 캐시 제공.
    @Volatile private var cache: List<Memo> = emptyList()
    suspend fun refreshCache() { cache = db.memoDao().getAll() }
    fun getCachedSnapshot(): List<Memo> = cache
}
```
- `add`/`update`/`delete` 후 내부에서 `refreshCache()` 호출해 캐시 갱신.
- DB 접근은 모두 `withContext(Dispatchers.IO)`.
- 매칭은 접근성 콜백(메인 스레드)에서 호출되므로 동기 `getCachedSnapshot()`을 제공(쿼리 블로킹 회피). 캐시는 **각 진입점의 코루틴에서 `refreshCache()`를 명시 호출(초기 1회)** + CRUD마다 갱신으로 채워진다. `init`은 캐시를 건드리지 않는다.

## 7. 예외 처리
- `init` 멱등(중복 호출 안전). `init`은 DB 빌드만 하고 suspend인 `refreshCache()`를 호출하지 않는다.
- 캐시 미초기화 시 `getCachedSnapshot()`는 빈 리스트 반환(NPE 없음). 초기 캐시 채움은 호출자(Activity의 `lifecycleScope` / 서비스의 전용 scope)가 코루틴에서 `refreshCache()`를 1회 호출해 책임진다.

## 8. 완료 조건
- 빌드 성공(KSP가 Room 코드 생성).
- 추가→조회 시 데이터가 보이고, 앱 재시작 후에도 유지(영속).
- `observeAll()` Flow가 변경 시 새 리스트를 방출.

## 9. 테스트 방법
- FEAT-03 목록 화면 연결 후 통합 확인. 단독으로는 빌드 성공 + Room 스키마 생성 로그 확인.
- (선택) 임시 테스트 호출로 add 후 getCachedSnapshot 비어있지 않음 확인.

## 10. 금지 사항
- 화면/어댑터 구현 금지(FEAT-03/04).
- 매칭·추출 로직 포함 금지(FEAT-05).
- 입력 검증을 Repository에서 강제하지 말 것(검증은 FEAT-04 UI 책임).
- 불필요한 라이브러리 추가 금지(Room/coroutines만).
