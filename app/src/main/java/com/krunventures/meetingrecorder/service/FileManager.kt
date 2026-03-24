package com.krunventures.meetingrecorder.service

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileManager {
    fun getDefaultBaseName(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    fun saveSttText(text: String, saveDir: File, fileName: String): Result<File> {
        return try {
            saveDir.mkdirs()
            val file = File(saveDir, "${fileName}.txt")
            file.writeText(text, Charsets.UTF_8)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(Exception("STT 파일 저장 실패: ${e.message}"))
        }
    }

    fun saveSummaryText(text: String, saveDir: File, fileName: String): Result<File> {
        return try {
            saveDir.mkdirs()
            val file = File(saveDir, "${fileName}.md")
            file.writeText(text, Charsets.UTF_8)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(Exception("요약 파일 저장 실패: ${e.message}"))
        }
    }

    /**
     * 회의록요약 + STT변환을 하나의 통합 파일로 저장
     * 배치 순서: 1) 회의록 요약  2) STT 변환 원문
     */
    fun saveCombinedSummary(summaryText: String, sttText: String, saveDir: File, fileName: String): Result<File> {
        return try {
            saveDir.mkdirs()
            val combined = buildString {
                appendLine("# 회의록 요약")
                appendLine()
                appendLine(summaryText.trim())
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("# STT 변환 원문")
                appendLine()
                appendLine(sttText.trim())
            }
            val file = File(saveDir, "${fileName}.md")
            file.writeText(combined, Charsets.UTF_8)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(Exception("통합 요약 파일 저장 실패: ${e.message}"))
        }
    }

    /**
     * 녹음 정지 즉시 호출 — 녹음파일을 저장 디렉토리에 즉시 복사
     * 임시 이름(타임스탬프)으로 저장하고, 나중에 사용자가 파일이름을 확정하면 rename
     *
     * @param srcFile 녹음된 원본 파일 (앱 캐시/임시 디렉토리)
     * @param saveDir 최종 저장 디렉토리 (audioSaveDir)
     * @return 저장된 파일 (임시 이름)
     */
    fun saveRecordingImmediately(srcFile: File, saveDir: File): Result<File> {
        return try {
            saveDir.mkdirs()
            val tempName = "REC_${getDefaultBaseName()}.${srcFile.extension.ifEmpty { "mp3" }}"
            val dest = File(saveDir, tempName)
            srcFile.copyTo(dest, overwrite = true)
            Result.success(dest)
        } catch (e: Exception) {
            Result.failure(Exception("녹음파일 즉시 저장 실패: ${e.message}"))
        }
    }

    /**
     * 사용자가 파일이름을 확정한 후 녹음파일 이름 변경
     * saveRecordingImmediately()에서 저장한 임시 파일 → 최종 이름으로 변경
     *
     * @param savedFile 즉시 저장된 파일 (임시 이름)
     * @param newBaseName 사용자가 입력한 파일이름 (확장자 제외)
     * @return 이름이 변경된 파일
     */
    fun renameRecordingFile(savedFile: File, newBaseName: String): Result<File> {
        return try {
            val ext = savedFile.extension.ifEmpty { "mp3" }
            val renamedFile = File(savedFile.parentFile, "${newBaseName}.$ext")
            if (savedFile.renameTo(renamedFile)) {
                Result.success(renamedFile)
            } else {
                // renameTo 실패 시 복사 + 삭제 폴백
                savedFile.copyTo(renamedFile, overwrite = true)
                savedFile.delete()
                Result.success(renamedFile)
            }
        } catch (e: Exception) {
            Result.failure(Exception("녹음파일 이름변경 실패: ${e.message}"))
        }
    }

    fun copyAudioToSaveDir(srcFile: File, saveDir: File, fileName: String): Result<File> {
        return try {
            saveDir.mkdirs()
            val dest = File(saveDir, "${fileName}${srcFile.extension.let { if (it.isNotEmpty()) ".$it" else "" }}")
            srcFile.copyTo(dest, overwrite = true)
            Result.success(dest)
        } catch (e: Exception) {
            Result.failure(Exception("오디오 파일 복사 실패: ${e.message}"))
        }
    }

    fun getFileSizeMb(file: File): Double =
        if (file.exists()) file.length() / (1024.0 * 1024.0) else 0.0

    fun listAudioFiles(audioDir: File): List<File> {
        if (!audioDir.exists()) return emptyList()
        val extensions = setOf("mp3", "wav", "m4a", "mp4", "ogg", "flac")
        return audioDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in extensions }
            .sortedByDescending { it.lastModified() }
            .toList()
    }

    fun listSummaryFiles(summaryDir: File): List<File> {
        if (!summaryDir.exists()) return emptyList()
        val extensions = setOf("md", "txt")
        return summaryDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in extensions }
            .sortedByDescending { it.lastModified() }
            .toList()
    }
}
