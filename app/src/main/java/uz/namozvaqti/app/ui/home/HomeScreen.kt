package uz.namozvaqti.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.namozvaqti.app.R
import uz.namozvaqti.app.data.local.PrayerDayEntity
import uz.namozvaqti.app.domain.Prayer
import uz.namozvaqti.app.ui.theme.extras
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------------------------
// Pure helpers (no Android): which prayer is next / currently active.
// ---------------------------------------------------------------------------------------------

private data class NextPrayer(val prayer: Prayer, val timeMs: Long, val zone: ZoneId)

/** First of the five prayers whose time is still in the future (rolls over to tomorrow's Fajr). */
private fun findNext(days: List<PrayerDayEntity>, now: Long): NextPrayer? {
    for (day in days.sortedBy { it.date }) {
        for (prayer in Prayer.entries) {
            val t = day.timeOf(prayer)
            if (t > now) return NextPrayer(prayer, t, ZoneId.of(day.timezone))
        }
    }
    return null
}

private fun findToday(days: List<PrayerDayEntity>): PrayerDayEntity? =
    days.firstOrNull { it.date == LocalDate.now(ZoneId.of(it.timezone)).toString() }

/**
 * The prayer whose window we are in: the last one that has started. Fajr ends at sunrise, so
 * between sunrise and Dhuhr nothing is "active".
 */
private fun findCurrent(today: PrayerDayEntity, now: Long): Prayer? {
    val started = Prayer.entries.lastOrNull { today.timeOf(it) <= now } ?: return null
    return if (started == Prayer.FAJR && now >= today.sunrise) null else started
}

private fun formatCountdown(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun formatClock(ms: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(modifier: Modifier = Modifier, vm: HomeViewModel = viewModel()) {
    val ctx = LocalContext.current
    val days by vm.days.collectAsStateWithLifecycle()
    val now by vm.nowMs.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val prompted by vm.locationPrompted.collectAsStateWithLifecycle()

    // Ask for location once on first launch, so times are not silently based on the Tashkent fallback.
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refresh()
    }
    LaunchedEffect(prompted) {
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (prompted == false) {
            vm.markLocationPrompted()
            if (!granted) locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val list = days
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (list == null) {
            LoadingState()
        } else if (list.isEmpty()) {
            if (refreshing) LoadingState() else EmptyState(onRetry = vm::refresh)
        } else {
            val next = findNext(list, now)
            val today = findToday(list)
            if (next != null) NextPrayerHero(next, now)
            if (today != null) {
                TimetableCard(
                    day = today,
                    current = findCurrent(today, now),
                    next = next?.prayer,
                    nextTimeMs = next?.timeMs
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.prayer_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.home_no_data_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(R.string.home_no_data_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
    }
}

// ---------------------------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------------------------

@StringRes
private fun nameOf(prayer: Prayer): Int = prayer.nameRes

@Composable
private fun NextPrayerHero(next: NextPrayer, now: Long) {
    val extras = MaterialTheme.extras
    val shape = RoundedCornerShape(32.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(extras.heroStart, extras.heroEnd)))
    ) {
        // Soft geometric ornament in the corner: concentric rings.
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width * 0.92f, size.height * 0.08f)
            for (i in 1..5) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.07f),
                    radius = i * 38.dp.toPx(),
                    center = c,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(R.string.home_next_prayer).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = extras.onHero.copy(alpha = 0.75f),
                letterSpacing = 2.sp
            )
            Text(
                stringResource(nameOf(next.prayer)),
                style = MaterialTheme.typography.headlineLarge,
                color = extras.gold,
                fontWeight = FontWeight.Bold
            )
            Text(
                formatCountdown(next.timeMs - now),
                style = TextStyle(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    fontFeatureSettings = "tnum" // equal-width digits: the timer does not jitter
                ),
                color = extras.onHero
            )
            Text(
                stringResource(R.string.home_time_left),
                style = MaterialTheme.typography.bodyMedium,
                color = extras.onHero.copy(alpha = 0.75f)
            )
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.14f),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text(
                    formatClock(next.timeMs, next.zone),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = extras.onHero
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Timetable
// ---------------------------------------------------------------------------------------------

private data class Row3(@StringRes val nameRes: Int, val timeMs: Long, val prayer: Prayer?)

@Composable
private fun TimetableCard(
    day: PrayerDayEntity,
    current: Prayer?,
    next: Prayer?,
    nextTimeMs: Long?
) {
    val zone = ZoneId.of(day.timezone)
    val locale = LocalConfiguration.current.locales[0]
    val dateText = remember(day.date, locale) {
        runCatching {
            LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
        }.getOrDefault(day.date)
    }

    val rows = listOf(
        Row3(R.string.prayer_fajr, day.fajr, Prayer.FAJR),
        Row3(R.string.prayer_sunrise, day.sunrise, null),
        Row3(R.string.prayer_dhuhr, day.dhuhr, Prayer.DHUHR),
        Row3(R.string.prayer_asr, day.asr, Prayer.ASR),
        Row3(R.string.prayer_maghrib, day.maghrib, Prayer.MAGHRIB),
        Row3(R.string.prayer_isha, day.isha, Prayer.ISHA)
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.home_timetable_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { row ->
                    val isCurrent = row.prayer != null && row.prayer == current
                    // "Next" is matched by time so Sunrise is never mislabeled and tomorrow's Fajr is not marked today.
                    val isNext = !isCurrent && row.prayer != null && row.prayer == next && row.timeMs == nextTimeMs
                    TimetableRow(
                        name = stringResource(row.nameRes),
                        time = formatClock(row.timeMs, zone),
                        isCurrent = isCurrent,
                        isNext = isNext,
                        dimmed = row.prayer == null
                    )
                }
            }
        }
    }
}

@Composable
private fun TimetableRow(name: String, time: String, isCurrent: Boolean, isNext: Boolean, dimmed: Boolean) {
    val bg = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left marker: gold dot for the active prayer, outline dot for the rest.
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (isCurrent) MaterialTheme.extras.gold
                    else MaterialTheme.colorScheme.outlineVariant
                )
        )
        Text(
            name,
            Modifier.weight(1f).padding(start = 14.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
        if (isCurrent) Badge(stringResource(R.string.home_now_badge), filled = true)
        if (isNext) Badge(stringResource(R.string.home_next_badge), filled = false)
        Text(
            time,
            Modifier.padding(start = 12.dp),
            style = TextStyle(fontSize = 18.sp, fontFeatureSettings = "tnum"),
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}

@Composable
private fun Badge(text: String, filled: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
