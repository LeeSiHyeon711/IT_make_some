package com.itmakesome.pickupmemo.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.itmakesome.pickupmemo.data.LogFileStore
import com.itmakesome.pickupmemo.data.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 로그 내보내기 (설계서 4-4 / 5-3 / 5-4, 7장 LogExporter, 이슈 E-1 #10).
 *
 * 내보내기 플로우(설계서 4-4):
 *  1. **compaction** — 내보내기 직전(설계서 5-3 ②) 인메모리 deque 기준으로 내부 로그 파일을
 *     5,000건으로 재작성한다([LogRepository.compactNow]). → 내보낸 txt가 한도를 정확히 지킨다.
 *  2. **export txt 생성** — compaction된 내부 파일([LogFileStore.LOG_FILE_NAME])을
 *     `filesDir/exports/pickupmemo_log_yyyyMMdd_HHmmss.txt`(설계서 4-1/4-4 파일명 규칙)로 복사한다.
 *     export 디렉터리는 FileProvider 노출 경로(설계서 6-4 `file_paths.xml`)다.
 *  3. **FileProvider URI** — `${applicationId}.fileprovider` authority로 content:// URI를 얻는다.
 *  4. **ACTION_SEND 공유** — 호출부([MainActivity])가 [buildShareIntent]로 만든 공유 시트를 띄운다.
 *
 * 파일 IO(compaction·복사)는 [Dispatchers.IO]에서 수행해 호출 스레드(메인)를 막지 않는다(설계서 2-1).
 * 직전 내보내기 파일은 누적되지 않도록 새 파일 생성 전에 정리한다(공유 시트에 최신 1개만 노출).
 */
object LogExporter {

    /** FileProvider 노출 디렉터리명 (설계서 5-4 / 6-4 `file_paths.xml`의 `exports/`). */
    private const val EXPORTS_DIR = "exports"

    /** 매니페스트 FileProvider authority 접미사(`${applicationId}.fileprovider`, 설계서 6-4). */
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    /**
     * compaction → export txt 생성 → FileProvider URI까지 수행하고 공유용 content:// URI를 반환한다.
     * IO 디스패처에서 동작하는 suspend 함수다(파일 IO).
     *
     * @return 생성된 내보내기 파일의 FileProvider content:// URI
     */
    suspend fun export(context: Context): Uri = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext

        // 1) 내보내기 직전 compaction(설계서 5-3 ②). 이미 IO 스레드이므로 동기 호출.
        LogRepository.compactNow()

        // 2) export 디렉터리 준비 + 이전 내보내기 파일 정리(최신 1개만 노출).
        val exportsDir = File(appContext.filesDir, EXPORTS_DIR).apply { mkdirs() }
        exportsDir.listFiles()?.forEach { it.delete() }

        // 3) compaction된 내부 로그 파일을 export txt로 복사(설계서 4-4 파일명 규칙).
        val source = File(appContext.filesDir, LogFileStore.LOG_FILE_NAME)
        val exportFile = File(exportsDir, TimeFormat.exportFileName(System.currentTimeMillis()))
        if (source.exists()) {
            source.copyTo(exportFile, overwrite = true)
        } else {
            // 로그가 한 건도 없으면 빈 파일을 만들어 공유 자체는 동작하게 한다.
            exportFile.writeText("")
        }

        // 4) FileProvider URI 발급(설계서 6-4).
        FileProvider.getUriForFile(appContext, appContext.packageName + FILE_PROVIDER_SUFFIX, exportFile)
    }

    /**
     * 내보내기 파일 URI로 `ACTION_SEND` 공유 Intent를 만든다(설계서 4-4).
     * `text/plain` + `FLAG_GRANT_READ_URI_PERMISSION`으로 수신 앱에 읽기 권한을 일시 부여한다.
     * 호출부는 `Intent.createChooser`로 감싸 공유 시트를 띄운다.
     */
    fun buildShareIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
