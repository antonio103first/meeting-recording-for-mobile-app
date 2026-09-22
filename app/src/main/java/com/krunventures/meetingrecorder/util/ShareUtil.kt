package com.krunventures.meetingrecorder.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * v3.12.0 — 녹음파일(MP3/M4A)을 카카오톡·슬랙·메일 등 외부 앱으로 보내기.
 *
 * 회의목록(MP3 탭)·녹음 화면(파일 선택 미리듣기) 양쪽에서 같은 로직을 쓰므로
 * ViewModel/Composable 어디서든 호출 가능한 순수 유틸로 분리했다.
 */
object ShareUtil {
    private const val TAG = "ShareUtil"

    fun audioMime(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "mp4", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "amr" -> "audio/amr"
            else -> "audio/*"
        }

    /**
     * 녹음파일 1개를 시스템 공유 시트(카톡/슬랙/메일…)로 보낸다.
     *
     * @param path 절대경로 또는 `content://` 문자열(SAF로 가져온 파일은 DB에 URI 문자열로 저장됨)
     * @return 실패 사유. 성공이면 null
     */
    fun shareAudio(context: Context, path: String, title: String? = null): String? {
        if (path.isBlank()) return "보낼 녹음파일이 없습니다."

        val displayName: String
        val uri: Uri = try {
            if (path.startsWith("content://")) {
                displayName = title ?: path.substringAfterLast('/')
                Uri.parse(path)
            } else {
                val file = File(path)
                if (!file.exists()) return "녹음파일을 찾을 수 없습니다: ${file.name}"
                displayName = file.name
                toShareableUri(context, file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "공유 URI 준비 실패: $path", e)
            return "공유 준비 실패: ${e.message?.take(80)}"
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = audioMime(displayName)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title ?: displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "녹음파일 보내기")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(chooser)
            null
        } catch (e: Exception) {
            Log.e(TAG, "공유 시트 실행 실패", e)
            "공유 앱을 열 수 없습니다: ${e.message?.take(80)}"
        }
    }

    /**
     * FileProvider 가 커버하는 경로(앱 전용 외부저장소·내부저장소·캐시)면 그대로 URI 발급.
     * 그 밖의 경로(사용자가 SAF 로 지정한 임의 폴더를 절대경로로 접근하는 경우 등)는
     * IllegalArgumentException 이 나므로 캐시(share_temp)에 복사한 뒤 발급한다.
     */
    private fun toShareableUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "FileProvider 범위 밖 → 캐시 복사 후 공유: ${file.absolutePath}")
            val tempDir = File(context.cacheDir, "share_temp").apply { mkdirs() }
            val copy = File(tempDir, file.name)
            file.copyTo(copy, overwrite = true)
            FileProvider.getUriForFile(context, authority, copy)
        }
    }
}
