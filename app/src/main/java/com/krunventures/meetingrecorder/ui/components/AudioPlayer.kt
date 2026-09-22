package com.krunventures.meetingrecorder.ui.components

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

private const val TAG = "AudioPlayerState"

/**
 * 회의 녹음파일(MP3/M4A) 미니 재생기 — 리스트/화면 단위로 한 번에 하나만 재생되도록
 * 재생 상태를 한 곳에서 들고 있는 홀더. 여러 항목이 각자 MediaPlayer를 만들면
 * 동시에 여러 개가 재생될 수 있어(리스트에서 여러 행을 눌렀을 때) 이 방식으로 방지한다.
 *
 * 안드로이드 내장 MediaPlayer를 사용 — 파일을 통째로 메모리에 올리지 않고 디스크에서
 * 스트리밍 재생하므로 PC(Roundtable) 쪽처럼 긴 녹음의 메모리/로딩 시간 문제가 없다.
 */
class AudioPlayerState {
    var currentPath by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isPreparing by mutableStateOf(false)
        private set
    var isError by mutableStateOf(false)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var durationMs by mutableIntStateOf(0)
        private set

    private var mediaPlayer: MediaPlayer? = null

    /** 같은 파일이면 재생/일시정지 토글, 다른 파일이면 새로 재생 시작 */
    fun toggle(path: String) {
        if (currentPath == path && mediaPlayer != null) {
            if (isPlaying) pause() else resume()
        } else {
            play(path)
        }
    }

    fun play(path: String) {
        release()
        val file = File(path)
        if (!file.exists()) {
            isError = true
            currentPath = path
            return
        }
        currentPath = path
        isError = false
        isPreparing = true
        positionMs = 0
        durationMs = 0
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    durationMs = mp.duration
                    isPreparing = false
                    try {
                        mp.start()
                        this@AudioPlayerState.isPlaying = true
                    } catch (e: Exception) {
                        Log.e(TAG, "start 실패", e)
                        isError = true
                    }
                }
                setOnCompletionListener {
                    this@AudioPlayerState.isPlaying = false
                    positionMs = 0
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "재생 오류 what=$what extra=$extra path=$path")
                    isError = true
                    isPreparing = false
                    this@AudioPlayerState.isPlaying = false
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "재생 준비 실패: $path", e)
            isError = true
            isPreparing = false
        }
    }

    private fun resume() {
        try {
            mediaPlayer?.start()
            isPlaying = true
        } catch (e: Exception) {
            Log.e(TAG, "재개 실패", e)
            currentPath?.let { play(it) }
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {
        }
        isPlaying = false
    }

    fun seekTo(ms: Int) {
        try {
            mediaPlayer?.seekTo(ms)
            positionMs = ms
        } catch (_: Exception) {
        }
    }

    fun syncPosition() {
        if (isPlaying) {
            try {
                positionMs = mediaPlayer?.currentPosition ?: positionMs
            } catch (_: Exception) {
            }
        }
    }

    fun release() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        isPlaying = false
        isPreparing = false
    }
}

@Composable
fun rememberAudioPlayerState(): AudioPlayerState {
    val state = remember { AudioPlayerState() }
    DisposableEffect(Unit) {
        onDispose { state.release() }
    }
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            state.syncPosition()
            delay(200)
        }
    }
    return state
}

private fun formatMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

/**
 * ▶/⏸ 버튼 + 진행바 + 시간표시 한 줄. [path]가 이 플레이어의 현재 로드된 파일이 아니면
 * 버튼을 누르는 즉시 [AudioPlayerState.play]가 호출되어 새로 재생을 시작한다.
 */
@Composable
fun MiniAudioPlayerRow(
    path: String,
    player: AudioPlayerState,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    textLightColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onShare: (() -> Unit)? = null,   // v3.12.0: 📤 녹음파일 보내기(카톡/슬랙 등). null이면 버튼 없음
) {
    val isCurrent = player.currentPath == path
    val playing = isCurrent && player.isPlaying
    val preparing = isCurrent && player.isPreparing
    val error = isCurrent && player.isError

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = { player.toggle(path) },
                enabled = !preparing,
                modifier = Modifier.size(36.dp)
            ) {
                if (preparing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "일시정지" else "재생",
                        tint = accentColor
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                if (error) "재생할 수 없는 파일입니다"
                else "${formatMs(if (isCurrent) player.positionMs else 0)} / ${formatMs(if (isCurrent) player.durationMs else 0)}",
                fontSize = 11.sp,
                color = if (error) MaterialTheme.colorScheme.error else textLightColor
            )
            Spacer(Modifier.width(6.dp))
            if (!error) {
                Slider(
                    value = if (isCurrent && player.durationMs > 0)
                        player.positionMs.toFloat().coerceIn(0f, player.durationMs.toFloat())
                    else 0f,
                    onValueChange = { v -> if (isCurrent) player.seekTo(v.toInt()) },
                    valueRange = 0f..(if (isCurrent && player.durationMs > 0) player.durationMs.toFloat() else 1f),
                    enabled = isCurrent && player.durationMs > 0,
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                )
            }
            if (onShare != null) {
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "녹음파일 보내기",
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
