package com.krunventures.meetingrecorder.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

class ConfigManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("meeting_config", Context.MODE_PRIVATE)

    // === API Keys ===
    var geminiApiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(v) = prefs.edit().putString("gemini_api_key", v).apply()

    var claudeApiKey: String
        get() = prefs.getString("claude_api_key", "") ?: ""
        set(v) = prefs.edit().putString("claude_api_key", v).apply()

    var clovaInvokeUrl: String
        get() = prefs.getString("clova_invoke_url", "") ?: ""
        set(v) = prefs.edit().putString("clova_invoke_url", v).apply()

    var clovaSecretKey: String
        get() = prefs.getString("clova_secret_key", "") ?: ""
        set(v) = prefs.edit().putString("clova_secret_key", v).apply()

    // V2.0: OpenAI API (Whisper STT + GPT-4o 요약)
    var chatGptApiKey: String
        get() = prefs.getString("chatgpt_api_key", "") ?: ""
        set(v) = prefs.edit().putString("chatgpt_api_key", v).apply()

    // === Engine Selection ===
    var sttEngine: String
        get() = prefs.getString("stt_engine", "clova") ?: "clova"
        set(v) = prefs.edit().putString("stt_engine", v).apply()

    var aiEngine: String
        get() = prefs.getString("ai_engine", "gemini") ?: "gemini"
        set(v) = prefs.edit().putString("ai_engine", v).apply()

    var summaryMode: String
        get() = prefs.getString("summary_mode", "speaker") ?: "speaker"
        set(v) = prefs.edit().putString("summary_mode", v).apply()

    // ★ v3.5: 녹음 입력 게인(증폭 배수). AudioRecorderManager가 PCM 샘플에 곱해 음량을 키운다.
    // 1.0 = 기본 트림(★v3.7.13: 실제 증폭은 소프트웨어 AGC가 자동 처리 → 사용자 게인은 추가 트림). 최대 8.0.
    var recordingGain: Float
        get() = prefs.getFloat("recording_gain", 1.0f)
        set(v) = prefs.edit().putFloat("recording_gain", v.coerceIn(1.0f, 8.0f)).apply()

    // ★ v3.10: 차량/블루투스 마이크 사용 토글 (녹음 시작 버튼 옆).
    //  ON  → BT 헤드셋/차량이 연결돼 있으면 SCO(핸즈프리 마이크)로 녹음, 없으면 폰 마이크(자동 폴백)
    //  OFF → 항상 폰 내장 마이크(고품질). 기본 OFF(평소 고음질 유지, 차에서만 켜기)
    var useBluetoothMic: Boolean
        get() = prefs.getBoolean("use_bluetooth_mic", false)
        set(v) = prefs.edit().putBoolean("use_bluetooth_mic", v).apply()

    // === Storage Paths (Android 16 호환 — 앱 전용 디렉토리 사용) ===
    // getExternalFilesDir()는 권한 없이 읽기/쓰기 가능, 앱 삭제 시 같이 삭제됨
    private val defaultBaseDir: String
        get() {
            // 우선 앱 전용 외부 저장소 사용 (권한 불필요)
            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (appDir != null) {
                return File(appDir, "Meeting recording").absolutePath
            }
            // 폴백: 내부 저장소
            return File(context.filesDir, "Meeting recording").absolutePath
        }

    // User-selected base directory via SAF (Storage Access Framework)
    var userSelectedBaseDir: String
        get() = prefs.getString("user_selected_base_dir", "") ?: ""
        set(v) = prefs.edit().putString("user_selected_base_dir", v).apply()

    // User-selected directories for separate folders (v2.0)
    var userSelectedAudioDir: String
        get() = prefs.getString("user_selected_audio_dir", "") ?: ""
        set(v) = prefs.edit().putString("user_selected_audio_dir", v).apply()

    var userSelectedSttDir: String
        get() = prefs.getString("user_selected_stt_dir", "") ?: ""
        set(v) = prefs.edit().putString("user_selected_stt_dir", v).apply()

    var userSelectedSummaryDir: String
        get() = prefs.getString("user_selected_summary_dir", "") ?: ""
        set(v) = prefs.edit().putString("user_selected_summary_dir", v).apply()

    // ★ v3.0: Obsidian vault 저장 경로 (SAF URI)
    var obsidianVaultDir: String
        get() = prefs.getString("obsidian_vault_dir", "") ?: ""
        set(v) = prefs.edit().putString("obsidian_vault_dir", v).apply()

    // ★ v3.11: 통화녹음 폴더 (SAF tree URI) — Recordings/TPhoneCallRecords
    //   Scoped Storage 라 File API 접근 불가 → 사용자가 1회 폴더를 등록하고 영구 권한을 보관한다.
    var callRecordDirUri: String
        get() = prefs.getString("call_record_dir_uri", "") ?: ""
        set(v) = prefs.edit().putString("call_record_dir_uri", v).apply()

    /** ★ v3.11: 이미 STT·요약을 마친 통화녹음 파일명 — 목록에 ✅ 표시해 중복 요약을 막는다 */
    val processedCallRecordings: Set<String>
        get() = prefs.getStringSet("processed_call_recordings", emptySet()) ?: emptySet()

    fun markCallRecordingProcessed(fileName: String) {
        if (fileName.isBlank()) return
        // getStringSet 이 돌려준 Set 은 직접 수정하면 안 됨 (문서화된 제약) → 복사본에 추가
        val updated = processedCallRecordings.toMutableSet()
        if (!updated.add(fileName)) return
        prefs.edit().putStringSet("processed_call_recordings", updated).apply()
    }

    var recordingDir: String
        get() {
            // 1. Check if user has selected a SAF directory
            val safUri = userSelectedBaseDir
            if (safUri.isNotBlank()) {
                return safUri  // Return the SAF URI string
            }
            // 2. Check saved dir (v2)
            val saved = prefs.getString("recording_dir_v2", null)
            if (saved != null) return saved
            // 3. Migrate from v1 if exists
            val legacyDir = prefs.getString("recording_dir", null)
            if (legacyDir != null && legacyDir.startsWith("/storage/emulated")) {
                Log.w(TAG, "Migrating from legacy storage path: $legacyDir")
            }
            // 4. Fall back to default
            return defaultBaseDir
        }
        set(v) = prefs.edit().putString("recording_dir_v2", v).apply()

    /**
     * 녹음 중 임시 저장 디렉토리 — 항상 앱 전용 디렉토리 사용 (권한 불필요, EPERM 방지)
     * MediaRecorder는 File API로 직접 쓰기하므로 SAF 경로 사용 불가.
     * 녹음 완료 후 saveRecordingImmediately()에서 사용자 선택 폴더로 복사.
     */
    val tempRecordingDir: File
        get() {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, "recording_temp")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    var audioSubdir: String
        get() = prefs.getString("audio_subdir", "녹음파일") ?: "녹음파일"
        set(v) = prefs.edit().putString("audio_subdir", v).apply()

    var summarySubdir: String
        get() = prefs.getString("summary_subdir", "회의록(요약)") ?: "회의록(요약)"
        set(v) = prefs.edit().putString("summary_subdir", v).apply()

    var sttSubdir: String
        get() = prefs.getString("stt_subdir", "STT변환") ?: "STT변환"
        set(v) = prefs.edit().putString("stt_subdir", v).apply()

    /**
     * ★ audioSaveDir/sttSaveDir/summarySaveDir는 항상 앱 전용 디렉토리를 반환.
     * Android 11+ Scoped Storage에서 SAF URI를 File 경로로 변환하면 EPERM 발생.
     * 파일은 앱 전용 디렉토리에 저장 후, copyFileToSafDir()로 사용자 선택 폴더에 복사.
     */
    val audioSaveDir: File
        get() {
            val dir = File(defaultBaseDir, audioSubdir)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    val sttSaveDir: File
        get() {
            val dir = File(defaultBaseDir, sttSubdir)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    val summarySaveDir: File
        get() {
            val dir = File(defaultBaseDir, summarySubdir)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * SAF URI로 사용자가 선택한 폴더에 파일 복사.
     * ContentResolver + DocumentFile API 사용 — Android 11+ Scoped Storage 호환.
     *
     * @param srcFile 앱 전용 디렉토리에 저장된 파일
     * @param safUriString SAF tree URI (content://...)
     * @return 성공 여부
     */
    fun copyFileToSafDir(srcFile: File, safUriString: String): Boolean {
        if (safUriString.isBlank()) return false
        if (!srcFile.exists()) {
            Log.e(TAG, "SAF 복사 실패: 원본 파일 없음 (${srcFile.absolutePath})")
            return false
        }
        return try {
            val treeUri = Uri.parse(safUriString)

            // ★ 방법 1: DocumentsContract 직접 사용 (가장 안정적)
            // DocumentFile.createFile()의 MIME→확장자 자동 변환 문제를 완전히 우회
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

            // 동일 이름 파일 삭제 (DocumentFile로 검색)
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (treeDoc != null) {
                // 확장자 유무 모두 검색하여 중복 파일 정리
                treeDoc.findFile(srcFile.name)?.delete()
                treeDoc.findFile(srcFile.nameWithoutExtension)?.delete()
            }

            // application/octet-stream으로 생성 — 확장자를 시스템이 변경하지 않음
            // displayName에 확장자를 포함시켜 원래 파일명 그대로 보존
            val newDocUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                "application/octet-stream",
                srcFile.name  // 확장자 포함 전체 파일명 (예: "회의록.md")
            )

            if (newDocUri == null) {
                Log.e(TAG, "SAF createDocument 실패: ${srcFile.name}")
                // ★ 폴백: DocumentFile API 재시도
                return copyFileToSafDirFallback(srcFile, treeUri)
            }

            context.contentResolver.openOutputStream(newDocUri)?.use { out ->
                srcFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: run {
                Log.e(TAG, "SAF openOutputStream 실패: $newDocUri")
                return false
            }

            Log.d(TAG, "✅ SAF 복사 성공: ${srcFile.name} → $newDocUri")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SAF 권한 만료: ${srcFile.name} → $safUriString", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ SAF 복사 실패: ${srcFile.name} → $safUriString", e)
            // 폴백 시도
            try {
                return copyFileToSafDirFallback(srcFile, Uri.parse(safUriString))
            } catch (e2: Exception) {
                Log.e(TAG, "❌ SAF 폴백도 실패: ${srcFile.name}", e2)
                false
            }
        }
    }

    /**
     * DocumentFile API를 사용한 SAF 복사 폴백
     * DocumentsContract가 실패하는 기기에서 사용
     */
    private fun copyFileToSafDirFallback(srcFile: File, treeUri: Uri): Boolean {
        val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        treeDoc.findFile(srcFile.name)?.delete()

        // 모든 파일 타입을 application/octet-stream으로 생성
        val newDoc = treeDoc.createFile("application/octet-stream", srcFile.name) ?: run {
            // 한 번 더 시도: text/plain으로
            treeDoc.createFile("text/plain", srcFile.nameWithoutExtension) ?: return false
        }
        context.contentResolver.openOutputStream(newDoc.uri)?.use { out ->
            srcFile.inputStream().use { input -> input.copyTo(out) }
        } ?: return false

        Log.d(TAG, "✅ SAF 폴백 복사 성공: ${srcFile.name} → ${newDoc.uri}")
        return true
    }

    /**
     * 파일 타입별 사용자 선택 SAF URI 반환
     */
    fun getSafUriForAudio(): String = userSelectedAudioDir.ifBlank { userSelectedBaseDir }
    fun getSafUriForStt(): String = userSelectedSttDir.ifBlank { userSelectedBaseDir }
    fun getSafUriForSummary(): String = userSelectedSummaryDir.ifBlank { userSelectedBaseDir }

    /**
     * ★ SAF 폴더에 텍스트 파일을 직접 저장 (2단계 복사 없이 1단계로 바로 저장)
     * @param text 저장할 텍스트 내용
     * @param safUriString SAF tree URI
     * @param fileName 파일명 (확장자 포함, 예: "회의록.md")
     * @return 성공 시 SAF URI 문자열, 실패 시 null
     */
    fun writeTextToSafDir(text: String, safUriString: String, fileName: String): String? {
        if (safUriString.isBlank()) return null
        return try {
            val treeUri = Uri.parse(safUriString)
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

            // 동일 이름 파일 삭제
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            treeDoc?.findFile(fileName)?.delete()
            treeDoc?.findFile(fileName.substringBeforeLast("."))?.delete()

            val newDocUri = DocumentsContract.createDocument(
                context.contentResolver, parentUri, "application/octet-stream", fileName
            )
            if (newDocUri == null) {
                Log.e(TAG, "SAF writeText createDocument 실패: $fileName")
                return null
            }

            context.contentResolver.openOutputStream(newDocUri)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            } ?: run {
                Log.e(TAG, "SAF writeText openOutputStream 실패: $newDocUri")
                return null
            }

            Log.d(TAG, "✅ SAF 직접 저장 성공: $fileName → $newDocUri")
            newDocUri.toString()
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SAF 권한 만료 (writeText): $fileName → $safUriString", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ SAF 직접 저장 실패 (writeText): $fileName → $safUriString", e)
            null
        }
    }

    /**
     * ★ SAF 폴더에 오디오 등 바이너리 파일을 직접 저장 (2단계 복사 없이 1단계로 바로 저장)
     * @param srcFile 저장할 원본 파일
     * @param safUriString SAF tree URI
     * @return 성공 시 SAF URI 문자열, 실패 시 null
     */
    fun writeFileToSafDir(srcFile: File, safUriString: String): String? {
        if (safUriString.isBlank() || !srcFile.exists()) return null
        return try {
            val treeUri = Uri.parse(safUriString)
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)

            // 동일 이름 파일 삭제
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            treeDoc?.findFile(srcFile.name)?.delete()
            treeDoc?.findFile(srcFile.nameWithoutExtension)?.delete()

            val newDocUri = DocumentsContract.createDocument(
                context.contentResolver, parentUri, "application/octet-stream", srcFile.name
            )
            if (newDocUri == null) {
                Log.e(TAG, "SAF writeFile createDocument 실패: ${srcFile.name}")
                return null
            }

            context.contentResolver.openOutputStream(newDocUri)?.use { out ->
                srcFile.inputStream().use { input -> input.copyTo(out) }
            } ?: run {
                Log.e(TAG, "SAF writeFile openOutputStream 실패: $newDocUri")
                return null
            }

            Log.d(TAG, "✅ SAF 직접 파일 저장 성공: ${srcFile.name} → $newDocUri")
            newDocUri.toString()
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SAF 권한 만료 (writeFile): ${srcFile.name} → $safUriString", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ SAF 직접 파일 저장 실패 (writeFile): ${srcFile.name} → $safUriString", e)
            null
        }
    }

    /**
     * SAF 트리 내 서브폴더에 텍스트 파일 저장.
     * 폴더가 없으면 자동 생성. 음성메모 → 00_Inbox/voice_memos/ 저장에 사용.
     *
     * @param text 저장할 텍스트 내용
     * @param safUriString Obsidian vault SAF tree URI
     * @param subPath 서브폴더 경로 (예: "00_Inbox/voice_memos")
     * @param fileName 파일명 (예: "음성메모_20260523_150000.md")
     * @return 성공 시 SAF URI 문자열, 실패 시 null
     */
    fun writeTextToSafSubDir(
        text: String,
        safUriString: String,
        subPath: String,
        fileName: String
    ): String? {
        if (safUriString.isBlank()) return null
        return try {
            val treeUri = Uri.parse(safUriString)
            var currentDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null

            // ★ v3.7.17: 선택한 SAF 루트 폴더명이 subPath 의 선두 세그먼트와 겹치면 그 세그먼트를 건너뜀.
            //   사용자가 vault 루트가 아니라 '08_회의록' 폴더 자체를 저장 위치로 고른 경우
            //   08_회의록/08_회의록 이중 폴더가 생기던 문제를 방지(루트면 그대로 동작).
            val segments = subPath.split("/").filter { it.isNotBlank() }.toMutableList()
            while (segments.isNotEmpty() && currentDoc.name == segments.first()) {
                segments.removeAt(0)
            }

            // 서브폴더 순서대로 탐색 / 없으면 생성
            for (segment in segments) {
                currentDoc = currentDoc.findFile(segment)
                    ?: currentDoc.createDirectory(segment)
                    ?: run {
                        Log.e(TAG, "SAF subDir 생성 실패: $segment")
                        return null
                    }
            }

            // 동일 이름 파일 삭제 (덮어쓰기)
            currentDoc.findFile(fileName)?.delete()
            currentDoc.findFile(fileName.substringBeforeLast("."))?.delete()

            val newDoc = currentDoc.createFile("application/octet-stream", fileName) ?: run {
                Log.e(TAG, "SAF subDir createFile 실패: $fileName")
                return null
            }

            context.contentResolver.openOutputStream(newDoc.uri)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            } ?: run {
                Log.e(TAG, "SAF subDir openOutputStream 실패: ${newDoc.uri}")
                return null
            }

            Log.d(TAG, "✅ SAF 서브폴더 저장 성공: $subPath/$fileName → ${newDoc.uri}")
            newDoc.uri.toString()
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SAF 권한 만료 (writeTextSubDir): $fileName → $safUriString", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ SAF 서브폴더 저장 실패: $fileName → $safUriString", e)
            null
        }
    }

    /**
     * SAF 트리 내 서브폴더의 텍스트 파일을 읽는다. 파일이 없으면 null.
     * 데일리 노트(01_Daily/daily_YYYYMMDD.md) 존재 확인·읽기에 사용.
     *
     * @param safUriString Obsidian vault SAF tree URI
     * @param subPath 서브폴더 경로 (예: "01_Daily"). 빈 문자열이면 루트.
     * @param fileName 파일명 (예: "daily_20260616.md")
     * @return 파일 내용, 없거나 실패 시 null
     */
    fun readTextFromSafSubDir(
        safUriString: String,
        subPath: String,
        fileName: String
    ): String? {
        if (safUriString.isBlank()) return null
        return try {
            val treeUri = Uri.parse(safUriString)
            var currentDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            for (segment in subPath.split("/").filter { it.isNotBlank() }) {
                currentDoc = currentDoc.findFile(segment) ?: return null
            }
            val target = currentDoc.findFile(fileName) ?: return null
            if (!target.exists() || !target.isFile) return null
            context.contentResolver.openInputStream(target.uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SAF 권한 만료 (readText): $fileName → $safUriString", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ SAF 읽기 실패: $fileName → $safUriString", e)
            null
        }
    }

    /**
     * SAF URI에서 표시용 경로 추출 (UI 표시용)
     */
    fun safUriToDisplayPath(uriString: String): String {
        if (uriString.isBlank()) return ""
        return try {
            val uri = Uri.parse(uriString)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            if (split.size >= 2) {
                val storageId = if (split[0] == "primary") "내부저장소" else split[0]
                "$storageId/${split[1]}"
            } else {
                docId
            }
        } catch (e: Exception) {
            uriString.takeLast(50)
        }
    }

    // === Google Drive ===
    // 기본 폴더 ID — 사용자 Drive에 이미 존재하는 폴더
    companion object {
        private const val TAG = "ConfigManager"
        // 녹음파일 폴더: https://drive.google.com/drive/folders/1Yu6snQUtwl62j98b64Foi5iZ7mqn2GpS
        private const val DEFAULT_DRIVE_MP3_FOLDER_ID = "1Yu6snQUtwl62j98b64Foi5iZ7mqn2GpS"
        // 회의록 폴더: https://drive.google.com/drive/folders/1R8WbbJrhm3PLG0wZ0NPim_Kc6KRt9GHX
        private const val DEFAULT_DRIVE_TXT_FOLDER_ID = "1R8WbbJrhm3PLG0wZ0NPim_Kc6KRt9GHX"
    }

    var driveMp3FolderId: String
        get() = prefs.getString("drive_mp3_folder_id", DEFAULT_DRIVE_MP3_FOLDER_ID) ?: DEFAULT_DRIVE_MP3_FOLDER_ID
        set(v) = prefs.edit().putString("drive_mp3_folder_id", v).apply()

    var driveTxtFolderId: String
        get() = prefs.getString("drive_txt_folder_id", DEFAULT_DRIVE_TXT_FOLDER_ID) ?: DEFAULT_DRIVE_TXT_FOLDER_ID
        set(v) = prefs.edit().putString("drive_txt_folder_id", v).apply()

    // ★ v3.9.0: 음성메모 요약본(.md) 전용 Drive 폴더 — PC 자동화(voice_memo_pull)가 폴링해
    // 모바일 Obsidian 을 켜지 않아도 PC vault 로 릴레이한다. 폴더 id 는 최초 ensureFolder 후 캐시.
    var driveVoiceMemoFolderId: String
        get() = prefs.getString("drive_voicememo_folder_id", "") ?: ""
        set(v) = prefs.edit().putString("drive_voicememo_folder_id", v).apply()

    var driveAutoUpload: Boolean
        get() = prefs.getBoolean("drive_auto_upload", true)
        set(v) = prefs.edit().putBoolean("drive_auto_upload", v).apply()

    // === Speaker Count ===
    var numSpeakers: Int
        get() = prefs.getInt("num_speakers", 2)
        set(v) = prefs.edit().putInt("num_speakers", v).apply()

    // ★ v3.1.1: 화자 수 자동/수동 모드 (기본: 자동)
    var autoSpeakerDetection: Boolean
        get() = prefs.getBoolean("auto_speaker_detection", true)
        set(v) = prefs.edit().putBoolean("auto_speaker_detection", v).apply()

    /**
     * STT 호출 시 사용할 화자 수 반환
     * - 자동 모드: 0 (AI가 자동 판단)
     * - 수동 모드: 사용자가 설정한 numSpeakers 값
     */
    fun getEffectiveNumSpeakers(): Int {
        return if (autoSpeakerDetection) 0 else numSpeakers
    }

    fun isConfigComplete(): Boolean {
        val hasGemini = geminiApiKey.isNotBlank()
        val hasClova = clovaInvokeUrl.isNotBlank() && clovaSecretKey.isNotBlank()
        return hasGemini || hasClova
    }

    fun ensureDirs() {
        try {
            val audioDir = audioSaveDir
            val sttDir = sttSaveDir
            val summaryDir = summarySaveDir
            Log.d(TAG, "ensureDirs — audio: ${audioDir.absolutePath} (exists=${audioDir.exists()}, writable=${audioDir.canWrite()})")
            Log.d(TAG, "ensureDirs — stt: ${sttDir.absolutePath} (exists=${sttDir.exists()}, writable=${sttDir.canWrite()})")
            Log.d(TAG, "ensureDirs — summary: ${summaryDir.absolutePath} (exists=${summaryDir.exists()}, writable=${summaryDir.canWrite()})")
        } catch (e: Exception) {
            Log.e(TAG, "ensureDirs failed", e)
        }
    }
}
