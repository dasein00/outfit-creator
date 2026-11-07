package com.dasein.poryadok.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dasein.poryadok.ui.theme.Halloween
import com.dasein.poryadok.ui.theme.Holiday
import com.dasein.poryadok.ui.theme.LocalExtra
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val LIME = Color(0xFFC9F05A)
private val MOSS = Color(0xFF7FC23A)
private val PLUM = Color(0xFF2A1838)
private val NIGHT = Color(0xFF1A1022)
private val LILAC = Color(0xFFF2EEF6)
private val EYE_RED = Color(0xFFFF3B3B)

/** Картинки Хэллоуина из assets/halloween: кайма, ветки, туман, мыши, глаза, рука, привидения, листья. */
object HalloweenAssets {
    val ready = mutableStateOf(false)
    var far: ImageBitmap? = null; private set
    var near: ImageBitmap? = null; private set
    var top: ImageBitmap? = null; private set
    var fog1: ImageBitmap? = null; private set
    var fog2: ImageBitmap? = null; private set
    var batHang: List<ImageBitmap> = emptyList(); private set
    var batUp: List<ImageBitmap> = emptyList(); private set
    var batFly: List<ImageBitmap> = emptyList(); private set
    var batLand: List<ImageBitmap> = emptyList(); private set
    /** [вид][открыты, прикрыты, закрыты]. */
    var eyes: List<List<ImageBitmap>> = emptyList(); private set
    var hand: List<ImageBitmap> = emptyList(); private set
    var ghost: List<ImageBitmap> = emptyList(); private set
    var ghostling: List<ImageBitmap> = emptyList(); private set
    var leaves: List<ImageBitmap> = emptyList(); private set
    var fallBats: List<ImageBitmap> = emptyList(); private set
    var stars: List<ImageBitmap> = emptyList(); private set

    fun load(ctx: android.content.Context) {
        if (ready.value) return
        fun one(n: String) = runCatching { ctx.assets.open("halloween/$n.webp").use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        fun many(prefix: String, n: Int) = (0 until n).mapNotNull { one("${prefix}_$it") }
        far = one("border_far"); near = one("border_near"); top = one("border_top"); fog1 = one("fog_1"); fog2 = one("fog_2")
        batHang = many("bat_hang", 4); batUp = many("bat_up", 4); batFly = many("bat_fly", 4); batLand = many("bat_land", 4)
        eyes = (0 until 4).map { k -> (0 until 3).mapNotNull { one("eyes_${k}_$it") } }.filter { it.size == 3 }
        hand = many("hand", 6); ghost = many("ghost", 6); ghostling = many("ghostling", 4)
        leaves = many("fall_leaf", 6); fallBats = many("fall_bat", 3); stars = many("fall_star", 3)
        ready.value = near != null && batHang.size == 4 && batFly.size == 4 && eyes.isNotEmpty() && hand.size == 6 && ghost.size == 6
    }
}

private fun DrawScope.img(b: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f, filter: ColorFilter? = null) = drawImage(
    b, IntOffset.Zero, IntSize(b.width, b.height), IntOffset(x.roundToInt(), y.roundToInt()), IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
    alpha = alpha, colorFilter = filter,
)

private fun DrawScope.glow(c: Color, center: Offset, r: Float, a: Float) =
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), c.copy(alpha = 0f)), center, r), r, center)

/** Полоса тумана: текстура плывёт по горизонтали и повторяется без шва. */
private fun DrawScope.fogBand(tex: ImageBitmap?, y: Float, bandH: Float, speed: Float, t: Float, color: Color) {
    tex ?: return
    val tw = size.width * 1.3f
    var x = -((t * speed * density) % tw)
    val f = ColorFilter.tint(color)
    while (x < size.width) { img(tex, x, y, tw, bandH, filter = f); x += tw }
}

/** Светлячки: лаймовые и золотые огоньки медленно плавают и гаснут. */
private class Firefly(val x: Float, val y: Float, val sp: Float, val ph: Float, val gold: Boolean)
private val FIREFLIES = Random(3110).let { r -> List(14) { Firefly(r.nextFloat(), .25f + r.nextFloat() * .65f, .3f + r.nextFloat() * .5f, r.nextFloat() * 6.28f, it % 4 == 0) } }

/** Где на кайме кусты (глаза снизу) и кроны (глаза сверху), в долях картинки каймы. */
private val BUSH_EYES = listOf(.155f to .70f, .40f to .80f, .74f to .74f, .965f to .69f)
private val CROWN_EYES = listOf(.13f to .16f, .89f to .13f)

/** Куда на ветках верхней каймы садятся летучие мыши (низ ветки), в долях картинки. */
private val PERCHES = listOf(.23f to .14f, .315f to .135f, .215f to .34f, .30f to .32f, .57f to .215f, .645f to .225f, .78f to .315f, .86f to .317f)

/** Стайка летучих мышей: висят на ветках, иногда одна взлетает и пересаживается на свободную ветку. */
private object Flock {
    const val DUR = 2.8f
    val at = intArrayOf(0, 4, 6)
    val to = intArrayOf(-1, -1, -1)
    val start = FloatArray(3)
    val next = floatArrayOf(5f, 11f, 17f)
    val rnd = Random(1031)

    fun step(t: Float) {
        for (i in at.indices) {
            if (to[i] >= 0 && (t < start[i] || t - start[i] >= DUR)) { at[i] = to[i]; to[i] = -1; next[i] = t + 7f + rnd.nextFloat() * 10f }
            if (to[i] < 0 && t >= next[i]) {
                val busy = at.toSet() + to.filter { it >= 0 }
                val free = PERCHES.indices.filter { it !in busy }
                if (free.isNotEmpty()) { to[i] = free[rnd.nextInt(free.size)]; start[i] = t }
            }
            if (t + 60f < next[i]) next[i] = t + 5f
        }
    }
}

/**
 * Хэллоуинская сцена во весь экран: ночной градиент, луна, светлячки, туман между дальним и ближним планом
 * кладбища, рука из могилы, моргающие красные глаза в кустах и кронах, ветки с летучими мышами.
 */
fun DrawScope.halloweenScene(t: Float, dark: Boolean) {
    val w = size.width; val h = size.height
    val st = Halloween.bg()
    val v = if (dark) st.dark else st.light
    drawRect(Brush.radialGradient(v, Offset(w * .5f, h * .3f), maxOf(w, h) * .95f))
    // Луна с мягким пульсирующим свечением.
    val mc = Offset(w * .8f, h * .15f); val mr = w * .1f
    val pulse = .85f + .15f * sin(t * .8f)
    glow(LIME, mc, mr * 3.2f, (if (dark) .26f else .18f) * pulse)
    drawCircle(LIME.copy(alpha = if (dark) .92f else .7f), mr, mc)
    val crater = Color(0xFFA8D84A).copy(alpha = .7f)
    drawCircle(crater, mr * .22f, mc + Offset(-mr * .32f, -mr * .18f))
    drawCircle(crater, mr * .14f, mc + Offset(mr * .3f, mr * .25f))
    drawCircle(crater, mr * .1f, mc + Offset(-mr * .05f, mr * .45f))
    // Светлячки.
    FIREFLIES.forEach { f ->
        val a = ((sin(t * 1.6f * f.sp + f.ph) + 1f) / 2f).let { it * it }
        if (a < .05f) return@forEach
        val p = Offset((f.x + .03f * sin(t * f.sp + f.ph)) * w, (f.y + .02f * cos(t * f.sp * .8f + f.ph)) * h)
        val c = if (f.gold) Color(0xFFF7D66B) else LIME
        glow(c, p, 9f * density, .5f * a)
        drawCircle(c.copy(alpha = a), 1.8f * density, p)
    }
    val as0 = HalloweenAssets
    if (!as0.ready.value) return
    val fogOn = Halloween.fog.value
    val lav = Color(0xFFD8C6F0)
    // Лёгкий туман под плашками посередине экрана.
    if (fogOn) fogBand(as0.fog2, h * .38f, h * .3f, 5f, t, lav.copy(alpha = if (dark) .09f else .16f))
    // Кладбище внизу: дальний план → туман → ближний план.
    val bh = w / 3f; val by = h - bh
    as0.far?.let { img(it, 0f, by, w, bh, alpha = .95f) }
    if (fogOn) {
        fogBand(as0.fog1, by + bh * .38f, bh * .5f, 9f, t, lav.copy(alpha = if (dark) .55f else .6f))
        fogBand(as0.fog2, by + bh * .55f, bh * .35f, 16f, t, LIME.copy(alpha = .22f))
    }
    as0.near?.let { img(it, 0f, by, w, bh) }
    // Рука вырывается из земли у могилы, машет и уходит обратно.
    if (Halloween.hand.value) {
        val local = (t + 8f) % 26f
        val f = when {
            local < 1f -> (local * 6).toInt().coerceIn(0, 5)
            local < 3.2f -> if (((local - 1f) / .35f).toInt() % 2 == 0) 5 else 4
            local < 4.2f -> (5 - ((local - 3.2f) * 6).toInt()).coerceIn(0, 5)
            else -> 0
        }
        val hb = as0.hand[f]
        val hh = bh * .34f; val hw = hh * hb.width / hb.height
        img(hb, w * .2f - hw / 2f, by + bh * .955f - hh, hw, hh)
    }
    if (fogOn) fogBand(as0.fog1, by + bh * .78f, bh * .3f, 22f, t, lav.copy(alpha = .22f))
    // Моргающие красные глаза: в кустах кладбища и в кронах деревьев.
    if (Halloween.eyes.value) {
        BUSH_EYES.forEachIndexed { i, (fx, fy) -> eyesAt(i, t, w * fx, by + bh * fy, w * .085f) }
    }
    // Ветки сверху, на них летучие мыши.
    val th = w / 3f
    as0.top?.let { img(it, 0f, 0f, w, th) }
    if (Halloween.eyes.value) CROWN_EYES.forEachIndexed { i, (fx, fy) -> eyesAt(i + 4, t, w * fx, th * fy + w * .03f, w * .07f) }
    if (Halloween.bats.value) bats(t, w, th)
}

private fun DrawScope.eyesAt(i: Int, t: Float, cx: Float, bottom: Float, ew: Float) {
    val set = HalloweenAssets.eyes.getOrNull(i % HalloweenAssets.eyes.size) ?: return
    val vis = ((sin(t * .21f + i * 2.3f) + .35f) / .4f).coerceIn(0f, 1f)
    if (vis <= 0f) return
    val p = 3.4f + i * .9f
    val ph = (t + i * 1.7f) % p
    val frame = when {
        ph > p - .12f -> 1
        ph > p - .24f -> 2
        ph > p - .36f -> 1
        else -> 0
    }
    val b = set[frame]
    val eh = ew * b.height / b.width
    if (frame != 2) glow(EYE_RED, Offset(cx, bottom - eh * .5f), ew * .55f, .32f * vis)
    img(b, cx - ew / 2f, bottom - eh, ew, eh, alpha = vis)
}

private fun DrawScope.bats(t: Float, w: Float, th: Float) {
    val a = HalloweenAssets
    Flock.step(t)
    val bw = w * .1f
    val bh = bw * a.batHang[0].height / a.batHang[0].width
    fun perch(i: Int) = Offset(PERCHES[i].first * w, PERCHES[i].second * th)
    for (i in Flock.at.indices) {
        val from = perch(Flock.at[i])
        val dest = Flock.to[i]
        if (dest < 0) {
            val k = ((t * 1.1f + i * 3f) % 7f).toInt()
            val f = a.batHang[when (k) { 5 -> 1; 6 -> 2; else -> 0 }]
            img(f, from.x - bw * .53f, from.y, bw, bh)
            continue
        }
        val to = perch(dest)
        val u = ((t - Flock.start[i]) / Flock.DUR).coerceIn(0f, 1f)
        val left = to.x < from.x
        fun mirrored(anchorX: Float, block: DrawScope.() -> Unit) = if (left) scale(-1f, 1f, Offset(anchorX, 0f)) { block() } else block()
        when {
            u < .14f -> { val f = a.batUp[(u / .14f * 4).toInt().coerceIn(0, 3)]; mirrored(from.x) { img(f, from.x - bw * .53f, from.y, bw, bh) } }
            u > .86f -> { val f = a.batLand[((u - .86f) / .14f * 4).toInt().coerceIn(0, 3)]; mirrored(to.x) { img(f, to.x - bw * .53f, to.y, bw, bh) } }
            else -> {
                val s = ((u - .14f) / .72f).let { it * it * (3 - 2 * it) }
                val p0 = from + Offset(0f, bh * .55f); val p2 = to + Offset(0f, bh * .55f)
                val c = Offset((p0.x + p2.x) / 2f, maxOf(p0.y, p2.y) + w * .2f)
                val pos = Offset(
                    (1 - s) * (1 - s) * p0.x + 2 * (1 - s) * s * c.x + s * s * p2.x,
                    (1 - s) * (1 - s) * p0.y + 2 * (1 - s) * s * c.y + s * s * p2.y,
                )
                val f = a.batFly[(t * 10f).toInt() % 4]
                mirrored(pos.x) { img(f, pos.x - bw / 2f, pos.y - bh / 2f, bw, bh) }
            }
        }
    }
}

/** Фон экрана в хэллоуинский режим — рисуется под карточками, нажатия проходят сквозь. */
@Composable
fun HalloweenBackdrop(modifier: Modifier = Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { HalloweenAssets.load(ctx.applicationContext) } }
    val dark = LocalExtra.current.dark
    HalloweenAssets.ready.value
    Canvas(modifier) { halloweenScene(Holiday.clock.floatValue, dark) }
}

/**
 * Шапка плашки в хэллоуинский режим: земляной холмик со мхом и норы (одна или две). Время от времени из норы
 * выползает паучок: при двух норах — перебегает по шапке в другую, при одной — гуляет по плашке и возвращается.
 */
fun Modifier.halloweenCap(seed: Int, force: Boolean = false): Modifier = drawWithContent {
    drawContent()
    if (!force && !Halloween.active) return@drawWithContent
    val rnd = Random(seed)
    val ch = 9.dp.toPx()
    val w = size.width
    // Холмик: волнистый верх, прямой низ.
    val top = Path()
    top.moveTo(0f, ch * .55f)
    var x = 0f
    var hi = true
    while (x < w) {
        val step = w * (.08f + rnd.nextFloat() * .1f)
        val nx = (x + step).coerceAtMost(w)
        top.quadraticTo((x + nx) / 2f, if (hi) ch * (.05f + rnd.nextFloat() * .2f) else ch * (.45f + rnd.nextFloat() * .2f), nx, ch * (.35f + rnd.nextFloat() * .25f))
        x = nx; hi = !hi
    }
    val fill = Path().apply { addPath(top); lineTo(w, ch); lineTo(0f, ch); close() }
    drawPath(fill, PLUM)
    drawPath(top, MOSS, style = Stroke(2.2f * density, cap = StrokeCap.Round))
    drawPath(top, LIME.copy(alpha = .8f), style = Stroke(.9f * density, cap = StrokeCap.Round))
    // Травинки.
    repeat(6) {
        val gx = rnd.nextFloat() * w
        val gy = ch * .45f
        drawLine(MOSS, Offset(gx, gy), Offset(gx + (rnd.nextFloat() - .5f) * 4 * density, gy - (2.5f + rnd.nextFloat() * 3f) * density), 1.2f * density, StrokeCap.Round)
    }
    // Норы.
    val two = (seed and 1) == 1
    val holes = if (two) listOf(w * (.15f + rnd.nextFloat() * .15f), w * (.62f + rnd.nextFloat() * .2f)) else listOf(w * (.25f + rnd.nextFloat() * .5f))
    val hw = 13.dp.toPx(); val hh = 6.dp.toPx(); val hy = ch * .62f
    holes.forEach { hx ->
        drawOval(Color(0xFF0D0711), Offset(hx - hw / 2f, hy - hh / 2f), Size(hw, hh))
        drawArc(LIME.copy(alpha = .7f), 190f, 160f, false, Offset(hx - hw / 2f, hy - hh / 2f), Size(hw, hh), style = Stroke(1f * density))
    }
    if (!Halloween.spiders.value && !force) return@drawWithContent
    // Паучок: выход раз в 15–19 с, прогулка ~7 с.
    val t = Holiday.clock.floatValue
    val period = 15f + (seed.mod(5))
    val local = (t + seed.mod(97)) % period
    if (local > 7f) return@drawWithContent
    val a = holes[0]
    val emerge = .7f
    val pos: Offset
    val dir: Offset
    val sc: Float
    if (two) {
        val b = holes[1]
        val u = ((local - emerge) / (7f - 2 * emerge)).coerceIn(0f, 1f)
        val s = u * u * (3 - 2 * u)
        val px = a + (b - a) * s
        pos = Offset(px, ch * .3f + sin(s * PI.toFloat() * 6f) * .6f * density)
        dir = Offset(b - a, 0f)
        sc = when { local < emerge -> local / emerge; local > 7f - emerge -> (7f - local) / emerge; else -> 1f }
    } else {
        val r = 26.dp.toPx()
        val u = ((local - emerge) / (7f - 2 * emerge)).coerceIn(0f, 1f)
        val ang = u * 2f * PI.toFloat()
        pos = Offset(a + r * 1.5f * sin(ang), hy + r * (1 - cos(ang)))
        dir = Offset(r * 1.5f * cos(ang), r * sin(ang)).let { if (it.getDistance() < .01f) Offset(0f, 1f) else it }
        sc = when { local < emerge -> local / emerge; local > 7f - emerge -> (7f - local) / emerge; else -> 1f }
    }
    spider(pos, atan2(dir.y, dir.x) * 180f / PI.toFloat(), sc.coerceIn(0f, 1f), t, 4.2f * density)
}

/** Паучок сверху: брюшко, голова, восемь шагающих лапок, красные глазки. Смотрит по [angle]. */
private fun DrawScope.spider(c: Offset, angle: Float, sc: Float, t: Float, r: Float) {
    if (sc <= .02f) return
    translate(c.x, c.y) {
        rotate(angle, Offset.Zero) {
            scale(sc, sc, Offset.Zero) {
                for (side in listOf(-1f, 1f)) for (k in 0 until 4) {
                    val base = (-55f + k * 36f) * PI.toFloat() / 180f
                    val swing = sin(t * 16f + k * 1.6f + side) * .28f
                    val ang = base + swing
                    val knee = Offset(cos(ang) * r * 1.4f, side * (r * .9f + sin(ang).let { if (it < 0) -it else it } * r * .5f))
                    val foot = Offset(cos(ang) * r * 2.3f, side * r * 2.2f)
                    drawLine(NIGHT, Offset(r * .2f, 0f), knee, r * .28f, StrokeCap.Round)
                    drawLine(NIGHT, knee, foot, r * .22f, StrokeCap.Round)
                }
                drawOval(NIGHT, Offset(-r * 1.6f, -r * .85f), Size(r * 1.7f, r * 1.7f))
                drawCircle(NIGHT, r * .62f, Offset(r * .55f, 0f))
                drawArc(LIME.copy(alpha = .75f), 200f, 110f, false, Offset(-r * 1.5f, -r * .78f), Size(r * 1.5f, r * 1.5f), style = Stroke(r * .2f))
                drawCircle(EYE_RED, r * .17f, Offset(r * .85f, -r * .22f))
                drawCircle(EYE_RED, r * .17f, Offset(r * .85f, r * .22f))
            }
        }
    }
}

/** Привидение с двумя маленькими спутниками летит слева направо по коридору; [p] — доля пути, [t] — секунды. */
fun DrawScope.ghostTeam(p: Float, t: Float, cw: Float, ch: Float) {
    val a = HalloweenAssets
    if (a.ghost.size < 6 || a.ghostling.size < 4) return
    val gh = ch * 1.05f
    val gw = gh * a.ghost[0].width / a.ghost[0].height
    val sh = ch * .6f
    val sw = sh * a.ghostling[0].width / a.ghostling[0].height
    val teamW = gw + sw * 2.4f
    val front = -cw * .03f + p * (cw * 1.06f + teamW)
    val arc = -sin(p * PI.toFloat()) * ch * .12f
    val gx = front - gw
    val gy = (ch - gh) / 2f + arc + sin(t * 2.4f) * ch * .07f
    // Мерцающий лиловый шлейф.
    for (k in 1..16) {
        val sx = gx - sw * 2.2f - k * cw * .02f
        val sy = gy + gh * (.55f + .1f * sin(k * 1.3f + t * 3f))
        val al = (1f - k / 17f) * (.5f + .5f * sin(t * 8f + k * 2f).let { it * it })
        val r = (1.4f + (k % 3) * .8f) * density
        glow(Color(0xFFCDB8F0), Offset(sx, sy), r * 3f, .4f * al)
        drawCircle(Color(0xFFCDB8F0).copy(alpha = al), r, Offset(sx, sy))
    }
    for (k in 0 until 2) {
        val f = a.ghostling[((t * 6f).toInt() + k * 2) % 4]
        val sx = gx - sw * (1.05f + k * 1.15f)
        val sy = gy + gh * (.2f + k * .18f) + sin(t * 3f + k * 1.7f) * ch * .08f
        img(f, sx, sy, sw, sh)
    }
    val mid = (gx + gw / 2f) / cw
    val f = if (mid in .3f..0.7f) a.ghost[4 + ((t * 3f).toInt() % 2)] else a.ghost[(t * 6f).toInt() % 4]
    img(f, gx, gy, gw, gh)
}

private class Fall(val kind: Int, val img: Int, val x: Float, val y0: Float, val speed: Float, val size: Float, val sway: Float, val swayHz: Float, val phase: Float, val spin: Float)

/** Листопад при новом входе: листья кружатся, летучие мыши пролетают, звёздочки мерцают — 5 секунд, потом за край экрана. */
@Composable
fun HalloweenFall() {
    if (!Halloween.active || !Halloween.leaves.value) return
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { HalloweenAssets.load(ctx.applicationContext) } }
    val entry = Holiday.entry.intValue
    var running by remember { mutableStateOf(false) }
    val elapsed = remember { mutableLongStateOf(0L) }
    val items = remember(entry) {
        val r = Random(entry * 7 + 1)
        List(34) {
            val kind = when { it % 5 == 0 -> 1; it % 7 == 0 -> 2; else -> 0 }
            val speed = .14f + r.nextFloat() * .08f
            Fall(kind, r.nextInt(6), r.nextFloat(), -.06f - speed * r.nextFloat() * 5f, speed, if (kind == 2) 14f else 24f + r.nextFloat() * 12f,
                .01f + r.nextFloat() * .03f, .2f + r.nextFloat() * .3f, r.nextFloat() * 6.28f, (r.nextFloat() - .5f) * 140f)
        }
    }
    val total = (items.maxOf { (1.08f - it.y0) / it.speed } * 1000).toLong()
    LaunchedEffect(entry) {
        if (entry <= FallState.shownFor) return@LaunchedEffect
        FallState.shownFor = entry
        if (TabAnim.saving(ctx)) return@LaunchedEffect
        running = true
        val start = withFrameMillis { it }
        while (elapsed.longValue < total) elapsed.longValue = withFrameMillis { it } - start
        running = false
        elapsed.longValue = 0L
    }
    if (!running || !HalloweenAssets.ready.value) return
    Canvas(Modifier.fillMaxSize()) {
        val t = elapsed.longValue / 1000f
        val fade = (t / .4f).coerceAtMost(1f)
        val a = HalloweenAssets
        items.forEach { f ->
            val y = (f.y0 + f.speed * t) * size.height
            if (y < -60f || y > size.height + 60f) return@forEach
            val x = (f.x + f.sway * sin(2f * PI.toFloat() * f.swayHz * t + f.phase) + if (f.kind == 1) t * .03f else 0f) * size.width
            val list = when (f.kind) { 1 -> a.fallBats; 2 -> a.stars; else -> a.leaves }
            if (list.isEmpty()) return@forEach
            val b = list[f.img % list.size]
            val sw = f.size * density
            val sh = sw * b.height / b.width
            when (f.kind) {
                0 -> rotate(f.phase * 57f + t * f.spin, Offset(x, y)) { img(b, x - sw / 2f, y - sh / 2f, sw, sh, alpha = fade) }
                1 -> scale(1f, .75f + .25f * sin(t * 18f + f.phase), Offset(x, y)) { img(b, x - sw / 2f, y - sh / 2f, sw, sh, alpha = fade) }
                else -> img(b, x - sw / 2f, y - sh / 2f, sw, sh, alpha = fade * (.5f + .5f * sin(t * 6f + f.phase).let { it * it }))
            }
        }
    }
}

private object FallState { var shownFor = 0 }
