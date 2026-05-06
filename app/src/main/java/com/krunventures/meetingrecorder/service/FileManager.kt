package com.krunventures.meetingrecorder.service

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileManager {
    fun getDefaultBaseName(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    /** 요약 모드 → 한글 라벨 변환 */
    fun getModeLabel(summaryMode: String): String = when (summaryMode) {
        "topic" -> "다자간협의"
        "formal_md" -> "회의록업무"
        "ir_md" -> "IR미팅"
        "phone" -> "전화메모"
        "flow" -> "네트워킹"
        "lecture_md" -> "강의요약"
        "conference" -> "컨퍼런스"
        else -> "주간회의"
    }

    /**
     * 요약 결과 텍스트에서 제목을 자동 추출
     * - 첫 번째 # 헤더에서 제목 추출
     * - 테이블의 '기업명', '대상기업', '회의명' 등에서 추출
     * - 추출 실패 시 빈 문자열 반환
     */
    fun extractTitleFromSummary(summaryText: String): String {
        val lines = summaryText.lines()
        // 1) 첫 번째 # 헤더에서 제목 추출 (## 이상은 건너뜀)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ") && !trimmed.startsWith("## ")) {
                val title = trimmed.removePrefix("# ").trim()
                // 일반적인 헤더명(주간회의록, 회의록, IR 미팅회의록 등)은 건너뜀 — 구체적 제목이 아님
                if (title !in listOf("주간회의록", "회의록(업무)", "다자간 협의", "IR 미팅회의록",
                        "전화통화 메모", "네트워킹(티타임)", "강의 요약 노트")) {
                    return sanitizeFileName(title)
                }
            }
        }
        // 2) 테이블에서 기업명/회의명/강의명/상대방 추출
        val tableKeywords = listOf("기업명", "대 상 기 업", "회의명", "강의명", "상 대 방")
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            for (keyword in tableKeywords) {
                if (trimmed.contains(keyword) && trimmed.startsWith("|") && trimmed.endsWith("|")) {
                    val cells = trimmed.split("|").filter { it.isNotBlank() }.map { it.trim() }
                    if (cells.size >= 2) {
                        val value = cells[1].trim()
                        if (value.isNotBlank() && value != "(녹취록에서 자동 식별)" && value != "(투자업체명 자동 식별)") {
                            return sanitizeFileName(value)
                        }
                    }
                }
            }
        }
        return ""
    }

    /** 파일명에 사용할 수 없는 문자 제거 */
    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(40)

    /**
     * ★ v3.2: 기본 저장 파일명 생성
     * 형식: 제목_20260424(강의요약)
     * 제목이 없으면: 회의록_20260424(강의요약)
     */
    fun buildSuggestedFileName(summaryText: String, summaryMode: String): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val modeLabel = getModeLabel(summaryMode)
        val title = extractTitleFromSummary(summaryText).ifBlank { "회의록" }
        return "${title}_${date}(${modeLabel})"
    }

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
            val file = File(saveDir, "${fileName}.txt")
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
            val file = File(saveDir, "${fileName}.txt")
            file.writeText(combined, Charsets.UTF_8)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(Exception("통합 요약 파일 저장 실패: ${e.message}"))
        }
    }

    /**
     * V2.0: 재요약 결과를 별도 파일로 저장
     * 기존 STT 텍스트 없이 요약 결과만 저장
     */
    fun saveResummarizedSummary(summaryText: String, saveDir: File, fileName: String): Result<File> {
        return try {
            saveDir.mkdirs()
            val content = buildString {
                appendLine("# 회의록 재요약")
                appendLine()
                appendLine(summaryText.trim())
                appendLine()
                appendLine("---")
                appendLine("회의녹음요약 앱 재요약 자동 생성")
            }
            val file = File(saveDir, "${fileName}.txt")
            file.writeText(content, Charsets.UTF_8)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(Exception("재요약 파일 저장 실패: ${e.message}"))
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

    /**
     * V2.0: 재요약용 파일이름 포맷 생성
     * 형식: {날짜}_{원본파일명}_{요약방식라벨}.md
     * 예: 20260322_투자심사_흐름중심.md
     */
    /**
     * ★ v3.2: 재요약용 파일이름 — 동일한 제목_날짜(모드) 형식
     */
    fun getResummarizeFileName(originalFileName: String, summaryMode: String): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val modeLabel = getModeLabel(summaryMode)
        val baseName = originalFileName
            .removeSuffix(".md")
            .removeSuffix(".txt")
            .removeSuffix(".stt")
            // 기존 날짜/모드 패턴 제거 (재요약 시 중복 방지)
            .replace(Regex("_\\d{8}\\(.*\\)$"), "")
            .replace(Regex("_회의록$"), "")
            .take(40)
        return "${baseName}_${date}(${modeLabel})"
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
