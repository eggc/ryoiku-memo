package net.eggc.ryoikumemo.ui.feature.timer

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.delay

private const val DEFAULT_DURATION_MS = 3 * 60 * 1000L
private const val ONE_SECOND_MS = 1000L

@Composable
fun TimerScreen(
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var totalDurationMs by rememberSaveable { mutableLongStateOf(DEFAULT_DURATION_MS) }
    var remainingMs by rememberSaveable { mutableLongStateOf(DEFAULT_DURATION_MS) }
    var remainingInput by rememberSaveable { mutableStateOf(formatDuration(DEFAULT_DURATION_MS)) }
    var isInputFocused by rememberSaveable { mutableStateOf(false) }
    var hasPendingInputEdit by rememberSaveable { mutableStateOf(false) }
    var runStartElapsedMs by rememberSaveable { mutableLongStateOf(0L) }
    var runStartRemainingMs by rememberSaveable { mutableLongStateOf(DEFAULT_DURATION_MS) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var completionSoundPlayed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect

        while (isRunning && remainingMs > 0L) {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - runStartElapsedMs
            val rawRemaining = (runStartRemainingMs - elapsed).coerceAtLeast(0L)

            val roundedRemaining = if (rawRemaining == 0L) 0L else ((rawRemaining + (ONE_SECOND_MS - 1L)) / ONE_SECOND_MS) * ONE_SECOND_MS
            if (roundedRemaining != remainingMs) {
                remainingMs = roundedRemaining
            }

            if (roundedRemaining == 0L) {
                isRunning = false
                break
            }

            val elapsedFromStart = now - runStartElapsedMs
            val waitMs = ONE_SECOND_MS - (elapsedFromStart % ONE_SECOND_MS)
            delay(waitMs)
        }
    }

    LaunchedEffect(remainingMs, isRunning, isInputFocused) {
        if (isInputFocused) {
            return@LaunchedEffect
        }

        remainingInput = formatDuration(remainingMs)
    }

    LaunchedEffect(remainingMs, isRunning, completionSoundPlayed) {
        if (remainingMs == 0L && !isRunning && !completionSoundPlayed) {
            completionSoundPlayed = true
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1800)
            delay(1900)
            tone.release()
        }
    }

    val progress = if (totalDurationMs <= 0L) 0f else (remainingMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    val redAccent = MaterialTheme.colorScheme.error

    fun commitInputIfPossible() {
        if (isRunning) return
        if (!hasPendingInputEdit) {
            remainingInput = formatDuration(remainingMs)
            return
        }

        parseDurationToMillis(remainingInput)?.let { parsed ->
            totalDurationMs = parsed
            remainingMs = parsed
            completionSoundPlayed = false
            remainingInput = formatDuration(parsed)
        } ?: run {
            remainingInput = formatDuration(remainingMs)
        }
        hasPendingInputEdit = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        focusManager.clearFocus()
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = remainingInput,
            onValueChange = { input ->
                remainingInput = input
                hasPendingInputEdit = true
            },
            enabled = !isRunning,
            singleLine = true,
            label = { Text("残り時間") },
            textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (isRunning) {
                        isInputFocused = false
                        return@onFocusChanged
                    }

                    if (focusState.isFocused && !isInputFocused) {
                        remainingInput = ""
                        hasPendingInputEdit = false
                    }

                    if (!focusState.isFocused && isInputFocused) {
                        commitInputIfPossible()
                    }
                    isInputFocused = focusState.isFocused
                }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(280.dp)) {
                drawCircle(color = redAccent.copy(alpha = 0.18f))

                val sweep = 360f * progress
                if (sweep > 0.1f) {
                    drawArc(
                        color = redAccent,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = true,
                        style = Fill,
                        size = Size(size.width, size.height)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                modifier = Modifier.size(56.dp),
                onClick = {
                    focusManager.clearFocus()
                    commitInputIfPossible()
                    if (remainingMs > 0L) {
                        runStartElapsedMs = SystemClock.elapsedRealtime()
                        runStartRemainingMs = remainingMs
                        completionSoundPlayed = false
                        isRunning = true
                    }
                },
                enabled = !isRunning && remainingMs > 0L
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "スタート"
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            OutlinedIconButton(
                modifier = Modifier.size(56.dp),
                onClick = {
                    focusManager.clearFocus()
                    isRunning = false
                },
                enabled = isRunning
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "一時停止"
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            OutlinedIconButton(
                modifier = Modifier.size(56.dp),
                onClick = {
                    focusManager.clearFocus()
                    commitInputIfPossible()
                    isRunning = false
                    remainingMs = totalDurationMs
                    completionSoundPlayed = false
                },
                enabled = !isRunning || remainingMs != totalDurationMs
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "リセット"
                )
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

private fun parseDurationToMillis(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    val parsedSeconds = if (trimmed.contains(':')) {
        val parts = trimmed.split(':')
        if (parts.size != 2) return null
        val minutes = parts[0].toLongOrNull() ?: return null
        val seconds = parts[1].toLongOrNull() ?: return null
        if (seconds !in 0..59) return null
        minutes * 60 + seconds
    } else {
        trimmed.toLongOrNull() ?: return null
    }

    if (parsedSeconds <= 0L) return null
    return parsedSeconds * 1000L
}
