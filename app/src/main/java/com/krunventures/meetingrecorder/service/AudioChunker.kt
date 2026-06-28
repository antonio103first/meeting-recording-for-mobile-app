package com.krunventures.meetingrecorder.service

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * ★ v3.7.2: 긴 오디오를 시간 단위(기본 10분)로 분할한다.
 *
 * 배경: Gemini STT가 긴 녹음(특히 "네/예" 맞장구가 많은 회의)에서
 *   `[화자1] 네. [화자2] 네.` 같은 반복 루프에 빠져 출력 토큰을 모두 소진하고
 *   전사가 통째로 잘리는 사고가 있었음(35분 회의에서 99.5%가 반복 루프).
 *   → 오디오를 짧은 청크로 나눠 각각 전사하면 한 청크의 루프가 전체를 망치지 않고,
 *     컨텍스트가 짧아져 루프 발생 확률도 낮아진다.
 *
 * AAC(M4A) 트랙을 재인코딩 없이 remux 한다(빠르고 무손실).
 * 비AAC(mp3 등)·짧은 파일·실패 시에는 원본 1개를 그대로 반환(폴백).
 */
object AudioChunker {
    private const val TAG = "AudioChunker"

    /**
     * @param src 원본 오디오(앱 녹음 m4a 권장)
     * @param outDir 분할 청크를 저장할 임시 폴더
     * @param chunkSec 청크 길이(초). 기본 600초(10분)
     * @return 분할된 청크 파일 목록. 분할 불가 시 [src] 1개
     */
    fun splitByDuration(src: File, outDir: File, chunkSec: Int = 600): List<File> {
        if (!src.exists()) return listOf(src)
        val chunkUs = chunkSec.toLong() * 1_000_000L
        val extractor = MediaExtractor()
        val created = mutableListOf<File>()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(src.absolutePath)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) { audioTrack = i; format = f; break }
            }
            if (audioTrack < 0 || format == null) return listOf(src)

            // MediaMuxer(MP4)는 AAC 만 안정적으로 remux 가능 — 그 외(mp3 등)는 폴백
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (!mime.contains("mp4a") && !mime.contains("aac")) {
                Log.w(TAG, "비AAC($mime) — 분할 생략, 원본 사용")
                return listOf(src)
            }

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
                format.getLong(MediaFormat.KEY_DURATION) else -1L
            if (durationUs in 1..chunkUs) return listOf(src)  // 한 청크보다 짧음 → 분할 불필요

            outDir.mkdirs()
            extractor.selectTrack(audioTrack)
            val maxInput = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE))
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
            val buffer = ByteBuffer.allocate(max(maxInput, 512 * 1024))
            val info = MediaCodec.BufferInfo()

            var segIndex = 0
            var segStartUs = -1L
            var muxTrack = -1

            fun startSeg(firstPts: Long) {
                val out = File(outDir, "${src.nameWithoutExtension}_seg${segIndex}.m4a")
                if (out.exists()) out.delete()
                val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxTrack = m.addTrack(format!!)
                m.start()
                muxer = m
                created.add(out)
                segStartUs = firstPts
            }

            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val pts = extractor.sampleTime
                if (muxer == null) {
                    startSeg(pts)
                } else if (pts - segStartUs >= chunkUs) {
                    muxer!!.stop(); muxer!!.release(); muxer = null
                    segIndex++
                    startSeg(pts)
                }
                info.offset = 0
                info.size = size
                info.presentationTimeUs = pts - segStartUs  // 각 청크는 0 부근에서 시작
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer!!.writeSampleData(muxTrack, buffer, info)
                extractor.advance()
            }
            muxer?.let { it.stop(); it.release() }
            muxer = null

            return if (created.size >= 2) created
            else {
                // 청크가 1개뿐이면(짧은 파일) 임시본 버리고 원본 사용
                created.forEach { runCatching { it.delete() } }
                listOf(src)
            }
        } catch (e: Exception) {
            Log.e(TAG, "오디오 분할 실패 — 원본 사용: ${e.message}", e)
            try { muxer?.release() } catch (_: Exception) {}
            created.forEach { runCatching { it.delete() } }
            return listOf(src)
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
