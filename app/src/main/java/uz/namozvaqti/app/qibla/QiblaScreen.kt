package uz.namozvaqti.app.qibla

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.namozvaqti.app.R
import kotlin.math.abs
import kotlin.math.roundToInt

private val DialFill = Color(0xFF0B3D2E)
private val Gold = Color(0xFFD4AF37)
private val Cream = Color(0xFFF5EFD9)
private val AlignedGreen = Color(0xFF4CAF50)
private val NorthRed = Color(0xFFE57373)

@Composable
fun QiblaScreen(modifier: Modifier = Modifier, vm: QiblaViewModel = viewModel()) {
    val ctx = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshLocation()
    }
    val hasLocationPermission = ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // Second smoothing layer: the spring eases between filtered sensor samples at display refresh rate.
    val animatedHeading by animateFloatAsState(
        targetValue = state.heading,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "heading"
    )

    val aligned = state.qiblaBearing?.let {
        abs(QiblaMath.shortestDelta(it - QiblaMath.normalize(state.heading))) < ALIGN_TOLERANCE_DEG
    } ?: false

    Column(
        modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Text(stringResource(R.string.qibla_title), style = MaterialTheme.typography.headlineMedium)

        if (!state.sensorAvailable) {
            Text(
                stringResource(R.string.qibla_no_sensor),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
        }

        CompassDial(
            qiblaBearing = state.qiblaBearing,
            headingProvider = { animatedHeading }, // read inside graphicsLayer: no recomposition per frame
            aligned = aligned,
            modifier = Modifier.size(300.dp)
        )

        Text(
            text = when {
                state.qiblaBearing == null -> stringResource(R.string.qibla_finding)
                aligned -> stringResource(R.string.qibla_aligned)
                else -> stringResource(R.string.qibla_turn)
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (aligned) AlignedGreen else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        state.qiblaBearing?.let { bearing ->
            val km = state.distanceKm?.roundToInt()
            Text(
                if (km != null) stringResource(R.string.qibla_bearing_distance, bearing.roundToInt(), km)
                else stringResource(R.string.qibla_bearing, bearing.roundToInt())
            )
        }
        if (state.hasHeading) {
            Text(
                stringResource(R.string.qibla_heading, QiblaMath.normalize(state.heading).roundToInt()),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (state.hasHeading && state.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            Text(
                stringResource(R.string.qibla_calibrate),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (state.usingFallbackLocation) {
            Text(
                stringResource(R.string.qibla_fallback_location),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (!hasLocationPermission) {
            OutlinedButton(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                Text(stringResource(R.string.btn_use_location))
            }
        }
        Text(
            stringResource(R.string.qibla_hold_flat),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val ALIGN_TOLERANCE_DEG = 3f

/**
 * The whole dial (ticks, N/E/S/W, Kaaba marker) is ONE layer rotated by -heading, so north and
 * the Kaaba stay fixed in the real world while the phone turns. The pointer at the top is fixed
 * to the phone ("where I'm facing").
 */
@Composable
private fun CompassDial(
    qiblaBearing: Float?,
    headingProvider: () -> Float,
    aligned: Boolean,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(18.dp)
                .graphicsLayer { rotationZ = -headingProvider() }
        ) {
            val c = center
            val r = size.minDimension / 2f
            val edge = c.y - r

            drawCircle(DialFill, radius = r)
            drawCircle(if (aligned) AlignedGreen else Gold, radius = r, style = Stroke(width = 4.dp.toPx()))

            // Tick marks every 5°, longer every 30°
            for (i in 0 until 72) {
                val major = i % 6 == 0
                val length = if (major) 16.dp.toPx() else 8.dp.toPx()
                rotate(degrees = i * 5f, pivot = c) {
                    drawLine(
                        color = Cream.copy(alpha = if (major) 0.95f else 0.5f),
                        start = Offset(c.x, edge + 6.dp.toPx()),
                        end = Offset(c.x, edge + 6.dp.toPx() + length),
                        strokeWidth = if (major) 3.dp.toPx() else 1.5.dp.toPx()
                    )
                }
            }

            // Cardinal letters, oriented radially
            listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (label, angle) ->
                val layout = measurer.measure(
                    label,
                    TextStyle(
                        color = if (label == "N") NorthRed else Cream,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                rotate(degrees = angle, pivot = c) {
                    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, edge + 30.dp.toPx()))
                }
            }

            // Qibla marker: a line from the centre plus the Kaaba emoji at the rim
            qiblaBearing?.let { bearing ->
                rotate(degrees = bearing, pivot = c) {
                    val tip = edge + r * 0.38f
                    drawLine(
                        color = Gold,
                        start = c,
                        end = Offset(c.x, tip),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    val layout = measurer.measure("🕋", TextStyle(fontSize = 28.sp))
                    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, tip - layout.size.height))
                }
            }

            drawCircle(Gold, radius = 6.dp.toPx(), center = c)
        }

        // Fixed "facing" marker at the top of the phone
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width / 2f
            val path = Path().apply {
                moveTo(x, 20.dp.toPx())
                lineTo(x - 11.dp.toPx(), 0f)
                lineTo(x + 11.dp.toPx(), 0f)
                close()
            }
            drawPath(path, color = if (aligned) AlignedGreen else Gold)
        }
    }
}
