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
import java.io.File

data class MeetingListUiState(
    val showActionMenu: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showMoveDialog: Boolean = false,
    val targetMeeting: Meeting? = null,
    val renameText: String = "",
    val statusMessage: String = "",
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
                val sttName = newName + "_회의록.txt"
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

    // === Share (공유) ===
    fun shareMeeting() {
        val meeting = _uiState.value.targetMeeting ?: return
        _uiState.value = _uiState.value.copy(showActionMenu = false)

        val context = getApplication<Application>()
        val filesToShare = mutableListOf<File>()

        // 회의록 파일 우선 공유
        if (meeting.summaryLocalPath.isNotBlank()) {
            val f = File(meeting.summaryLocalPath)
            if (f.exists()) filesToShare.add(f)
        }
        if (meeting.mp3LocalPath.isNotBlank()) {
            val f = File(meeting.mp3LocalPath)
            if (f.exists()) filesToShare.add(f)
        }

        if (filesToShare.isEmpty()) {
            _uiState.value = _uiState.value.copy(statusMessage = "공유할 파일이 없습니다.")
            return
        }

        try {
            val uris = filesToShare.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = if (filesToShare[0].extension == "txt") "text/plain" else "audio/*"
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val chooser = Intent.createChooser(intent, "회의 파일 공유").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Share failed", e)
            _uiState.value = _uiState.value.copy(statusMessage = "공유 실패: ${e.message?.take(100)}")
        }
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
}
