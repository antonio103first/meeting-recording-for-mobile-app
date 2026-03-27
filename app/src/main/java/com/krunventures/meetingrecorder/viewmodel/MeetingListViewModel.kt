package com.krunventures.meetingrecorder.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.krunventures.meetingrecorder.MeetingApp
import com.krunventures.meetingrecorder.data.Meeting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krunventures.meetingrecorder.util.MdToPdfConverter
import org.json.JSONObject
import java.io.File

data class MeetingListUiState(
    val showActionMenu: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showMoveDialog: Boolean = false,
    val showSpeakerDialog: Boolean = false,
    val showShareSheet: Boolean = false,
    val targetMeeting: Meeting? = null,
    val renameText: String = "",
    val statusMessage: String = "",
    val currentSpeakers: List<Pair<String, String>> = emptyList(),
)

class MeetingListViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        private const val TAG = "MeetingListVM"
    }

    private val dao = (app as MeetingApp).database.meetingDao()
    val meetings = dao.getAll()

    private val _uiState = MutableStateFlow(MeetingListUiState())
    val uiState: StateFlow<MeetingListUiState> = _uiState

    // === Action Menu ===
    fun showActionMenu(meeting: Meeting) {
        _uiState.value = _uiState.value.copy(
            showActionMenu = true,
            targetMeeting = meeting
        )
    }

    fun dismissActionMenu() {
        _uiState.value = _uiState.value.copy(showActionMenu = false)
    }

    // === Delete ===
    fun showDeleteDialog() {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = true,
            showActionMenu = false
        )
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = false)
    }

    /**
     * DB 레코드 삭제 + 로컬 파일도 함께 삭제
     */
    fun deleteMeeting(deleteFiles: Boolean = true) {
        val meeting = _uiState.value.targetMeeting ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (deleteFiles) {
                    // 녹음 파일 삭제
                    deleteFileIfExists(meeting.mp3LocalPath)
                    // 회의록 파일 삭제
                    deleteFileIfExists(meeting.sttLocalPath)
                    if (meeting.summaryLocalPath != meeting.sttLocalPath) {
                        deleteFileIfExists(meeting.summaryLocalPath)
                    }
                    Log.d(TAG, "Local files deleted for meeting: ${meeting.id}")
                }
                dao.delete(meeting.id)
                Log.d(TAG, "Meeting ${meeting.id} deleted from DB")
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        showDeleteDialog = false,
                        targetMeeting = null,
                        statusMessage = "삭제 완료"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        showDeleteDialog = false,
                        statusMessage = "삭제 실패: ${e.message?.take(100)}"
                    )
                }
            }
        }
    }

    private fun deleteFileIfExists(path: String) {
        if (path.isNotBlank()) {
            val file = File(path)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Delete file: $path → $deleted")
            }
        }
    }

    // === Rename ===
    fun showRenameDialog() {
        val meeting = _uiState.value.targetMeeting ?: return
        _uiState.value = _uiState.value.copy(
            showRenameDialog = true,
            showActionMenu = false,
            renameText = meeting.fileName.substringBeforeLast(".")
        )
    }

    fun dismissRenameDialog() {
        _uiState.value = _uiState.value.copy(showRenameDialog = false)
    }

    fun updateRenameText(text: String) {
        _uiState.value = _uiState.value.copy(renameText = text)
    }

    /**
     * 파일 이름 변경 — DB + 실제 로컬 파일
     */
    fun confirmRename() {
        val meeting = _uiState.value.targetMeeting ?: return
        val newName = _uiState.value.renameText.trim()
        if (newName.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ext = meeting.fileName.substringAfterLast(".", "")
                val fullNewName = if (ext.isNotEmpty()) "$newName.$ext" else newName

                // 녹음 파일 이름 변경
                val newMp3Path = renameFileIfExists(meeting.mp3LocalPath, fullNewName)
                // 회의록 파일 이름 변경
                val sttName = newName + "_회의록.md"
                val newSttPath = renameFileIfExists(meeting.sttLocalPath, sttName)
                val newSummaryPath = if (meeting.summaryLocalPath == meeting.sttLocalPath) {
                    newSttPath
                } else {
                    renameFileIfExists(meeting.summaryLocalPath, sttName)
                }

                // DB 업데이트
                dao.updateFileName(meeting.id, fullNewName)
                dao.updateFilePaths(meeting.id, newMp3Path, newSttPath, newSummaryPath)

                Log.d(TAG, "Renamed meeting ${meeting.id}: $fullNewName")
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        showRenameDialog = false,
                        targetMeeting = null,
                        statusMessage = "이름 변경 완료: $fullNewName"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Rename failed", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        showRenameDialog = false,
                        statusMessage = "이름 변경 실패: ${e.message?.take(100)}"
                    )
                }
            }
        }
    }

    private fun renameFileIfExists(oldPath: String, newFileName: String): String {
        if (oldPath.isBlank()) return oldPath
        val oldFile = File(oldPath)
        if (!oldFile.exists()) return oldPath
        val newFile = File(oldFile.parentFile, newFileName)
        return if (oldFile.renameTo(newFile)) {
            Log.d(TAG, "Renamed: ${oldFile.name} → $newFileName")
            newFile.absolutePath
        } else {
            Log.w(TAG, "Rename failed: ${oldFile.name}")
            oldPath
        }
    }

    // === File Location ===
    /**
     * Copy file location(s) to clipboard for the current meeting
     */
    fun copyFileLocationToClipboard(meeting: Meeting) {
        val context = getApplication<Application>()
        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

        // Build location info with all file paths
        val locations = buildString {
            if (meeting.mp3LocalPath.isNotBlank()) {
                appendLine("🎵 녹음 파일:")
                appendLine(meeting.mp3LocalPath)
                appendLine()
            }
            if (meeting.sttLocalPath.isNotBlank()) {
                appendLine("📝 회의록 파일:")
                appendLine(meeting.sttLocalPath)
                appendLine()
            }
        }.trim()

        if (locations.isNotBlank()) {
            val clip = android.content.ClipData.newPlainText("파일 위치", locations)
            clipboardManager.setPrimaryClip(clip)
            Log.d(TAG, "File locations copied to clipboard:\n$locations")
            _uiState.value = _uiState.value.copy(
                showActionMenu = false,
                statusMessage = "파일 경로가 클립보드에 복사되었습니다"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                showActionMenu = false,
                statusMessage = "복사할 파일 경로가 없습니다"
            )
        }
    }

    // === Copy to Clipboard (복사) ===

    /**
     * 회의록 요약 + STT 원문을 클립보드에 복사
     */
    fun copyMeetingTextToClipboard() {
        val meeting = _uiState.value.targetMeeting ?: return
        val context = getApplication<Application>()
        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

        val text = buildString {
            appendLine("=== ${meeting.fileName} ===")
            appendLine("작성일: ${meeting.createdAt}")
            appendLine()
            if (meeting.summaryText.isNotBlank()) {
                appendLine("## 회의록 요약")
                appendLine(meeting.summaryText)
                appendLine()
            }
            if (meeting.sttText.isNotBlank()) {
                appendLine("---")
                appendLine("## STT 변환 원문")
                appendLine(meeting.sttText)
            }
        }.trim()

        if (text.isNotBlank()) {
            val clip = android.content.ClipData.newPlainText("회의록", text)
            clipboardManager.setPrimaryClip(clip)
            _uiState.value = _uiState.value.copy(
                showActionMenu = false,
                statusMessage = "회의록이 클립보드에 복사되었습니다"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                showActionMenu = false,
                statusMessage = "복사할 내용이 없습니다"
            )
        }
    }

    // === Share (공유) ===

    /**
     * 파일 형태별 공유
     * @param format "plain_text" | "md_file" | "txt_file" | "with_audio"
     */
    fun shareByFormat(format: String) {
        val meeting = _uiState.value.targetMeeting ?: return
        _uiState.value = _uiState.value.copy(showShareSheet = false)
        val context = getApplication<Application>()

        try {
            when (format) {
                "plain_text" -> {
                    // Plain text — 텍스트 내용을 직접 공유 (파일 첨부 없음)
                    val text = buildString {
                        appendLine("=== ${meeting.fileName} ===")
                        appendLine()
                        appendLine("## 회의록 요약")
                        appendLine(meeting.summaryText.ifEmpty { "(없음)" })
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, meeting.fileName)
                        putExtra(Intent.EXTRA_TEXT, text)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, "텍스트 공유").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }

                "md_file" -> {
                    // 회의록 PDF 공유 — MD→HTML→PDF 변환 (카카오톡 호환, 프린트 형태)
                    val tempDir = File(context.cacheDir, "share_temp")
                    tempDir.mkdirs()
                    val baseName = meeting.fileName.ifEmpty { "회의록" }
                        .removeSuffix(".m4a").removeSuffix(".mp3").removeSuffix(".md").removeSuffix(".txt")

                    // 원본 파일이 있으면 그 내용 사용, 없으면 DB summaryText 사용
                    val mdContent = if (meeting.summaryLocalPath.isNotBlank()) {
                        val srcFile = File(meeting.summaryLocalPath)
                        if (srcFile.exists()) srcFile.readText(Charsets.UTF_8)
                        else meeting.summaryText.ifEmpty { "(요약 없음)" }
                    } else {
                        meeting.summaryText.ifEmpty { "(요약 없음)" }
                    }

                    val pdfFile = File(tempDir, "${baseName}_회의록.pdf")
                    val success = MdToPdfConverter.convert(context, mdContent, pdfFile, baseName)
                    if (success) {
                        shareFileIntent(context, pdfFile, "application/pdf", "${baseName}_회의록.pdf")
                    } else {
                        // PDF 변환 실패 시 .txt 폴백
                        Log.w(TAG, "PDF 변환 실패, txt 폴백")
                        val txtFile = File(tempDir, "${baseName}_회의록.txt").apply {
                            writeText(mdContent, Charsets.UTF_8)
                        }
                        shareFileIntent(context, txtFile, "text/plain", meeting.fileName)
                    }
                }

                "txt_file" -> {
                    // .txt 파일 공유 — 회의록 요약을 텍스트 파일로 첨부
                    val tempDir = File(context.cacheDir, "share_temp")
                    tempDir.mkdirs()
                    val baseName = meeting.fileName.ifEmpty { "회의록" }
                    val txtFile = File(tempDir, "${baseName}.txt").apply {
                        writeText(buildString {
                            appendLine("[ ${meeting.fileName} ]")
                            appendLine("작성일: ${meeting.createdAt}")
                            appendLine()
                            appendLine(meeting.summaryText.ifEmpty { "(요약 없음)" })
                        }, Charsets.UTF_8)
                    }
                    shareFileIntent(context, txtFile, "text/plain", meeting.fileName)
                }

                "with_audio" -> {
                    // 녹음포함 — 회의록 .md + 녹음 .m4a/.mp3 파일 함께 공유
                    val filesToShare = mutableListOf<File>()

                    // 회의록 파일
                    if (meeting.summaryLocalPath.isNotBlank()) {
                        val f = File(meeting.summaryLocalPath)
                        if (f.exists()) filesToShare.add(f)
                    } else if (meeting.summaryText.isNotBlank()) {
                        val tempDir = File(context.cacheDir, "share_temp")
                        tempDir.mkdirs()
                        val baseName = meeting.fileName.ifEmpty { "회의록" }
                        filesToShare.add(File(tempDir, "${baseName}.md").apply {
                            writeText(meeting.summaryText, Charsets.UTF_8)
                        })
                    }

                    // 녹음 파일
                    if (meeting.mp3LocalPath.isNotBlank()) {
                        val f = File(meeting.mp3LocalPath)
                        if (f.exists()) filesToShare.add(f)
                    }

                    if (filesToShare.isEmpty()) {
                        _uiState.value = _uiState.value.copy(statusMessage = "공유할 파일이 없습니다.")
                        return
                    }

                    val uris = filesToShare.map { file ->
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    }

                    val intent = if (uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "*/*"
                            putExtra(Intent.EXTRA_STREAM, uris[0])
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(Intent.createChooser(intent, "회의 파일 공유").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Share failed (format=$format)", e)
            _uiState.value = _uiState.value.copy(statusMessage = "공유 실패: ${e.message?.take(100)}")
        }
    }

    private fun shareFileIntent(context: Application, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "파일 공유").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // === Delete only files (keep DB record) ===
    fun deleteFilesOnly() {
        val meeting = _uiState.value.targetMeeting ?: return
        viewModelScope.launch(Dispatchers.IO) {
            deleteFileIfExists(meeting.mp3LocalPath)
            deleteFileIfExists(meeting.sttLocalPath)
            if (meeting.summaryLocalPath != meeting.sttLocalPath) {
                deleteFileIfExists(meeting.summaryLocalPath)
            }
            dao.updateFilePaths(meeting.id, "", "", "")
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    showActionMenu = false,
                    targetMeeting = null,
                    statusMessage = "로컬 파일 삭제 완료 (DB 기록은 유지)"
                )
            }
        }
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(statusMessage = "")
    }

    // === Speaker Name Change ===
    fun extractSpeakers(sttText: String): List<String> {
        // Regex to find [화자1], [화자2], [Speaker 1] etc. patterns
        val pattern = Regex("\\[(화자\\d+|Speaker\\s\\d+)\\]")
        return pattern.findAll(sttText)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    fun replaceSpeakerNames(text: String, speakerMap: Map<String, String>): String {
        var result = text
        speakerMap.forEach { (label, name) ->
            result = result.replace("[$label]", "[$name]")
        }
        return result
    }

    fun showSpeakerDialog() {
        val meeting = _uiState.value.targetMeeting ?: return
        val speakers = extractSpeakers(meeting.sttText)
        val pairs = speakers.map { it to "" }
        _uiState.value = _uiState.value.copy(
            showSpeakerDialog = true,
            showActionMenu = false,
            currentSpeakers = pairs
        )
    }

    fun dismissSpeakerDialog() {
        _uiState.value = _uiState.value.copy(showSpeakerDialog = false)
    }

    fun updateSpeakerName(index: Int, newName: String) {
        val current = _uiState.value.currentSpeakers.toMutableList()
        if (index < current.size) {
            current[index] = current[index].copy(second = newName)
            _uiState.value = _uiState.value.copy(currentSpeakers = current)
        }
    }

    fun saveSpeakerMap(meetingId: Int) {
        val meeting = _uiState.value.targetMeeting ?: return
        val speakerMap = _uiState.value.currentSpeakers
            .filter { it.second.isNotBlank() }
            .associate { it.first to it.second }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Serialize to JSON
                val jsonObj = JSONObject()
                speakerMap.forEach { (label, name) ->
                    jsonObj.put(label, name)
                }
                val speakerMapJson = jsonObj.toString()

                // Replace speaker names in texts
                val newSttText = replaceSpeakerNames(meeting.sttText, speakerMap)
                val newSummaryText = replaceSpeakerNames(meeting.summaryText, speakerMap)

                // Update DB
                dao.updateSpeakerMap(meetingId, speakerMapJson)
                dao.updateSummary(meetingId, newSttText, newSummaryText, meeting.summaryLocalPath)

                Log.d(TAG, "Speaker map saved for meeting $meetingId")

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        showSpeakerDialog = false,
                        targetMeeting = meeting.copy(
                            sttText = newSttText,
                            summaryText = newSummaryText,
                            speakerMap = speakerMapJson
                        ),
                        statusMessage = "화자 이름이 변경되었습니다"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save speaker map", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        showSpeakerDialog = false,
                        statusMessage = "화자 이름 변경 실패: ${e.message?.take(100)}"
                    )
                }
            }
        }
    }

    // === Share Sheet ===
    fun showShareSheet() {
        _uiState.value = _uiState.value.copy(
            showShareSheet = true,
            showActionMenu = false
        )
    }

    fun dismissShareSheet() {
        _uiState.value = _uiState.value.copy(showShareSheet = false)
    }

}
