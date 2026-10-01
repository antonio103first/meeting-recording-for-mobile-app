package com.krunventures.meetingrecorder.service

/**
 * ★ v3.14.0: STT '줄 내부' 반복 루프 감지·정리 — PC app_dist/gemini_service.py 의
 *   _RUN_LIMIT / _runs / _has_inline_loop / _collapse_inline_loops 와 동일 로직(이식).
 *
 * 실사례(2026-09-30, 62분 티타임 정밀 STT 13구간 중 3구간): '네.' 20,143회(한 줄 82,902자) /
 *   '아이고' 1,247회 / '외국인' 1,200회 — 줄이 1개라 줄 단위 반복 검사로는 잡히지 않았음.
 *
 * 판정: 공백 기준 어절 토큰에서 1~4어절 n-gram 이 연속 [RUN_LIMIT]회 이상 반복되면 루프.
 * 정리: 반복 단위를 1회만 남기고 바로 뒤에 `[반복 인식 {k}회 생략]` 을 넣는다(조용한 삭제 금지).
 *
 * Android 의존성이 없는 순수 Kotlin — JVM 단위 테스트(SttLoopGuardTest) 대상.
 */
object SttLoopGuard {
    /** 같은 1~4어절이 연속 이만큼 반복되면 루프로 판정 (사람은 "네 네 네" 정도가 한계) */
    const val RUN_LIMIT = 15

    /** (시작 인덱스, n, 반복횟수) */
    data class Run(val start: Int, val n: Int, val k: Int)

    private fun tokens(line: String): List<String> =
        line.split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** 1~4어절 n-gram 이 RUN_LIMIT 회 이상 연속 반복된 구간 목록 (PC _runs 와 동일 탐색 순서) */
    fun runs(toks: List<String>): List<Run> {
        val out = mutableListOf<Run>()
        val size = toks.size
        var i = 0
        while (i < size) {
            var best: Run? = null
            for (n in 1..4) {
                if (i + n > size) break
                val unit = toks.subList(i, i + n)
                var k = 1
                while (i + (k + 1) * n <= size && toks.subList(i + k * n, i + (k + 1) * n) == unit) k++
                if (k >= RUN_LIMIT && (best == null || k * n > best.n * best.k)) best = Run(i, n, k)
            }
            if (best != null) {
                out.add(best)
                i += best.n * best.k
            } else {
                i += 1
            }
        }
        return out
    }

    /** 한 줄 안에서 같은 어절(구)이 끝없이 반복되는 루프가 있는지 */
    fun hasInlineLoop(text: String): Boolean =
        text.lines().any { runs(tokens(it)).isNotEmpty() }

    /** 재시도로도 못 고친 루프를 1회만 남기고 잘라냄. 잘라낸 사실은 본문에 `[반복 인식 N회 생략]`으로 표시. */
    fun collapseInlineLoops(text: String): String =
        text.lines().joinToString("\n") { line ->
            val toks = tokens(line)
            val rs = runs(toks)
            if (rs.isEmpty()) line
            else {
                val res = mutableListOf<String>()
                var pos = 0
                for (r in rs) {
                    res += toks.subList(pos, r.start)
                    res += toks.subList(r.start, r.start + r.n)
                    res += "[반복 인식 ${r.k}회 생략]"
                    pos = r.start + r.n * r.k
                }
                res += toks.subList(pos, toks.size)
                res.joinToString(" ")
            }
        }
}
