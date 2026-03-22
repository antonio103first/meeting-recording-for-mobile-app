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
