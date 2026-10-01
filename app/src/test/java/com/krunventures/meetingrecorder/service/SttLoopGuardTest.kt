package com.krunventures.meetingrecorder.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ★ v3.14.0: 줄 내부 반복 루프 감지·정리 — PC _has_inline_loop / _collapse_inline_loops 와 같은 결과여야 함 */
class SttLoopGuardTest {

    @Test
    fun `네 100회 연속은 루프`() {
        assertTrue(SttLoopGuard.hasInlineLoop("네. ".repeat(100)))
    }

    @Test
    fun `사람의 자연스러운 맞장구는 루프 아님`() {
        assertFalse(SttLoopGuard.hasInlineLoop("네 네 네 네 그렇죠. 네. 네. 네."))
    }

    @Test
    fun `14회는 루프 아님 15회부터 루프`() {
        assertFalse(SttLoopGuard.hasInlineLoop("아이고 ".repeat(14)))
        assertTrue(SttLoopGuard.hasInlineLoop("아이고 ".repeat(15)))
    }

    @Test
    fun `여러 어절 단위 반복도 감지`() {
        assertTrue(SttLoopGuard.hasInlineLoop("앞 " + "외국인 노동자 문제 ".repeat(20) + "뒤"))
    }

    @Test
    fun `정리 시 1회만 남기고 생략 표시`() {
        val src = "시작합니다 " + "네. ".repeat(100) + "끝입니다\n다음 줄 그대로"
        val out = SttLoopGuard.collapseInlineLoops(src)
        assertEquals("시작합니다 네. [반복 인식 100회 생략] 끝입니다\n다음 줄 그대로", out)
        assertFalse(SttLoopGuard.hasInlineLoop(out))
    }

    @Test
    fun `루프 없는 줄은 원문 그대로`() {
        val src = "  공백  포함   원문은\n그대로 둡니다"
        assertEquals(src, SttLoopGuard.collapseInlineLoops(src))
    }
}
