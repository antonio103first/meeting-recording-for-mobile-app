package com.krunventures.meetingrecorder.util

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Markdown → HTML → PDF 변환 유틸리티
 *
 * Android WebView를 사용하여 Markdown을 HTML로 렌더링 후
 * PdfDocument API로 PDF 파일을 생성합니다.
 * 카카오톡 등 메신저 앱에서 프린트된 형태로 공유할 수 있습니다.
 */
object MdToPdfConverter {

    private const val TAG = "MdToPdfConverter"

    /**
     * Markdown 텍스트를 PDF 파일로 변환
     * @param context Application context
     * @param mdText Markdown 텍스트
     * @param outputFile 출력 PDF 파일
     * @param title 문서 제목
     * @return 변환 성공 여부
     */
    fun convert(context: Context, mdText: String, outputFile: File, title: String = "회의록"): Boolean {
        return try {
            val html = markdownToHtml(mdText, title)
            htmlToPdf(context, html, outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "MD→PDF 변환 실패", e)
            false
        }
    }

    /**
     * 간단한 Markdown → HTML 변환 (외부 라이브러리 불필요)
     */
    private fun markdownToHtml(md: String, title: String): String {
        var html = escapeHtml(md)

        // 테이블 변환 (--- 구분선 포함)
        html = convertTables(html)

        // 헤딩 변환 (### → <h3>, ## → <h2>, # → <h1>)
        html = html.replace(Regex("^### (.+)$", RegexOption.MULTILINE), "<h3>$1</h3>")
        html = html.replace(Regex("^## (.+)$", RegexOption.MULTILINE), "<h2>$1</h2>")
        html = html.replace(Regex("^# (.+)$", RegexOption.MULTILINE), "<h1>$1</h1>")

        // 수평선
        html = html.replace(Regex("^---+$", RegexOption.MULTILINE), "<hr/>")

        // 줄바꿈
        html = html.replace("\n", "<br/>\n")

        // 불필요한 <br/> 제거 (태그 뒤)
        html = html.replace(Regex("</h[123]><br/>"), "</h1>")
            .replace("</h2><br/>", "</h2>")
            .replace("</h3><br/>", "</h3>")
            .replace("<hr/><br/>", "<hr/>")
            .replace("</table><br/>", "</table>")
            .replace("</tr><br/>", "</tr>")

        return """
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<style>
  @page { margin: 20mm; }
  body {
    font-family: 'Noto Sans KR', sans-serif;
    font-size: 11pt;
    line-height: 1.6;
    color: #222;
    max-width: 100%;
    padding: 0;
    margin: 0;
  }
  h1 { font-size: 18pt; border-bottom: 2px solid #333; padding-bottom: 6px; margin-top: 16px; }
  h2 { font-size: 14pt; border-bottom: 1px solid #999; padding-bottom: 4px; margin-top: 14px; }
  h3 { font-size: 12pt; margin-top: 12px; color: #333; }
  hr { border: none; border-top: 1px solid #ccc; margin: 12px 0; }
  table {
    border-collapse: collapse;
    width: 100%;
    margin: 8px 0;
    font-size: 10pt;
  }
  th, td {
    border: 1px solid #999;
    padding: 6px 10px;
    text-align: left;
  }
  th { background-color: #f0f0f0; font-weight: bold; }
  .footer { font-size: 9pt; color: #888; margin-top: 16px; text-align: center; }
</style>
</head>
<body>
$html
</body>
</html>
""".trimIndent()
    }

    /**
     * HTML을 PDF로 변환 (WebView 렌더링 → PdfDocument)
     */
    private fun htmlToPdf(context: Context, html: String, outputFile: File): Boolean {
        val latch = CountDownLatch(1)
        var success = false

        Handler(Looper.getMainLooper()).post {
            try {
                val webView = WebView(context)
                webView.settings.apply {
                    javaScriptEnabled = false
                    defaultTextEncodingName = "UTF-8"
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }

                // A4 사이즈 설정 (595 x 842 포인트 = A4 at 72dpi)
                val pageWidth = 595
                val pageHeight = 842

                webView.layout(0, 0, pageWidth, pageHeight * 3) // 여유 높이

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        try {
                            // WebView 렌더링 완료 후 약간의 딜레이
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    val document = PdfDocument()
                                    val contentHeight = webView.contentHeight
                                    val scale = webView.scale
                                    val totalHeight = (contentHeight * scale).toInt()
                                    val pageCount = maxOf(1, (totalHeight + pageHeight - 1) / pageHeight)

                                    for (i in 0 until pageCount) {
                                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create()
                                        val page = document.startPage(pageInfo)
                                        val canvas = page.canvas

                                        // 페이지별 Y 오프셋 적용
                                        canvas.translate(0f, -(i * pageHeight).toFloat())
                                        webView.draw(canvas)
                                        document.finishPage(page)
                                    }

                                    outputFile.parentFile?.mkdirs()
                                    FileOutputStream(outputFile).use { fos ->
                                        document.writeTo(fos)
                                    }
                                    document.close()

                                    Log.d(TAG, "✅ PDF 생성 완료: ${outputFile.absolutePath} (${pageCount}페이지)")
                                    success = true
                                } catch (e: Exception) {
                                    Log.e(TAG, "PDF 문서 생성 오류", e)
                                } finally {
                                    webView.destroy()
                                    latch.countDown()
                                }
                            }, 500) // 렌더링 안정화 대기
                        } catch (e: Exception) {
                            Log.e(TAG, "WebView 후처리 오류", e)
                            latch.countDown()
                        }
                    }
                }

                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            } catch (e: Exception) {
                Log.e(TAG, "WebView 초기화 오류", e)
                latch.countDown()
            }
        }

        // 최대 10초 대기
        latch.await(10, TimeUnit.SECONDS)
        return success
    }

    /**
     * HTML 특수문자 이스케이프 (Markdown 처리 전에 적용)
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    /**
     * Markdown 테이블을 HTML 테이블로 변환
     */
    private fun convertTables(text: String): String {
        val lines = text.split("\n").toMutableList()
        val result = StringBuilder()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            // 테이블 감지: | 로 시작하고 다음 줄이 |---|---|
            if (line.startsWith("|") && i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                if (nextLine.matches(Regex("\\|[-|: ]+"))) {
                    // 테이블 시작
                    result.append("<table>\n")

                    // 헤더 행
                    val headers = parseCells(line)
                    result.append("<tr>")
                    headers.forEach { result.append("<th>$it</th>") }
                    result.append("</tr>\n")

                    i += 2 // 헤더 + 구분선 건너뜀

                    // 데이터 행
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val cells = parseCells(lines[i].trim())
                        result.append("<tr>")
                        cells.forEach { result.append("<td>$it</td>") }
                        result.append("</tr>\n")
                        i++
                    }
                    result.append("</table>\n")
                    continue
                }
            }

            result.append(lines[i])
            result.append("\n")
            i++
        }

        return result.toString()
    }

    /**
     * 테이블 행에서 셀 값 추출
     */
    private fun parseCells(row: String): List<String> {
        return row.trim().removeSurrounding("|").split("|").map { it.trim() }
    }
}
