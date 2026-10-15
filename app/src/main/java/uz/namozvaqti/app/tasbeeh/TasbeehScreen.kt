package uz.namozvaqti.app.tasbeeh

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import uz.namozvaqti.app.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val Gold = Color(0xFFD4AF37)
private val DeepGreen = Color(0xFF0B3D2E)
private val Cream = Color(0xFFF5EFD9)
private val TARGETS = listOf(33, 99, 100)

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

    val progress by animateFloatAsState(
        targetValue = state.countInRound.toFloat() / state.target,
        animationSpec = tween(180),
        label = "tasbeeh-progress"
    )
    val atMilestone = state.count > 0 && state.count % state.target == 0
    val counterDescription = stringResource(R.string.tasbeeh_counter_desc, state.count)
    val countLabel = stringResource(R.string.tasbeeh_count_label)

    Column(
        modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
    ) {
        Text(stringResource(R.string.tasbeeh_title), style = MaterialTheme.typography.headlineMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TARGETS.forEach { t ->
                FilterChip(
                    selected = state.target == t,
                    onClick = { vm.setTarget(t) },
                    label = { Text("$t") }
                )
            }
        }

        // The big tap target
        Box(
            Modifier
                .size(280.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = countLabel) { vm.onTap() }
                .semantics { contentDescription = counterDescription },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawCircle(DeepGreen.copy(alpha = 0.10f))
                drawArc(
                    color = DeepGreen.copy(alpha = 0.15f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(stroke)
                )
                drawArc(
                    color = if (atMilestone) Gold else DeepGreen,
                    startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${state.count}",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    color = if (atMilestone) Gold else MaterialTheme.colorScheme.onSurface
                )
                Text("${state.countInRound} / ${state.target}", style = MaterialTheme.typography.titleMedium)
                if (state.rounds > 0) {
                    Text(stringResource(R.string.tasbeeh_rounds, state.rounds), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text(
            stringResource(R.string.tasbeeh_tap_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = vm::undo, enabled = state.count > 0) {
                Text(stringResource(R.string.tasbeeh_undo))
            }
            OutlinedButton(onClick = { confirmReset = true }, enabled = state.count > 0) {
                Text(stringResource(R.string.action_reset))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.tasbeeh_vibration))
            Switch(checked = state.hapticsEnabled, onCheckedChange = vm::setHaptics)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.tasbeeh_reset_title)) },
            text = { Text(stringResource(R.string.tasbeeh_reset_text, state.count)) },
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
