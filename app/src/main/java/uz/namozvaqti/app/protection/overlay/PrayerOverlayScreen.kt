package uz.namozvaqti.app.protection.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import uz.namozvaqti.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import uz.namozvaqti.app.protection.ProtectionSession

private val DeepGreen = Color(0xFF0B3D2E)
private val Night = Color(0xFF061A14)
private val Gold = Color(0xFFD4AF37)
private val Cream = Color(0xFFF5EFD9)

private const val HOLD_TO_EXIT_MS = 15_000

@Composable
fun PrayerOverlayScreen(
    session: ProtectionSession,
    onExitConfirmed: () -> Unit
) {
    var remainingMs by remember { mutableLongStateOf(session.endsAtMillis - System.currentTimeMillis()) }
    LaunchedEffect(session.endsAtMillis) {
        while (true) {
            remainingMs = (session.endsAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
            delay(250)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepGreen, Night)))
            // Swallow every touch that is not on the exit button.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
            .systemBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.overlay_arabic),
                    color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.overlay_title),
                    color = Cream, fontSize = 22.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(session.prayerName, color = Gold, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = formatMmSs(remainingMs),
                    color = Cream,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                Text(
                    stringResource(R.string.overlay_subtitle),
                    color = Cream.copy(alpha = 0.75f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }

            HoldToExitButton(onCompleted = onExitConfirmed)
        }
    }
}

/**
 * Emergency exit: must be held for 15 CONTINUOUS seconds. Lifting the finger resets progress.
 * This is the Google Play "user is never trapped" safeguard.
 */
@Composable
fun HoldToExitButton(
    holdMillis: Int = HOLD_TO_EXIT_MS,
    onCompleted: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    val secondsLeft = ceil((1f - progress.value) * holdMillis / 1000f).toInt()

    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .pointerInput(holdMillis) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val hold = scope.launch {
                        progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
                        currentOnCompleted()
                    }
                    waitForUpOrCancellation() // returns when finger lifts or gesture is cancelled
                    hold.cancel()
                    scope.launch { progress.animateTo(0f, tween(250)) }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(Gold.copy(alpha = 0.55f))
        )
        Text(
            text = if (progress.value == 0f) stringResource(R.string.overlay_hold_to_exit, holdMillis / 1000)
            else stringResource(R.string.overlay_keep_holding, secondsLeft),
            color = Cream,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatMmSs(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}
