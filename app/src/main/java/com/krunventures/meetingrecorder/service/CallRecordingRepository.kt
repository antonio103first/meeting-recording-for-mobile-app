package com.krunventures.meetingrecorder.service

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ★ v3.11: 통화 녹음 파일 1건 (SAF 문서).
 *
 * T전화(SKT) 통화녹음 파일명 규칙:
 *   {이름}({소속})_{전화번호}_{yyyyMMddHHmmss}.m4a
 *   예) 성낙환(디캠프)_01031678395_20260713183840.m4a
 *       김영후_01027357783_20260713164941.m4a          ← 소속 없음
 */
data class CallRecording(
    val uri: Uri,
    val fileName: String,
    /** 상대방 이름 — 파싱 실패 시 확장자 뺀 파일명 */
    val person: String,
    /** 소속 — 괄호가 없으면 "" */
    val org: String,
    val phone: String,
    /** 통화 시각 (파일명 타임스탬프 우선, 없으면 파일 수정시각) */
    val timeMillis: Long,
    val sizeBytes: Long,
    /** 이미 STT·요약을 마친 파일인지 */
    val processed: Boolean = false
) {
    /** 목록 표시용 — "07-13 18:38" */
    val dateLabel: String
        get() = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))

    /** 요약 본문 '일 시' 칸에 넣을 값 — "2026-07-13 18:38" */
    val dateTimeLabel: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))

    /** 요약 파일명용 — "20260713" */
    val yyyymmdd: String
        get() = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timeMillis))

    /** 요약 본문 '상 대 방' 칸에 넣을 값 — "성낙환 (디캠프)" */
    val whoLabel: String
        get() = if (org.isBlank()) person else "$person ($org)"

    val sizeMbLabel: String
        get() = String.format(Locale.getDefault(), "%.1fMB", sizeBytes / 1024.0 / 1024.0)

    /**
     * Obsidian 자동화(route_inbox_notes)가 `02_Persons/{인물}/` 로 라우팅하는 파일명 규칙에 맞춘 기본 이름.
     * → "성낙환_20260713_전화통화"
     */
    val suggestedFileName: String
        get() = "${sanitize(person)}_${yyyymmdd}_전화통화"

    private fun sanitize(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|\[\]#^]"""), "").trim().ifBlank { "통화" }
}

/**
 * ★ v3.11: 휴대폰의 통화녹음 폴더(Recordings/TPhoneCallRecords)를 SAF로 읽어 최신순 목록을 만든다.
 *
 * Android 11+ Scoped Storage 라 File API 로는 접근 불가 → SAF tree URI(1회 폴더 등록·영구 권한) 사용.
 * 목록 조회에 DocumentFile.listFiles() 를 쓰면 파일당 쿼리가 나가 수천 건 폴더에서 수 초씩 걸리므로,
 * ContentResolver 커서 1회 조회(projection 4개)로 뽑고 메모리에서 정렬한다.
 */
class CallRecordingRepository(private val context: Context) {

    companion object {
        private const val TAG = "CallRecRepo"

        /** SAF 폴더 선택 시 곧바로 열어 줄 기본 위치 (T전화 통화녹음 폴더) */
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val DEFAULT_DOC_ID = "primary:Recordings/TPhoneCallRecords"

        /** {이름}({소속})_{번호}_{yyyyMMddHHmmss}.{확장자} */
        private val NAME_RE = Regex("""^(.+?)(?:\((.+?)\))?_([\d+*#-]{3,})_(\d{14})\.[A-Za-z0-9]+$""")

        private val AUDIO_EXT = setOf("m4a", "mp3", "aac", "amr", "wav", "3gp", "ogg", "mp4")

        /** 폴더 선택 다이얼로그를 통화녹음 폴더에서 시작시키는 initial URI */
        fun initialPickerUri(): Uri =
            DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, DEFAULT_DOC_ID)
    }

    /**
     * 통화녹음 폴더의 오디오 파일을 최신순(내림차순)으로 반환.
     *
     * @param treeUriString 사용자가 등록한 SAF tree URI
     * @param processedNames 이미 요약을 마친 파일명 (목록에 ✅ 표시)
     * @param limit 최근 N건만
     */
    fun list(
        treeUriString: String,
        processedNames: Set<String> = emptySet(),
        limit: Int = 30
    ): List<CallRecording> {
        if (treeUriString.isBlank()) return emptyList()
        return try {
            val treeUri = Uri.parse(treeUriString)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri)
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
            )
            val out = ArrayList<CallRecording>(256)
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val docId = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (name.substringAfterLast('.', "").lowercase() !in AUDIO_EXT) continue
                    val modified = if (c.isNull(2)) 0L else c.getLong(2)
                    val size = if (c.isNull(3)) 0L else c.getLong(3)
                    out.add(
                        parse(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                            name = name,
                            lastModified = modified,
                            size = size,
                            processed = name in processedNames
                        )
                    )
                }
            } ?: run {
                Log.w(TAG, "통화녹음 폴더 조회 실패 (cursor null): $treeUriString")
                return emptyList()
            }
            out.sortByDescending { it.timeMillis }
            if (out.size > limit) out.subList(0, limit).toList() else out
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ 통화녹음 폴더 권한 만료: $treeUriString", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 통화녹음 목록 조회 실패: $treeUriString", e)
            emptyList()
        }
    }

    /** 파일명에서 상대방·소속·번호·통화시각을 뽑는다. 규칙에 안 맞으면 파일명을 그대로 이름으로 쓴다. */
    private fun parse(
        uri: Uri,
        name: String,
        lastModified: Long,
        size: Long,
        processed: Boolean
    ): CallRecording {
        val m = NAME_RE.find(name)
        if (m != null) {
            val (rawPerson, org, phone, ts) = m.destructured
            val millis = parseTimestamp(ts) ?: lastModified
            return CallRecording(
                uri = uri,
                fileName = name,
                person = rawPerson.trim().ifBlank { phone },
                org = org.trim(),
                phone = phone,
                timeMillis = millis,
                sizeBytes = size,
                processed = processed
            )
        }
        return CallRecording(
            uri = uri,
            fileName = name,
            person = name.substringBeforeLast('.'),
            org = "",
            phone = "",
            timeMillis = lastModified,
            sizeBytes = size,
            processed = processed
        )
    }

    private fun parseTimestamp(ts: String): Long? = try {
        SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).parse(ts)?.time
    } catch (e: Exception) {
        null
    }
}
