package com.dasein.poryadok.ui.training

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.dasein.poryadok.ui.common.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dasein.poryadok.data.ExKind
import com.dasein.poryadok.data.Exercise
import com.dasein.poryadok.data.SetLog
import com.dasein.poryadok.ui.common.Glyph
import com.dasein.poryadok.ui.common.Glyphs
import com.dasein.poryadok.ui.common.Media
import com.dasein.poryadok.ui.common.MediaView
import com.dasein.poryadok.ui.common.Pill
import com.dasein.poryadok.ui.common.btnLabel
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun favPrefs(ctx: Context) = ctx.getSharedPreferences("ui_state", Context.MODE_PRIVATE)

/**
 * Упражнения лентой «как шортсы»: крупная карточка на каждое упражнение, видео или GIF обрезаны по карточке
 * и крутятся по кругу без звука; поверх — мышцы, снаряд, тип, «Смотреть», избранное и число подходов.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseShorts(exercises: List<Exercise>, sets: List<SetLog>, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    val extra = LocalExtra.current
    var q by remember { mutableStateOf("") }
    var muscles by remember { mutableStateOf(emptySet<String>()) }
    var favOnly by remember { mutableStateOf(false) }
    var favVer by remember { mutableIntStateOf(0) }
    fun fav(id: Long): Boolean { favVer; return favPrefs(ctx).getBoolean("ex_fav_$id", false) }
    val archivedIds = com.dasein.poryadok.data.ExercisePrefs.archived.value
    val onlyMine = com.dasein.poryadok.data.ExercisePrefs.onlyMine.value
    val gear = com.dasein.poryadok.data.ExercisePrefs.myGear.value
    var gearEdit by remember { mutableStateOf(false) }
    var archiveOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Exercise?>(null) }
    val active = exercises.filter { it.id !in archivedIds }
    // «Мой инвентарь»: только упражнения с тем, что есть дома, и со своим весом.
    val fitting = if (onlyMine) active.filter { com.dasein.poryadok.logic.HomeGear.fits(it.equipment, gear) } else active
    val hiddenByGear = active.size - fitting.size
    val list = fitting.filter {
        (muscles.isEmpty() || it.muscle in muscles) && (!favOnly || fav(it.id)) &&
            (q.isBlank() || it.name.contains(q, true) || it.equipment.contains(q, true) || it.muscle.contains(q, true))
    }
    val archivedList = exercises.filter { it.id in archivedIds }
    if (gearEdit) GearSetDialog { gearEdit = false }
    deleting?.let { e ->
        com.dasein.poryadok.ui.common.ConfirmDialog(
            "Удалить упражнение навсегда?", "«${e.name}» исчезнет из библиотеки и программ. Прошлые подходы останутся в истории.",
            onDismiss = { deleting = null },
        ) { com.dasein.poryadok.ui.common.io { com.dasein.poryadok.data.ExercisePrefs.deleteForever(e) } }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(q, { q = it }, placeholder = { Text(btnLabel("Поиск упражнения")) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            Column(Modifier.padding(top = 8.dp)) {
                MuscleFilter(muscles, fitting, { muscles = it }) {
                    Pill("Все", muscles.isEmpty() && !favOnly) { muscles = emptySet(); favOnly = false }
                    Pill("Избранное", favOnly, glyph = Glyphs.FAV_ON) { favOnly = !favOnly }
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(if (onlyMine) "Мой инвентарь ✓" else "Мой инвентарь", onlyMine) { com.dasein.poryadok.data.ExercisePrefs.setOnlyMine(!onlyMine) }
                    Text(
                        btnLabel("Изменить"), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp)).clickable { gearEdit = true }.padding(8.dp),
                    )
                }
                if (onlyMine && hiddenByGear > 0) Text(
                    "Скрыто $hiddenByGear упр. с другим инвентарём", fontSize = 12.sp, color = extra.dim, modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                )
            }
        }
        if (exercises.isEmpty()) item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(extra.card).clickable(onClick = onAdd).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Glyph("sport/15", 56.dp, badge = false)
                Text(btnLabel("Своих упражнений пока нет"), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Нажмите «+ Упражнение», дайте название, выберите мышцы и снаряд и добавьте видео или GIF — здесь оно будет крутиться по кругу без звука.",
                    fontSize = 13.sp, color = extra.dim, modifier = Modifier.padding(top = 4.dp),
                )
            }
        } else if (list.isEmpty()) item { Text(btnLabel("Ничего не найдено"), color = extra.dim, modifier = Modifier.padding(8.dp)) }
        itemsIndexed(list, key = { _, e -> e.id }) { i, e ->
            ShortCard(
                e, i + 1, list.size, sets.count { it.exerciseId == e.id && it.done }, fav(e.id),
                onFav = { favPrefs(ctx).edit().putBoolean("ex_fav_${e.id}", !fav(e.id)).apply(); favVer++ },
                onArchive = { com.dasein.poryadok.data.ExercisePrefs.archive(e.id) },
                onOpen = { onOpen(e.id) },
            )
        }
        // Архив: упражнения, убранные значком коробки. Отсюда — вернуть или удалить насовсем (даже встроенные).
        if (archivedList.isNotEmpty()) item {
            com.dasein.poryadok.ui.common.TextButton(onClick = { archiveOpen = !archiveOpen }, Modifier.fillMaxWidth()) {
                Text((if (archiveOpen) "Скрыть архив упражнений" else "Архив упражнений") + " (${archivedList.size})")
            }
        }
        if (archiveOpen) itemsIndexed(archivedList, key = { _, e -> "a" + e.id }) { _, e ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(extra.cardHigh).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(e.glyph, 24.dp)
                    Text("  " + e.name, color = extra.dim, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row {
                    com.dasein.poryadok.ui.common.TextButton(onClick = { com.dasein.poryadok.data.ExercisePrefs.restore(e.id) }) { Text(btnLabel("Вернуть")) }
                    com.dasein.poryadok.ui.common.TextButton(onClick = { deleting = e }) { Text(btnLabel("Удалить навсегда"), color = extra.danger) }
                }
            }
        }
    }
}

/** «Мой инвентарь»: что есть дома. Упражнения с другим инвентарём скрываются (кроме упражнений со своим весом). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GearSetDialog(onDismiss: () -> Unit) {
    var pick by remember { mutableStateOf(com.dasein.poryadok.data.ExercisePrefs.myGear.value) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(btnLabel("Мой инвентарь")) },
        text = {
            Column {
                Text("Отметьте, что есть дома. Упражнения со своим весом показываются всегда.", fontSize = 13.sp, color = LocalExtra.current.dim)
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.dasein.poryadok.data.ExercisePrefs.ALL_GEAR.forEach { g -> Pill(g, g in pick) { pick = if (g in pick) pick - g else pick + g } }
                }
            }
        },
        confirmButton = { com.dasein.poryadok.ui.common.TextButton(onClick = { com.dasein.poryadok.data.ExercisePrefs.setGear(pick); onDismiss() }) { Text(btnLabel("Сохранить")) } },
        dismissButton = { com.dasein.poryadok.ui.common.TextButton(onClick = onDismiss) { Text(btnLabel("Отмена")) } },
    )
}

private val SHADOW = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), blurRadius = 8f))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortCard(e: Exercise, index: Int, total: Int, doneSets: Int, fav: Boolean, onFav: () -> Unit, onArchive: () -> Unit, onOpen: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val media = e.media.lines().firstOrNull { it.isNotBlank() && java.io.File(it).exists() }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.45f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF1B1813)).clickable(onClick = onOpen),
    ) {
        when {
            media == null -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = .35f), Color(0xFF1B1813)))), contentAlignment = Alignment.Center) {
                Glyph(e.glyph, 96.dp, badge = false)
            }
            Media.isVideo(media) -> LoopVideo(media, Modifier.fillMaxSize())
            else -> MediaView(media, Modifier.fillMaxSize(), crop = true)
        }
        // Затемнение снизу и сверху — чтобы текст читался на любом кадре.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .45f), .3f to Color.Transparent, .55f to Color.Transparent, 1f to Color.Black.copy(alpha = .8f))))

        if (e.muscle.isNotBlank()) Row(
            Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = .45f))
                .border(1.dp, Color.White.copy(alpha = .15f), RoundedCornerShape(18.dp)).padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(Muscles.glyphOf(LocalContext.current, e.muscle) ?: e.glyph, 26.dp, badge = false)
            Text("  ${e.muscle}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Text("$index / $total", color = Color.White, fontSize = 13.sp, style = SHADOW, modifier = Modifier.align(Alignment.TopEnd).padding(14.dp))

        // Правая колонка: избранное и сколько подходов уже сделано.
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onFav), contentAlignment = Alignment.Center) {
                Glyph(if (fav) Glyphs.FAV_ON else Glyphs.FAV_OFF, 34.dp, badge = false)
            }
            Text(if (fav) "в избранном" else "избранное", color = Color.White, fontSize = 10.sp, style = SHADOW)
            // Под сердечком — архив: упражнение уходит из списка, вернуть или удалить можно в «Архиве упражнений» внизу.
            Box(Modifier.padding(top = 8.dp).size(44.dp).clip(CircleShape).clickable(onClick = onArchive), contentAlignment = Alignment.Center) {
                com.dasein.poryadok.ui.common.AppIcon(com.dasein.poryadok.ui.common.Ic.archive, 30.dp, badge = false)
            }
            Text(btnLabel("в архив"), color = Color.White, fontSize = 10.sp, style = SHADOW)
            if (doneSets > 0) {
                Text("$doneSets", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, style = SHADOW, modifier = Modifier.padding(top = 10.dp))
                Text(btnLabel("подходов"), color = Color.White, fontSize = 10.sp, style = SHADOW)
            }
        }

        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 70.dp, bottom = 14.dp)) {
            Text(e.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = SHADOW)
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (e.muscle.isNotBlank()) Tag(e.muscle, filled = true)
                if (e.equipment.isNotBlank()) Tag(e.equipment)
                Tag(ExKind.names[e.kind])
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(accent).clickable(onClick = onOpen).padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("▶", color = Color(0xFF1B1813), fontSize = 14.sp)
                    Text(btnLabel("  Смотреть"), color = Color(0xFF1B1813), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                if (media != null && Media.isVideo(media)) {
                    val dur by produceState<Long?>(null, media) { value = withContext(Dispatchers.IO) { durationMs(media) } }
                    dur?.let { Text("  ${mmss(it / 1000)}", color = Color.White, fontSize = 14.sp, style = SHADOW) }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, filled: Boolean = false) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text, fontSize = 12.sp, color = if (filled) Color(0xFF1B1813) else Color.White,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (filled) accent else Color.Black.copy(alpha = .45f))
            .border(1.dp, if (filled) Color.Transparent else Color.White.copy(alpha = .2f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun mmss(sec: Long) = "%d:%02d".format(sec / 60, sec % 60)

private val durations = HashMap<String, Long>()

private fun durationMs(path: String): Long? = durations[path] ?: runCatching {
    MediaMetadataRetriever().run {
        try { setDataSource(path); extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() } finally { release() }
    }
}.getOrNull()?.also { durations[path] = it }

/** Видео без звука по кругу, обрезанное по рамке (как обложка шортса). Проигрыватель освобождается, когда карточка уходит с экрана. */
@Composable
fun LoopVideo(path: String, modifier: Modifier = Modifier) = androidx.compose.runtime.key(path) {
    AndroidView(
        factory = { c -> LoopTexture(c, path) },
        onRelease = { it.release() },
        modifier = modifier,
    )
}

private class LoopTexture(c: Context, private val path: String) : TextureView(c), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var vw = 0
    private var vh = 0

    init { surfaceTextureListener = this }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        val s = Surface(st).also { surface = it }
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(path)
                setSurface(s)
                isLooping = true
                setVolume(0f, 0f)
                setOnVideoSizeChangedListener { _, x, y -> vw = x; vh = y; crop() }
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.getOrNull()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = crop()
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { release(); return true }
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    /** Центр-кроп: кадр заполняет всю карточку, лишнее по краям обрезается. */
    private fun crop() {
        if (vw == 0 || vh == 0 || width == 0 || height == 0) return
        val sx = width.toFloat() / vw
        val sy = height.toFloat() / vh
        val s = maxOf(sx, sy)
        val m = Matrix()
        m.setScale(vw * s / width, vh * s / height, width / 2f, height / 2f)
        setTransform(m)
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        runCatching { surface?.release() }
        surface = null
    }
}
