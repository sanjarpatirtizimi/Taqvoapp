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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import uz.namozvaqti.app.ui.components.InfoBanner
import uz.namozvaqti.app.ui.theme.extras
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ALIGN_TOLERANCE_DEG = 3f
private val NorthAccent = Color(0xFFE8776B)

@Composable
fun QiblaScreen(modifier: Modifier = Modifier, vm: QiblaViewModel = viewModel()) {
    val ctx = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()

    fun locationGranted() = ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    var hasLocationPermission by remember { mutableStateOf(locationGranted()) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasLocationPermission = locationGranted()
        vm.refreshLocation()
    }

    // Second smoothing layer: the spring eases between filtered sensor samples at display refresh rate.
    val animatedHeading by animateFloatAsState(
        targetValue = state.heading,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "heading"
    )

    val aligned = state.qiblaBearing?.let {
        abs(QiblaMath.shortestDelta(it - QiblaMath.normalize(state.heading))) < ALIGN_TOLERANCE_DEG
    } ?: false

    val showCalibrate = state.sensorAvailable && state.hasHeading &&
        state.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.qibla_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth()
        )

        // ---- Gentle, non-alarming notices (no red error text) ----
        if (!state.sensorAvailable) {
            InfoBanner(stringResource(R.string.qibla_info_no_sensor))
        }
        if (showCalibrate) {
            InfoBanner(stringResource(R.string.qibla_info_calibrate))
        }
        if (state.usingFallbackLocation) {
            InfoBanner(
                text = stringResource(R.string.qibla_fallback_location),
                actionLabel = if (!hasLocationPermission) stringResource(R.string.action_allow) else null,
                onAction = if (!hasLocationPermission) {
                    { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                } else null
            )
        }

        CompassDial(
            qiblaBearing = state.qiblaBearing,
            headingProvider = { animatedHeading }, // read at draw/graphics time: no recomposition per frame
            aligned = aligned,
            modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f)
        )

        // ---- Status ----
        Text(
            text = when {
                state.qiblaBearing == null -> stringResource(R.string.qibla_finding)
                aligned -> stringResource(R.string.qibla_status_aligned)
                else -> stringResource(R.string.qibla_status_turn)
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (aligned) MaterialTheme.extras.success else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        state.qiblaBearing?.let { bearing ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoChip(stringResource(R.string.qibla_degrees_from_north, bearing.roundToInt()))
                state.distanceKm?.roundToInt()?.let { km ->
                    InfoChip(stringResource(R.string.qibla_distance_km, km))
                }
            }
        }

        Text(
            stringResource(R.string.qibla_hold_flat),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * Two layers:
 *  1. The dial (face, ticks, N/E/S/W) is drawn ONCE and rotated by -heading with graphicsLayer
 *     (GPU only, no redraw), so north stays fixed in the real world while the phone turns.
 *  2. The overlay draws the Qibla needle and the upright Kaaba icon at angle (bearing - heading),
 *     plus the fixed "facing" marker at the top of the phone.
 */
@Composable
private fun CompassDial(
    qiblaBearing: Float?,
    headingProvider: () -> Float,
    aligned: Boolean,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val extras = MaterialTheme.extras
    val rim = if (aligned) extras.success else extras.gold

    Box(modifier) {
        // ---------- Layer 1: rotating dial ----------
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(DIAL_PADDING)
                .graphicsLayer { rotationZ = -headingProvider() }
        ) {
            val c = center
            val r = size.minDimension / 2f
            val edge = c.y - r

            // Face
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(extras.heroStart, extras.heroEnd),
                    center = c,
                    radius = r
                ),
                radius = r
            )
            // Soft inner rings
            drawCircle(extras.onHero.copy(alpha = 0.08f), radius = r * 0.36f, style = Stroke(1.5.dp.toPx()))
            drawCircle(extras.onHero.copy(alpha = 0.06f), radius = r * 0.68f, style = Stroke(1.dp.toPx()))
            // Rim
            drawCircle(rim.copy(alpha = if (aligned) 0.35f else 0.18f), radius = r + 5.dp.toPx(), style = Stroke(10.dp.toPx()))
            drawCircle(rim, radius = r, style = Stroke(width = 3.dp.toPx()))

            // Ticks every 5 degrees, longer every 30
            for (i in 0 until 72) {
                val major = i % 6 == 0
                val length = if (major) 14.dp.toPx() else 7.dp.toPx()
                rotate(degrees = i * 5f, pivot = c) {
                    drawLine(
                        color = extras.onHero.copy(alpha = if (major) 0.9f else 0.4f),
                        start = Offset(c.x, edge + 6.dp.toPx()),
                        end = Offset(c.x, edge + 6.dp.toPx() + length),
                        strokeWidth = if (major) 2.5.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Cardinal letters, oriented radially
            listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (label, angle) ->
                val layout = measurer.measure(
                    label,
                    TextStyle(
                        color = if (label == "N") NorthAccent else extras.onHero,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                rotate(degrees = angle, pivot = c) {
                    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, edge + 26.dp.toPx()))
                }
            }
        }

        // ---------- Layer 2: Qibla needle + Kaaba (redrawn per animation frame, cheap) ----------
        Canvas(
            Modifier
                .fillMaxSize()
                .padding(DIAL_PADDING)
        ) {
            val c = center
            val r = size.minDimension / 2f

            if (qiblaBearing != null) {
                val theta = Math.toRadians((qiblaBearing - headingProvider()).toDouble())
                val kaabaRadius = r * 0.56f
                val kaabaCenter = Offset(
                    c.x + (kaabaRadius * sin(theta)).toFloat(),
                    c.y - (kaabaRadius * cos(theta)).toFloat()
                )
                // Needle from the hub toward the Kaaba
                drawLine(
                    color = extras.gold,
                    start = c,
                    end = kaabaCenter,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Upright Kaaba (not rotated, so it always reads correctly)
                drawKaaba(kaabaCenter, size = 46.dp.toPx(), gold = extras.gold)
            }

            // Hub
            drawCircle(extras.gold, radius = 7.dp.toPx(), center = c)
            drawCircle(extras.heroEnd, radius = 3.dp.toPx(), center = c)
        }

        // ---------- Fixed "facing" marker at the top of the phone ----------
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width / 2f
            val path = Path().apply {
                moveTo(x, 24.dp.toPx())
                lineTo(x - 11.dp.toPx(), 2.dp.toPx())
                lineTo(x + 11.dp.toPx(), 2.dp.toPx())
                close()
            }
            drawPath(path, color = rim)
        }
    }
}

private val DIAL_PADDING = 14.dp
