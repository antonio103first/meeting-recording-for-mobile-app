package com.krunventures.meetingrecorder.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File

class ConfigManager(context: Context) {
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

    // === Storage Paths (Samsung Galaxy S25 optimized) ===
    private val defaultBaseDir: String
        get() {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            return File(docs, "Meeting recording").absolutePath
        }

    var recordingDir: String
        get() = prefs.getString("recording_dir", defaultBaseDir) ?: defaultBaseDir
        set(v) = prefs.edit().putString("recording_dir", v).apply()

    var audioSubdir: String
        get() = prefs.getString("audio_subdir", "녹음파일") ?: "녹음파일"
        set(v) = prefs.edit().putString("audio_subdir", v).apply()

    var summarySubdir: String
        get() = prefs.getString("summary_subdir", "회의록(요약)") ?: "회의록(요약)"
        set(v) = prefs.edit().putString("summary_subdir", v).apply()

    val audioSaveDir: File
        get() = File(recordingDir, audioSubdir).also { it.mkdirs() }

    val summarySaveDir: File
        get() = File(audioSaveDir, summarySubdir).also { it.mkdirs() }

    // === Google Drive ===
    var driveMp3FolderId: String
        get() = prefs.getString("drive_mp3_folder_id", "") ?: ""
        set(v) = prefs.edit().putString("drive_mp3_folder_id", v).apply()

    var driveTxtFolderId: String
        get() = prefs.getString("drive_txt_folder_id", "") ?: ""
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
        audioSaveDir.mkdirs()
        summarySaveDir.mkdirs()
    }
}
