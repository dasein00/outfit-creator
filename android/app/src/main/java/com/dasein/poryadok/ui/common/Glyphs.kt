@file:OptIn(ExperimentalLayoutApi::class)

package com.dasein.poryadok.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette

/**
 * Фирменные иконки DASEIN вместо эмодзи.
 * Ключ иконки — строка, которая хранится в тех же полях, где раньше был эмодзи:
 *  - «sport/15», «sleep/00» — наборы разделов из assets/glyphs;
 *  - «dish/healthy/24» — иллюстрации блюд из assets/dishes;
 *  - «ui:card» — базовый набор интерфейса (drawable).
 * Старые эмодзи из уже сохранённых данных показываются подходящей иконкой.
 */
object Glyphs {
    /** Наборы для выбора пользователем: заголовок → папка и номера. */
    val SETS = listOf(
        "Спорт" to "sport", "Тренировки" to "train", "Сон" to "sleep", "Питание" to "food",
        "Календарь" to "cal", "Праздники" to "fest", "Кино и книги" to "habit", "Медиа" to "books",
        "Цели" to "goals", "День" to "daily", "Жизнь" to "life", "Ночь" to "night",
        "Кино и стриминг" to "cinema", "Чтение" to "reading", "Жизнь и фитнес" to "lifefit", "Минимализм" to "minimal",
        "Аналитика" to "analytics", "Топы" to "tops", "Давление" to "pressure", "Репетитор" to "tutor",
    )
    val DISH_SETS = listOf(
        "Курица" to "chicken", "Салаты и продукты" to "salads", "Паста и вок" to "pasta", "Блюда мира" to "world",
        "Домашнее" to "own", "Полезное" to "healthy", "Завтраки" to "breakfast", "Основные блюда" to "dishes1", "Ещё блюда" to "dishes2",
        "Русская кухня" to "russian", "Кавказская кухня" to "caucasus", "Разное" to "mixed", "Выпечка" to "bakery",
        "Десерты" to "desserts", "Фастфуд" to "fastfood",
    ) + listOf(
        "Картофель и мясо" to "potmeat", "Рыба и морепродукты" to "seafood", "Тесто, яйца, лепёшки" to "dough",
        "Итальянская паста" to "pasta2", "Блюда из курицы" to "chicken2", "Салат-бар и кухня" to "saladbar", "Протеин и кухня" to "protein",
        "Бистро и десерты" to "bistro", "Блюда с грибами" to "mushroom",
    )
    /** Наборы из присланных листов: показываются в любом выборе иконки, не только для блюд. */
    val SHARED_DISH_SETS = setOf("potmeat", "seafood", "dough", "pasta2", "chicken2", "saladbar", "protein", "bistro", "mushroom")
    val UI = listOf(
        "v_film", "v_pot", "v_pulse", "v_plan", "v_bookopen", "v_steps", "v_weight", "v_mountain", "v_spark", "v_chart",
        "home", "calendar", "check", "stats", "pie", "grid", "search", "settings", "flame", "target", "timer", "salad",
        "scale", "dumbbell", "smile", "moon", "drop", "notebook", "trophy", "hanger", "wheel", "review", "bell", "sliders",
        "wallet", "coins", "bank", "card", "receipt", "leaves", "heart", "people", "cart", "globe", "pin", "map", "camera",
        "image", "document", "folder", "sun", "rain", "thermo", "book", "bulb", "cap", "gift", "trash",
    )

    // Часто используемые иконки по смыслу.
    const val TARGET = "sport/21"
    const val RUN = "sport/01"
    const val PLANE = "cal/35"
    const val WORK = "cal/29"
    const val HOME = "ui:home"
    const val FOLDER = "ui:folder"
    const val CHECK = "ui:check"
    const val TROPHY = "ui:trophy"
    const val CARD = "ui:card"
    const val CASH = "ui:coins"
    const val BANK = "ui:bank"
    const val NOTEBOOK = "ui:notebook"
    const val TRANSFER = "sport/34"
    const val SCALE = "sport/11"
    const val BED = "sleep/01"
    const val MOON = "sleep/00"
    const val ALARM = "sleep/02"
    const val SUNRISE = "sleep/18"
    const val PHONE_OFF = "sleep/15"
    const val WATER = "ui:drop"
    const val CAMERA = "fest/23"
    const val INBOX = "cal/20"
    const val EVENT = "cal/00"
    const val REMINDER = "cal/23"
    const val REPEAT = "cal/44"

    val WORKOUTS = linkedMapOf(
        "Силовая" to "sport/15", "Бег" to "sport/01", "Вело" to "sport/08", "Плавание" to "sport/09", "Йога" to "sport/04",
        "Растяжка" to "sport/14", "HIIT" to "sport/22", "Ходьба" to "sport/06", "Турник" to "sport/13", "Скакалка" to "sport/03",
        "Беговая дорожка" to "sport/07", "Велотренажёр" to "sport/02", "Поход" to "sport/10", "Игры" to "sport/27", "Другое" to "sport/00",
    )

    val MOOD_TAGS = linkedMapOf(
        "Работа" to "cal/29", "Семья" to "cal/33", "Друзья" to "train/37", "Свидание" to "fest/09", "Спорт" to "sport/00",
        "Прогулка" to "sport/06", "Выспался" to "sleep/01", "Не выспался" to "sleep/02", "Вкусно поел" to "fest/29",
        "Учёба" to "fest/15", "Хобби" to "habit/14", "Отдых" to "habit/07", "Поездка" to "cal/35", "Покупки" to "fest/20",
        "Болезнь" to "sport/32", "Стресс" to "sleep/19", "Погода" to "cal/43",
    )

    /** Эмодзи прежних версий → иконка. */
    private val LEGACY = mapOf(
        "🛒" to "food/24", "☕" to "fest/29", "🚕" to "ui:map", "🏠" to "ui:home", "📱" to "ui:globe", "💊" to "sport/18",
        "👕" to "ui:hanger", "🎬" to "habit/01", "🎁" to "fest/01", "📚" to "habit/11", "🏋️" to "sport/15", "🏋" to "sport/15",
        "💅" to "fest/12", "✈️" to "cal/35", "✈" to "cal/35", "🔁" to "sport/34", "📦" to "ui:grid", "💼" to "cal/29",
        "💻" to "ui:card", "💸" to "ui:receipt", "💰" to "ui:wallet", "💳" to "ui:card", "💵" to "ui:coins", "📒" to "ui:notebook",
        "🏦" to "ui:bank", "🪙" to "ui:coins", "📈" to "ui:stats", "🐷" to "ui:wallet", "💶" to "ui:coins", "💲" to "ui:coins",
        "📁" to "ui:folder", "🎯" to "sport/21", "✅" to "ui:check", "🏆" to "ui:trophy", "💪" to "sport/12", "🏃" to "sport/01",
        "🧘" to "sport/04", "💧" to "ui:drop", "🥗" to "ui:salad", "😴" to "sleep/00", "🚭" to "sleep/15", "🦷" to "ui:heart",
        "🧹" to "ui:home", "🌱" to "ui:leaves", "✍️" to "ui:notebook", "🎸" to "habit/14", "🇬🇧" to "fest/15", "🧠" to "sleep/19",
        "📖" to "habit/12", "🙏" to "ui:notebook", "☀️" to "ui:sun", "🚶" to "sport/06", "🚴" to "sport/08", "🏊" to "sport/09",
        "🍎" to "food/11", "👨‍👩‍👧" to "cal/33", "❤️" to "ui:heart", "🎨" to "ui:image", "🎮" to "habit/20", "🐶" to "ui:heart",
        "🌿" to "ui:leaves", "⭐" to "habit/22", "🔥" to "ui:flame", "📝" to "ui:notebook", "🎵" to "habit/14", "📷" to "fest/23",
        "🧺" to "food/24", "🤸" to "sport/14", "📵" to "sleep/15", "🍽" to "fest/29", "📺" to "habit/03", "🌍" to "habit/27",
        "✨" to "fest/32", "🎉" to "fest/03",
    )

    fun normalize(value: String): String {
        val v = value.trim()
        return when {
            v.isEmpty() -> "ui:grid"
            v.startsWith("ui:") || '/' in v -> v
            else -> LEGACY[v] ?: LEGACY[v.replace("️", "")] ?: "ui:grid"
        }
    }

    fun isKey(value: String) = value.startsWith("ui:") || '/' in value

    /** «🏋️ Силовая» → «Силовая»: убирает эмодзи в начале старых подписей. */
    fun stripEmoji(label: String): String {
        val t = label.trim()
        val i = t.indexOfFirst { it.isLetterOrDigit() }
        return if (i <= 0) t else t.substring(i)
    }

    fun workout(type: String): String = WORKOUTS[stripEmoji(type)] ?: "sport/00"
    fun moodTag(tag: String): String = MOOD_TAGS[stripEmoji(tag)] ?: "ui:smile"

    fun assetPath(key: String): String = if (key.startsWith("dish/")) "dishes/${key.removePrefix("dish/")}.webp" else "glyphs/$key.webp"

    private val cache = LruCache<String, ImageBitmap>(400)

    fun load(ctx: Context, key: String): ImageBitmap? {
        cache.get(key)?.let { return it }
        val bmp = runCatching { ctx.assets.open(assetPath(key)).use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return null
        return bmp.asImageBitmap().also { cache.put(key, it) }
    }

    fun list(ctx: Context, folder: String): List<String> =
        runCatching { ctx.assets.list(folder)?.sorted().orEmpty() }.getOrDefault(emptyList()).map { it.removeSuffix(".webp") }

    fun keysOf(ctx: Context, set: String): List<String> = list(ctx, "glyphs/$set").map { "$set/$it" }
    fun dishKeysOf(ctx: Context, set: String): List<String> = list(ctx, "dishes/$set").map { "dish/$set/$it" }
}

/**
 * Иконка по ключу или старому эмодзи. В светлой теме кремовые детали плохо видны,
 * поэтому иконки разделов лежат на тёмной плашке (как в нижнем меню); иллюстрации блюд — без плашки.
 */
@Composable
fun Glyph(value: String, size: Dp = 24.dp, modifier: Modifier = Modifier, badge: Boolean? = null, dimmed: Boolean = false) {
    val key = Glyphs.normalize(value)
    if (key.startsWith("ui:")) {
        val res = Ic.byName(key.removePrefix("ui:"))
        AppIcon(res, size, modifier, badge = badge ?: !LocalExtra.current.dark, dimmed = dimmed)
        return
    }
    val ctx = LocalContext.current
    if (key.startsWith("/")) {
        // Своя картинка: круглое фото вместо плашки.
        val photo by rememberImage(key, 256)
        Box(modifier.size(size * 1.45f).clip(CircleShape).background(Palette.Ink2).alpha(if (dimmed) .5f else 1f)) {
            photo?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        return
    }
    val img = remember(key) { Glyphs.load(ctx, key) }
    val useBadge = (badge ?: !LocalExtra.current.dark) && !key.startsWith("dish/")
    val a = if (dimmed) .5f else 1f
    val inner = @Composable { m: Modifier ->
        if (img != null) Image(img, null, m) else Box(m)
    }
    if (useBadge) {
        Box(
            modifier.size(size * 1.45f).clip(RoundedCornerShape(size * .4f)).background(Palette.Ink2).alpha(a),
            contentAlignment = Alignment.Center,
        ) { inner(Modifier.size(size)) }
    } else inner(modifier.size(size).alpha(a))
}

/** Свои картинки, загруженные раньше (иконки, фото рецептов), без сохранённых исходников для перекадрирования. */
private fun ownImages(ctx: Context, dishes: Boolean): List<String> =
    (listOf("icons") + if (dishes) listOf("recipes") else emptyList()).flatMap { dir ->
        java.io.File(ctx.filesDir, dir).listFiles().orEmpty()
            .filter { it.isFile && !it.name.contains("_orig") && it.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp") }
    }.sortedByDescending { it.lastModified() }.map { it.absolutePath }

private const val OWN_TAB = "own:"

/**
 * Выбор иконки или иллюстрации на весь экран: вкладки наборов — лентой сверху, сетка картинок занимает всё остальное место,
 * поэтому даже при крупном шрифте телефона картинки видны.
 */
@Composable
fun GlyphPickerDialog(
    selected: String,
    onDismiss: () -> Unit,
    dishes: Boolean = false,
    onPick: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val current = Glyphs.normalize(selected)
    var ownVersion by remember { mutableStateOf(0) }
    val own = remember(ownVersion) { ownImages(ctx, dishes) }
    val tabs = buildList {
        if (own.isNotEmpty()) add("Мои картинки" to OWN_TAB)
        if (dishes) Glyphs.DISH_SETS.forEach { (t, s) -> add(t to "dish:$s") }
        add("Основные" to "ui")
        Glyphs.SETS.forEach { add(it) }
        if (!dishes) Glyphs.DISH_SETS.filter { it.second in Glyphs.SHARED_DISH_SETS }.forEach { (t, s) -> add(t to "dish:$s") }
    }
    val initial = remember {
        when {
            current.startsWith("/") && own.isNotEmpty() -> OWN_TAB
            current.startsWith("dish/") -> "dish:" + current.removePrefix("dish/").substringBefore('/')
            current.startsWith("ui:") -> "ui"
            '/' in current && !dishes -> current.substringBefore('/')
            else -> tabs.first { it.second != OWN_TAB }.second
        }
    }
    var tab by remember { mutableStateOf(initial) }
    val keys = remember(tab, own) {
        when {
            tab == OWN_TAB -> own
            tab == "ui" -> Glyphs.UI.map { "ui:$it" }
            tab.startsWith("dish:") -> Glyphs.dishKeysOf(ctx, tab.removePrefix("dish:"))
            else -> Glyphs.keysOf(ctx, tab)
        }
    }
    val pickOwn = rememberImagePickerWithCrop(1f, "icons", round = !dishes, png = !dishes, outW = if (dishes) 900 else 320) { path ->
        ownVersion++
        onPick(path); onDismiss()
    }
    val tabsState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = tabs.indexOfFirst { it.second == initial }.coerceAtLeast(0),
    )
    val big = dishes || tab == OWN_TAB
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(vertical = 16.dp)) {
                androidx.compose.foundation.layout.Row(
                    Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (dishes) "Иллюстрация" else "Иконка",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
                androidx.compose.foundation.lazy.LazyRow(
                    state = tabsState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    items(tabs.size) { i -> val (t, s) = tabs[i]; Pill(t, s == tab) { tab = s } }
                }
                TextButton(onClick = { pickOwn() }, modifier = Modifier.padding(start = 8.dp)) { Text("+ Своя картинка из галереи") }
                if (keys.isEmpty()) {
                    Text(
                        "В этом наборе пока нет картинок",
                        color = LocalExtra.current.dim,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                LazyVerticalGrid(
                    GridCells.Adaptive(if (big) 84.dp else 60.dp),
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                ) {
                    items(keys, key = { it }) { k ->
                        Box(
                            Modifier.padding(4.dp).aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (k == current) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                    else Modifier,
                                )
                                .clickable { onPick(k); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                k.startsWith("/") -> {
                                    val img by rememberImage(k, 256)
                                    img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                }
                                k.startsWith("dish/") -> Glyph(k, 72.dp)
                                else -> Glyph(k, 30.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Кнопка «иконка» в формах: показывает текущую, по нажатию открывает выбор. */
@Composable
fun GlyphField(value: String, onChange: (String) -> Unit, label: String = "Иконка", dishes: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable { open = true }.padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(value, 30.dp)
        Text(label, fontSize = 11.sp, color = LocalExtra.current.dim, modifier = Modifier.padding(top = 4.dp))
    }
    if (open) GlyphPickerDialog(value, { open = false }, dishes, onChange)
}

/** Быстрый выбор из короткого списка иконок. */
@Composable
fun GlyphRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val sel = Glyphs.normalize(selected)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { k ->
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .then(if (k == sel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { onSelect(k) },
                contentAlignment = Alignment.Center,
            ) { Glyph(k, 26.dp) }
        }
    }
}

/** Настроение 1..5 — нарисованное лицо в фирменных цветах вместо эмодзи. */
@Composable
fun MoodFace(level: Int, size: Dp = 32.dp, modifier: Modifier = Modifier, selected: Boolean = true) {
    val colors = listOf(Color(0xFFB4553F), Color(0xFFD08C5B), Color(0xFFC7A46A), Color(0xFF9DB07A), Color(0xFF6FA36A))
    val c = colors[(level - 1).coerceIn(0, 4)].let { if (selected) it else it.copy(alpha = .35f) }
    val ink = Palette.Ink
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        drawCircle(c, s / 2)
        val eyeY = s * .40f
        drawCircle(ink, s * .055f, Offset(s * .35f, eyeY))
        drawCircle(ink, s * .055f, Offset(s * .65f, eyeY))
        val stroke = Stroke(width = s * .07f, cap = StrokeCap.Round)
        val left = s * .30f; val right = s * .70f; val y = s * .66f
        when (level.coerceIn(1, 5)) {
            3 -> drawLine(ink, Offset(left, y), Offset(right, y), s * .07f, cap = StrokeCap.Round)
            else -> {
                val smile = level > 3
                val h = if (level == 1 || level == 5) s * .22f else s * .14f
                val top = if (smile) y - h * .6f else y - h * .2f
                drawArc(ink, if (smile) 20f else 200f, 140f, false, Offset(left, top), Size(right - left, h), style = stroke)
            }
        }
    }
}
