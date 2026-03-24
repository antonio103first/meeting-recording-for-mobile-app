package com.krunventures.meetingrecorder.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
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

    var audioSubdir: String
        get() = prefs.getString("audio_subdir", "녹음파일") ?: "녹음파일"
        set(v) = prefs.edit().putString("audio_subdir", v).apply()

    var summarySubdir: String
        get() = prefs.getString("summary_subdir", "회의록(요약)") ?: "회의록(요약)"
        set(v) = prefs.edit().putString("summary_subdir", v).apply()

    val audioSaveDir: File
        get() {
            val baseDir = recordingDir
            // If baseDir is a SAF URI, use DocumentFile API
            if (baseDir.startsWith("content://")) {
                val uri = Uri.parse(baseDir)
                val docFile = DocumentFile.fromTreeUri(context, uri)
                if (docFile != null) {
                    var subDir = docFile.findFile(audioSubdir)
                    if (subDir == null) {
                        subDir = docFile.createDirectory(audioSubdir)
                    }
                    if (subDir != null) {
                        Log.d(TAG, "audioSaveDir (SAF): ${subDir.uri}")
                        return File(subDir.uri.toString())
                    }
                }
            }
            // Fall back to traditional File API
            return File(baseDir, audioSubdir).also {
                if (!it.exists()) {
                    val created = it.mkdirs()
                    Log.d(TAG, "audioSaveDir mkdirs: $created, path: ${it.absolutePath}")
                }
            }
        }

    val summarySaveDir: File
        get() {
            val baseDir = recordingDir
            // If baseDir is a SAF URI, use DocumentFile API
            if (baseDir.startsWith("content://")) {
                val uri = Uri.parse(baseDir)
                val docFile = DocumentFile.fromTreeUri(context, uri)
                if (docFile != null) {
                    var subDir = docFile.findFile(summarySubdir)
                    if (subDir == null) {
                        subDir = docFile.createDirectory(summarySubdir)
                    }
                    if (subDir != null) {
                        Log.d(TAG, "summarySaveDir (SAF): ${subDir.uri}")
                        return File(subDir.uri.toString())
                    }
                }
            }
            // Fall back to traditional File API
            return File(baseDir, summarySubdir).also {
                if (!it.exists()) {
                    val created = it.mkdirs()
                    Log.d(TAG, "summarySaveDir mkdirs: $created, path: ${it.absolutePath}")
                }
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

    var driveAutoUpload: Boolean
        get() = prefs.getBoolean("drive_auto_upload", true)
        set(v) = prefs.edit().putBoolean("drive_auto_upload", v).apply()

    // === Speaker Count ===
    var numSpeakers: Int
        get() = prefs.getInt("num_speakers", 2)
        set(v) = prefs.edit().putInt("num_speakers", v).apply()

    fun isConfigComplete(): Boolean {
        val hasGemini = geminiApiKey.isNotBlank()
        val hasClova = clovaInvokeUrl.isNotBlank() && clovaSecretKey.isNotBlank()
        return hasGemini || hasClova
    }

    fun ensureDirs() {
        try {
            val audioDir = audioSaveDir
            val summaryDir = summarySaveDir
            Log.d(TAG, "ensureDirs — audio: ${audioDir.absolutePath} (exists=${audioDir.exists()}, writable=${audioDir.canWrite()})")
            Log.d(TAG, "ensureDirs — summary: ${summaryDir.absolutePath} (exists=${summaryDir.exists()}, writable=${summaryDir.canWrite()})")
        } catch (e: Exception) {
            Log.e(TAG, "ensureDirs failed", e)
        }
    }
}
