package uz.namozvaqti.app.tasbeeh

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import uz.namozvaqti.app.R
import uz.namozvaqti.app.ui.theme.extras
import kotlin.math.cos
import kotlin.math.sin

private class Zikr(@StringRes val arabic: Int, @StringRes val name: Int, @StringRes val meaning: Int)

private val ZIKRS = listOf(
    Zikr(R.string.zikr_ar_1, R.string.zikr_name_1, R.string.zikr_meaning_1),
    Zikr(R.string.zikr_ar_2, R.string.zikr_name_2, R.string.zikr_meaning_2),
    Zikr(R.string.zikr_ar_3, R.string.zikr_name_3, R.string.zikr_meaning_3)
)

/**
 * The whole content area is the tap target. Zikr cycles every 33 taps:
 * 0-32 Subhanallah, 33-65 Alhamdulillah, 66-98 Allahu Akbar, then it starts over.
 */
@Composable
fun TasbeehScreen(modifier: Modifier = Modifier, vm: TasbeehViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

    // Counting with the screen dimming is annoying; keep it awake only while this screen is visible.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val count = state.count
    val zikr = ZIKRS[(count / ZIKR_CYCLE) % ZIKRS.size]
    val inRound = count % ZIKR_CYCLE

    // Celebration: when a tap completes a set, keep the ring full + gold for a moment, then reset.
    var celebrate by remember { mutableStateOf(false) }
    var lastCount by remember { mutableIntStateOf(-1) }
    LaunchedEffect(count) {
        val completedSet = lastCount >= 0 && count == lastCount + 1 && count > 0 && count % ZIKR_CYCLE == 0
        lastCount = count
        if (completedSet) {
            celebrate = true
            delay(700)
            celebrate = false
        }
    }
    val shownInRound = if (celebrate) ZIKR_CYCLE else inRound

    // Small "pop" on every tap (bigger on set completion).
    val pop = remember { Animatable(1f) }
    LaunchedEffect(count) {
        if (count == 0) return@LaunchedEffect
        pop.snapTo(if (count % ZIKR_CYCLE == 0) 1.10f else 1.04f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    val counterDescription = stringResource(R.string.tasbeeh_counter_desc, count)
    val countLabel = stringResource(R.string.tasbeeh_count_label)
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                )
            )
            // Tap ANYWHERE (except the small buttons on top, which consume their own taps).
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = countLabel,
                onClick = vm::onTap
            )
            .semantics { contentDescription = counterDescription }
    ) {
        // ---------- Top bar: title + reset / undo ----------
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.tasbeeh_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = vm::undo, enabled = count > 0) {
                Text(stringResource(R.string.tasbeeh_undo))
            }
            IconButton(onClick = { confirmReset = true }, enabled = count > 0) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_reset))
            }
        }

        // ---------- Centre: zikr + bead ring ----------
        Column(
            Modifier.fillMaxSize().padding(top = 64.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Crossfade(targetState = zikr, label = "zikr") { z ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(z.arabic),
                        style = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        stringResource(z.name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.extras.gold
                    )
                    Text(
                        stringResource(z.meaning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            BeadRing(
                filled = shownInRound,
                celebrate = celebrate,
                // Takes the space left between the zikr and the totals, so small phones never clip.
                modifier = Modifier.weight(1f).aspectRatio(1f).scale(pop.value)
            ) {
                Text(
                    if (celebrate) "$ZIKR_CYCLE" else "$inRound",
                    style = TextStyle(
                        fontSize = 88.sp,
                        fontWeight = FontWeight.Light,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (celebrate) MaterialTheme.extras.gold else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "/ $ZIKR_CYCLE",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.tasbeeh_total, count) + "  •  " +
                        stringResource(R.string.tasbeeh_rounds, count / ZIKR_CYCLE),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.tasbeeh_tap_anywhere),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.tasbeeh_reset_title)) },
            text = { Text(stringResource(R.string.tasbeeh_reset_text, count)) },
            confirmButton = {
                TextButton(onClick = { vm.reset(); confirmReset = false }) {
                    Text(stringResource(R.string.action_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

/**
 * 33 beads around a circle, like a real tasbeeh. Filled beads = taps in this set; the newest bead
 * is highlighted. Content (the counter) is centred inside.
 */
@Composable
private fun BeadRing(
    filled: Int,
    celebrate: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val gold = MaterialTheme.extras.gold
    val track = MaterialTheme.colorScheme.outlineVariant
    val disc = MaterialTheme.colorScheme.surfaceContainerLowest

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val ringRadius = size.minDimension / 2f - 14.dp.toPx()

            // Inner disc with a faint ring
            drawCircle(disc, radius = ringRadius - 18.dp.toPx(), center = c)
            drawCircle(
                color = primary.copy(alpha = 0.12f),
                radius = ringRadius - 18.dp.toPx(),
                center = c,
                style = Stroke(width = 2.dp.toPx())
            )

            for (i in 0 until ZIKR_CYCLE) {
                val angle = Math.toRadians(-90.0 + i * 360.0 / ZIKR_CYCLE)
                val pos = Offset(
                    c.x + (ringRadius * cos(angle)).toFloat(),
                    c.y + (ringRadius * sin(angle)).toFloat()
                )
                val isFilled = i < filled
                val isNewest = isFilled && i == filled - 1
                val color = when {
                    celebrate -> gold
                    isNewest -> gold
                    isFilled -> primary
                    else -> track
                }
                drawCircle(
                    color = color,
                    radius = if (isNewest) 7.dp.toPx() else 5.dp.toPx(),
                    center = pos
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}
