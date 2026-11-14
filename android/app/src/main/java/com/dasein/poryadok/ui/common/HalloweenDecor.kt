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
import androidx.compose.ui.graphics.graphicsLayer
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
    /** Шапки плашек: холмик со мхом, травой, грибами и норами (первые 4 — одна нора, остальные — две). */
    var caps: List<ImageBitmap> = emptyList(); private set
    /** Паучок: сбоку (идёт вправо), сверху (идёт вверх), выглядывает из норы. */
    var spiderSide: List<ImageBitmap> = emptyList(); private set
    var spiderTop: List<ImageBitmap> = emptyList(); private set
    var spiderPeek: List<ImageBitmap> = emptyList(); private set
    /** Злые глаза: [вид][открыты, прикрыты, почти закрыты, закрыты]. */
    var evil: List<List<ImageBitmap>> = emptyList(); private set
    /** Ворон: сидит, каркает, летит, взлетает, садится (смотрит вправо, лапы — внизу по центру кадра). */
    var crowIdle: List<ImageBitmap> = emptyList(); private set
    var crowCaw: List<ImageBitmap> = emptyList(); private set
    var crowFly: List<ImageBitmap> = emptyList(); private set
    var crowUp: List<ImageBitmap> = emptyList(); private set
    var crowLand: List<ImageBitmap> = emptyList(); private set
    /** Ведьма на метле: 0–3 полёт, 4–5 машет, 6–7 с котом. */
    var witch: List<ImageBitmap> = emptyList(); private set
    /** Сцена «Замок»: дальний план (замок на холме) и средний (кладбище с дорожкой). */
    var castleFar: ImageBitmap? = null; private set
    var castleMid: ImageBitmap? = null; private set
    /** Крыса: walk, peek, snarl, scared, run, climb, dive — по 4 кадра, смотрит вправо, низ на одной линии. */
    var rat: Map<String, List<ImageBitmap>> = emptyMap(); private set
    /** Паук для встреч: snarl, scared, run, climb, dive — по 4 кадра. */
    var spi: Map<String, List<ImageBitmap>> = emptyMap(); private set

    /** Вызывается из нескольких мест сразу — под замком, чтобы картинки не декодировались дважды. */
    @Synchronized
    fun load(ctx: android.content.Context) {
        if (ready.value) return
        fun one(n: String) = runCatching { ctx.assets.open("halloween/$n.webp").use { android.graphics.BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        fun many(prefix: String, n: Int) = (0 until n).mapNotNull { one("${prefix}_$it") }
        far = one("border_far"); near = one("border_near"); top = one("border_top"); fog1 = one("fog_1"); fog2 = one("fog_2")
        batHang = many("bat_hang", 4); batUp = many("bat_up", 4); batFly = many("bat_fly", 4); batLand = many("bat_land", 4)
        eyes = (0 until 4).map { k -> (0 until 3).mapNotNull { one("eyes_${k}_$it") } }.filter { it.size == 3 }
        hand = many("hand", 6); ghost = many("ghost", 6); ghostling = many("ghostling", 4)
        leaves = many("fall_leaf", 6); fallBats = many("fall_bat", 3); stars = many("fall_star", 3)
        caps = many("cap", 8); spiderSide = many("spider_side", 4); spiderTop = many("spider_top", 4); spiderPeek = many("spider_peek", 3)
        evil = (0 until 5).map { k -> (0 until 4).mapNotNull { one("evil_${k}_$it") } }.filter { it.size == 4 }
        crowIdle = many("crow_idle", 4); crowCaw = many("crow_caw", 4); crowFly = many("crow_fly", 4); crowUp = many("crow_up", 4); crowLand = many("crow_land", 4)
        witch = many("witch", 8)
        castleFar = one("castle_far"); castleMid = one("castle_mid")
        rat = listOf("walk", "peek", "snarl", "scared", "run", "climb", "dive").associateWith { many("rat_$it", 4) }.filterValues { it.size == 4 }
        spi = listOf("snarl", "scared", "run", "climb", "dive").associateWith { many("spi_$it", 4) }.filterValues { it.size == 4 }
        ready.value = near != null && batHang.size == 4 && batFly.size == 4 && evil.isNotEmpty() && hand.size == 6 && ghost.size == 6 &&
            spiderTop.size == 4 && spiderSide.size == 4 && spiderPeek.size == 3 && crowFly.size == 4 && crowIdle.size == 4 && crowUp.size == 4 && crowLand.size == 4 && crowCaw.size == 4 && witch.size == 8
    }
}

private fun DrawScope.img(b: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f, filter: ColorFilter? = null) = drawImage(
    b, IntOffset.Zero, IntSize(b.width, b.height), IntOffset(x.roundToInt(), y.roundToInt()), IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
    alpha = alpha, colorFilter = filter,
)

private fun DrawScope.glow(c: Color, center: Offset, r: Float, a: Float) =
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), c.copy(alpha = 0f)), center, r), r, center)

/** Готовые цветовые фильтры тумана (цветов всего несколько) — не создаются заново каждый кадр. */
private val fogTints = HashMap<ULong, ColorFilter>()

/** Полоса тумана: текстура плывёт по горизонтали и повторяется без шва. */
private fun DrawScope.fogBand(tex: ImageBitmap?, y: Float, bandH: Float, speed: Float, t: Float, color: Color) {
    tex ?: return
    val tw = size.width * 1.3f
    var x = -((t * speed * density) % tw)
    val f = fogTints.getOrPut(color.value) { ColorFilter.tint(color) }
    while (x < size.width) { img(tex, x, y, tw, bandH, filter = f); x += tw }
}

/** Светлячки: лаймовые и золотые огоньки медленно плавают и гаснут. */
private class Firefly(val x: Float, val y: Float, val sp: Float, val ph: Float, val gold: Boolean)
private val FIREFLIES = Random(3110).let { r -> List(14) { Firefly(r.nextFloat(), .25f + r.nextFloat() * .65f, .3f + r.nextFloat() * .5f, r.nextFloat() * 6.28f, it % 4 == 0) } }

/** Где на нижней кайме злые глаза: дупла деревьев, куст справа (центр, доли картинки каймы) и ширина глаз (доли экрана). */
private val TREE_EYES = listOf(Triple(.0859f, .2374f, .7f), Triple(.0635f, .4306f, .9f), Triple(.9047f, .2666f, .7f), Triple(.9366f, .5006f, .9f))
/** Норы в земле нижней каймы: сюда спускаются пауки, внутри потом горят глаза. */
private val BURROWS = listOf(.071f to .905f, .367f to .912f, .6907f to .8995f)
/** Ветки деревьев нижней каймы, на которых сидят вороны (верх ветки). */
private val CROW_PERCHES = listOf(.18f to .214f, .24f to .173f, .27f to .329f, .77f to .338f, .83f to .179f, .86f to .342f)

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

/** Звёзды неба сцены «Замок» (доли экрана, фаза мерцания). */
private val STARS = Random(77).let { r -> List(70) { Triple(r.nextFloat(), r.nextFloat() * .55f, r.nextFloat() * 6.28f) } }

/** Летучая мышь сцены «Замок»: траектория из волн с целым числом повторов за цикл — полёт всегда разный, но закольцован. */
private class CastleBat(
    val hx: List<Triple<Int, Float, Float>>, val hy: List<Triple<Int, Float, Float>>, val hz: List<Triple<Int, Float, Float>>,
    val ax: Float, val ay: Float, val oy: Float, val flap: Int, val s: Float, val dir: Int, val ph: Float, val circle: Boolean,
)

private const val BAT_LOOP = 24f

private val CASTLE_BATS: List<CastleBat> = Random(11).let { r ->
    fun u(a: Float, b: Float) = a + r.nextFloat() * (b - a)
    fun h(ks: List<Int>, lo: Float) = ks.map { k -> Triple(k, u(lo, 1f) / Math.pow(k.toDouble(), .8).toFloat(), u(0f, 6.28f)) }
    val around = List(22) {
        CastleBat(h(listOf(1, 2, 3, 5), .25f), h(listOf(1, 2, 3, 4), .2f), listOf(1, 2).map { k -> Triple(k, u(.5f, 1f), u(0f, 6.28f)) },
            u(.12f, .38f), u(.06f, .16f), u(.10f, .36f), 36 + r.nextInt(24), u(4.5f, 7.5f), if (r.nextBoolean()) 1 else -1, u(0f, 6.28f), false)
    }
    // Кружат над шпилями: почти круги, 2–3 оборота за цикл.
    val above = List(10) {
        val k = if (r.nextBoolean()) 2 else 3
        CastleBat(listOf(Triple(k, 1f, u(0f, 6.28f)), Triple(k * 2, u(.05f, .18f), u(0f, 6.28f))),
            listOf(Triple(k, 1f, 0f), Triple(k * 3, u(.05f, .2f), u(0f, 6.28f))), emptyList(),
            u(.05f, .13f), u(.02f, .05f), u(-.02f, .10f), 40 + r.nextInt(24), u(3.8f, 5.8f), if (r.nextBoolean()) 1 else -1, u(0f, 6.28f), true)
    }
    around + above
}

/** Сумма волн траектории; без временных объектов — вызывается десятки раз за кадр. */
private fun waves(hs: List<Triple<Int, Float, Float>>, u: Float, cosine: Boolean): Float {
    var s = 0f
    for (i in hs.indices) { val h = hs[i]; val a = h.first * u + h.third; s += h.second * (if (cosine) cos(a) else sin(a)) }
    return s
}
private fun amp(hs: List<Triple<Int, Float, Float>>): Float { var s = 0f; for (i in hs.indices) s += hs[i].second; return s }

/** Положение мыши пишется в [out]: x, y (в долях ширины/высоты сцены) и глубина z (меньше нуля — за замком). */
private fun batPos(b: CastleBat, t: Float, out: FloatArray) {
    val u = 2f * PI.toFloat() * t / BAT_LOOP * b.dir
    if (b.circle) {
        val h0 = b.hx[0]; val h1 = b.hx[1]; val v1 = b.hy[1]
        val z = sin(h0.first * u + h0.third)
        out[0] = .53f + b.ax * (cos(h0.first * u + h0.third) + h1.second * cos(h1.first * u + h1.third))
        out[1] = b.oy + b.ay * z + b.ay * .3f * sin(v1.first * u + v1.third)
        out[2] = z
        return
    }
    out[0] = .535f + b.ax * waves(b.hx, u, true) / amp(b.hx)
    out[1] = b.oy + b.ay * waves(b.hy, u, false) / amp(b.hy)
    out[2] = waves(b.hz, u, false)
}

/** Один контур на все силуэты и две точки-черновика: рисование идёт в одном потоке, новые объекты каждый кадр не нужны. */
private val batPath = Path()
private val batA = FloatArray(3)
private val batB = FloatArray(3)

/** Силуэт летучей мыши: тело, уши и крылья с фестонами; [flap] от -1 до 1 — взмах. */
private fun DrawScope.batShape(x: Float, y: Float, s: Float, flap: Float, color: Color, face: Float) {
    val up = -flap * s; val wing = s * 1.7f
    val p = batPath
    p.rewind()
    var sign = -1f
    repeat(2) {
        p.moveTo(x + sign * s * .15f, y - s * .05f)
        p.lineTo(x + sign * wing * .45f, y + up * .55f - s * .25f)
        p.lineTo(x + sign * wing, y + up)
        p.lineTo(x + sign * wing * .85f, y + up * .5f + s * .2f)
        p.lineTo(x + sign * wing * .68f, y + up * .35f + s * .05f)
        p.lineTo(x + sign * wing * .55f, y + up * .25f + s * .28f)
        p.lineTo(x + sign * wing * .38f, y + up * .12f + s * .12f)
        p.lineTo(x + sign * wing * .25f, y + s * .32f)
        p.lineTo(x + sign * s * .15f, y + s * .25f)
        p.close()
        sign = 1f
    }
    val hx = x + face * s * .08f
    var e = -1f
    repeat(2) {
        p.moveTo(hx + e * s * .15f, y - s * .28f); p.lineTo(hx + e * s * .1f, y - s * .55f); p.lineTo(hx + e * s * .02f, y - s * .3f); p.close()
        e = 1f
    }
    drawPath(p, color)
    drawOval(color, Offset(x - s * .22f, y - s * .2f), Size(s * .44f, s * .6f))
    drawOval(color, Offset(hx - s * .17f, y - s * .38f), Size(s * .34f, s * .32f))
}

private val BAT_BEHIND = Color(0xFF4E3480)
private val BAT_FRONT = Color(0xFF1E0F37)
private val BAT_FLOCK = Color(0xE6281646)

/**
 * Летучие мыши вокруг, над и за замком. [behind] — рисовать только тех, кто сейчас за замком (их закроют башни),
 * иначе — тех, кто перед ним, и две стайки в небе. [top]/[sh] — где лежит картинка замка.
 */
private fun DrawScope.castleBats(t: Float, w: Float, top: Float, sh: Float, behind: Boolean) {
    val u = 2f * PI.toFloat() * t / BAT_LOOP
    val k = w / 975f   // размер мышей — как в примере, относительно ширины сцены
    for (i in CASTLE_BATS.indices) {
        val b = CASTLE_BATS[i]
        batPos(b, t, batA)
        val z = batA[2]
        if ((z < 0f) != behind) continue
        batPos(b, t + .05f, batB)
        val glide = maxOf(0f, sin(3f * u + b.ph)).let { it * it * it }
        val flap = sin(b.flap * u + b.ph) * (1f - glide * .85f)
        val s = b.s * (1f + .3f * z) * k
        batShape(batA[0] * w, top + batA[1] * sh, s, flap, if (behind) BAT_BEHIND else BAT_FRONT, if (batB[0] > batA[0]) 1f else -1f)
    }
    if (behind) return
    for (fl in 0..1) {
        val n = if (fl == 0) 8 else 6
        val q = ((t / BAT_LOOP) + fl * .5f) % 1f
        for (j in 0 until n) {
            val x = (-.15f + 1.3f * q) * w - j * 13f * k - (j % 2) * 7f * k
            val y = top + sh * (if (fl == 0) .07f else .16f) + (j % 3) * 5f * k + sin(u * 24f + j) * 3f * k
            batShape(x, y, 3f * k, sin(u * 96f + j), BAT_FLOCK, 1f)
        }
    }
}

/**
 * Хэллоуинская сцена во весь экран: ночной градиент, луна, светлячки, туман между дальним и ближним планом
 * кладбища, рука из могилы, моргающие красные глаза в кустах и кронах, ветки с летучими мышами.
 */
fun DrawScope.halloweenScene(t: Float, dark: Boolean) {
    val w = size.width; val h = size.height
    val mc = Offset(w * .8f, h * .15f); val mr = w * .1f
    val castle = Halloween.scene.intValue == 1
    if (castle) {
        // Сцена «Замок»: ночное небо без луны — от тёмно-фиолетового к лавандовому, мерцающие звёзды.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF29124F), Color(0xFF5B3A9E), Color(0xFF8C66D9))))
        STARS.forEach { (x, y, ph) ->
            val a = .5f + .5f * sin(t * .9f + ph)
            if (a > .3f) drawCircle(Color.White.copy(alpha = a * .8f), 1.1f * density, Offset(x * w, y * h))
        }
    } else {
    val st = Halloween.bg()
    val v = if (dark) st.dark else st.light
    drawRect(Brush.radialGradient(v, Offset(w * .5f, h * .3f), maxOf(w, h) * .95f))
    // Луна с мягким пульсирующим свечением.
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
    }
    val as0 = HalloweenAssets
    if (!as0.ready.value) return
    if (Halloween.witch.value) witchFlight(t, w, h, mc, mr)
    val fogOn = Halloween.fog.value
    val lav = Color(0xFFD8C6F0)
    val bh = w / 3f; val by = h - bh
    val cFar = as0.castleFar; val cMid = as0.castleMid
    if (castle && cFar != null && cMid != null) {
        // Замок на холме → туман (гуще вдали) → кладбище с дорожкой → туман у земли; ближний план — как прежде.
        val sh = w / 2f
        val top = by + bh * .62f - sh * .68f  // опущено: верх замка ниже плашек
        if (Halloween.bats.value) castleBats(t, w, top, sh, behind = true)
        img(cFar, 0f, top, w, sh)
        if (fogOn) {
            fogBand(as0.fog1, top + sh * .18f, sh * .4f, 1.2f, t, lav.copy(alpha = .45f))
            fogBand(as0.fog2, top + sh * .5f, sh * .24f, 1.8f, t, lav.copy(alpha = .65f))
        }
        if (Halloween.bats.value) castleBats(t, w, top, sh, behind = false)
        img(cMid, 0f, top, w, sh)
        if (fogOn) {
            fogBand(as0.fog2, top + sh * .72f, sh * .16f, 2.4f, t, lav.copy(alpha = .38f))
            fogBand(as0.fog1, top + sh * .9f, sh * .14f, 3f, t, lav.copy(alpha = .22f))
        }
    } else {
    // Лёгкий туман под плашками посередине экрана.
    if (fogOn) fogBand(as0.fog2, h * .38f, h * .3f, 5f, t, lav.copy(alpha = if (dark) .09f else .16f))
    // Кладбище внизу: дальний план → туман → ближний план.
    as0.far?.let { img(it, 0f, by, w, bh, alpha = .95f) }
    if (fogOn) {
        fogBand(as0.fog1, by + bh * .4f, bh * .45f, 9f, t, lav.copy(alpha = if (dark) .5f else .55f))
        fogBand(as0.fog2, by + bh * .55f, bh * .35f, 16f, t, LIME.copy(alpha = .2f))
    }
    }
    as0.near?.let { img(it, 0f, by, w, bh) }
    // Рука вырывается из земли у креста справа, машет и уходит обратно.
    if (Halloween.hand.value) {
        val local = (t + 8f) % 26f
        val f = when {
            local < 1f -> (local * 6).toInt().coerceIn(0, 5)
            local < 3.2f -> if (((local - 1f) / .35f).toInt() % 2 == 0) 5 else 4
            local < 4.2f -> (5 - ((local - 3.2f) * 6).toInt()).coerceIn(0, 5)
            else -> 0
        }
        val hb = as0.hand[f]
        val hh = bh * .3f; val hw = hh * hb.width / hb.height
        img(hb, w * .8f - hw / 2f, by + bh * .99f - hh, hw, hh)
    }
    // Злые глаза в дуплах и в кусте: каждая пара моргает в своём ритме, иногда гаснет и загорается снова.
    if (Halloween.eyes.value) {
        TREE_EYES.forEachIndexed { i, (fx, fy, r) -> evilEyes(i, t, w * fx, by + bh * fy, r * density, roam = true) }
    }
    if (Halloween.crows.value) crows(t, w, by, bh)
    if (fogOn) fogBand(as0.fog1, by + bh * .8f, bh * .3f, 22f, t, lav.copy(alpha = .2f))
    // Ветки сверху, на них летучие мыши.
    val th = w / 3f
    as0.top?.let { img(it, 0f, 0f, w, th) }
    if (Halloween.bats.value) bats(t, w, th)
    // Пауки спускаются с веток на паутине по краю экрана в норы внизу.
    if (Halloween.webs.value) webSpider(t, w, th, by, bh)
}

/**
 * Пара красных глаз-точек с центром в ([cx], [cy]), как у паучков: на месте моргают (иногда дважды подряд),
 * при [roam] — то гаснут, то загораются. [r] — радиус точки.
 */
private fun DrawScope.evilEyes(i: Int, t: Float, cx: Float, cy: Float, r: Float, roam: Boolean, alpha: Float = 1f) {
    val vis = alpha * if (roam) ((sin(t * .19f + i * 2.3f) + .3f) / .35f).coerceIn(0f, 1f) else 1f
    if (vis <= .01f) return
    val p = 2.6f + (i * 1.37f) % 3.1f
    val ph = (t + i * 1.7f) % p
    val dbl = (((t + i * 1.7f) / p).toInt() + i) % 3 == 0
    // Открытость: 1 — открыты, 0 — закрыты; моргание ~0,25 с.
    fun lid(x: Float) = if (x < .25f) kotlin.math.abs(x - .125f) / .125f else 1f
    val open = when {
        ph < .25f -> lid(ph)
        dbl && ph in .38f..0.63f -> lid(ph - .38f)
        else -> 1f
    }.coerceAtLeast(.12f)
    val gap = r * 3.4f
    glow(EYE_RED, Offset(cx, cy), r * 5f, .28f * vis * open)
    for (sx in listOf(-1f, 1f)) {
        val c = Offset(cx + sx * gap / 2f, cy)
        scale(1f, open, c) { drawCircle(EYE_RED.copy(alpha = vis), r, c) }
        if (open > .6f) drawCircle(Color.White.copy(alpha = .5f * vis), r * .3f, c + Offset(-r * .3f, -r * .3f))
    }
}

/** Ворона в стайке: где сидит, куда летит, когда взлетела. */
private object Crows {
    const val DUR = 3.2f
    val at = intArrayOf(0, 3, 5)
    val to = intArrayOf(-1, -1, -1)
    val start = FloatArray(3)
    val next = floatArrayOf(9f, 19f, 29f)
    val rnd = Random(777)
    fun step(t: Float) {
        for (i in at.indices) {
            if (to[i] >= 0 && (t < start[i] || t - start[i] >= DUR)) { at[i] = to[i]; to[i] = -1; next[i] = t + 9f + rnd.nextFloat() * 14f }
            if (to[i] < 0 && t >= next[i]) {
                val busy = at.toSet() + to.filter { it >= 0 }
                val free = CROW_PERCHES.indices.filter { it !in busy }
                if (free.isNotEmpty()) { to[i] = free[rnd.nextInt(free.size)]; start[i] = t }
            }
            if (t + 80f < next[i]) next[i] = t + 6f
        }
    }
}

/** Вороны на ветках деревьев нижней каймы: сидят, чистят перья, каркают; иногда перелетают на другую ветку или дерево. */
private fun DrawScope.crows(t: Float, w: Float, by: Float, bh: Float) {
    val a = HalloweenAssets
    Crows.step(t)
    val cw = w * .065f
    fun perch(i: Int) = Offset(CROW_PERCHES[i].first * w, by + CROW_PERCHES[i].second * bh)
    fun drawCrow(f: ImageBitmap, feet: Offset, faceLeft: Boolean) {
        val x = feet.x - cw * .473f; val y = feet.y - cw * .925f
        if (faceLeft) scale(-1f, 1f, Offset(feet.x, feet.y)) { img(f, x, y, cw, cw) } else img(f, x, y, cw, cw)
    }
    for (i in Crows.at.indices) {
        val from = perch(Crows.at[i])
        val dest = Crows.to[i]
        if (dest < 0) {
            // На левом дереве смотрит вправо, на правом — влево (к центру экрана).
            val faceLeft = from.x > w / 2f
            val local = (t + i * 4.3f) % 11f
            val f = when {
                local < 1.4f -> a.crowCaw[intArrayOf(0, 1, 2, 1, 2, 0)[(local / .24f).toInt().coerceIn(0, 5)]]
                local in 5f..6.2f -> a.crowIdle[2]
                local in 8f..8.8f -> a.crowIdle[1]
                local in 9.6f..10f -> a.crowCaw[3]
                else -> a.crowIdle[0]
            }
            drawCrow(f, from, faceLeft)
            continue
        }
        val to = perch(dest)
        val u = ((t - Crows.start[i]) / Crows.DUR).coerceIn(0f, 1f)
        val left = to.x < from.x
        when {
            u < .14f -> drawCrow(a.crowUp[(u / .14f * 4).toInt().coerceIn(0, 3)], from, left)
            u > .84f -> drawCrow(a.crowLand[((u - .84f) / .16f * 4).toInt().coerceIn(0, 3)], to, left)
            else -> {
                val s = ((u - .14f) / .7f).let { it * it * (3 - 2 * it) }
                val c = Offset((from.x + to.x) / 2f, minOf(from.y, to.y) - bh * .35f)
                val pos = Offset(
                    (1 - s) * (1 - s) * from.x + 2 * (1 - s) * s * c.x + s * s * to.x,
                    (1 - s) * (1 - s) * from.y + 2 * (1 - s) * s * c.y + s * s * to.y,
                )
                drawCrow(a.crowFly[(t * 9f).toInt() % 4], pos, left)
            }
        }
    }
}

/** Кадр крысы/паука по имени анимации; null — если картинки не загрузились. */
private fun ratF(anim: String, i: Int) = HalloweenAssets.rat[anim]?.get(i.coerceIn(0, 3))
private fun spiF(anim: String, i: Int) = HalloweenAssets.spi[anim]?.get(i.coerceIn(0, 3))

/** Номер кадра 0..3 для доли [u] проигрывания и зацикленный кадр по времени. */
private fun seq(u: Float) = (u * 4f).toInt().coerceIn(0, 3)
private fun loop(t: Float, fps: Float = 10f) = (t * fps).toInt().mod(4)

/**
 * Рисует кадр зверька: низ кадра (лапы или край норы) — на линии [base], по центру [x]. Кадры смотрят вправо,
 * [faceLeft] отражает. [fw] — ширина кадра, [baseline] — где в кадре низ рисунка (доля высоты).
 */
private fun DrawScope.critter(b: ImageBitmap?, x: Float, base: Float, fw: Float, baseline: Float, faceLeft: Boolean = false) {
    b ?: return
    val fh = fw * b.height / b.width
    val l = x - fw / 2f; val tp = base - fh * baseline
    if (faceLeft) scale(-1f, 1f, Offset(x, base)) { img(b, l, tp, fw, fh) } else img(b, l, tp, fw, fh)
}
private const val RAT_BASE = .959f
private const val SPI_BASE = .97f
private const val OLD_SIDE_BASE = .955f

/**
 * Нижняя кайма: пауки спускаются с веток на паутине, в норах живёт крыса. Цикл ~46 с, по очереди:
 *  - паук идёт в нору с крысой → крыса скалится, паук пугается и бежит в ближайшую свободную нору;
 *  - паук сидит в норе (горят глаза), крыса идёт к нему → паук скалится, крыса бежит в ближайшую свободную;
 *  - просто паук: нора, глаза, уходит за край. Крыса иногда выглядывает из своей норы.
 */
private fun DrawScope.webSpider(t: Float, w: Float, th: Float, by: Float, bh: Float) {
    val a = HalloweenAssets
    val cycle = 46f
    val n = ((t + 6f) / cycle).toInt().coerceAtLeast(0)
    val lt = (t + 6f) % cycle
    val pos = BURROWS.map { Offset(it.first * w, by + it.second * bh) }
    val rim = pos.map { it + Offset(0f, bh * .03f) }   // край норы: здесь обрезаются кадры «выглядывает/ныряет»
    fun nearest(from: Offset, except: Set<Int>) = pos.indices.filter { it !in except }.minBy { kotlin.math.abs(pos[it].x - from.x) }
    val ratsOn = a.rat.size == 7 && a.spi.size == 5
    val stopGap = 18.dp.toPx()
    /** Куда убегает напуганная крыса: в ближайшую к ней свободную нору (кроме норы паука). */
    fun fleeHome(hm: Int, tg: Int): Int {
        val stopX = pos[tg].x + if (pos[hm].x < pos[tg].x) -stopGap else stopGap
        return nearest(Offset(stopX, 0f), setOf(tg))
    }
    // Где живёт крыса в начале каждого цикла (считается от начала, чтобы переходы совпадали).
    var home = 1
    var type = 0
    var target = 0
    for (c in 0..n) {
        val left = c % 2 == 0
        type = if (ratsOn) c % 3 else 2
        val sideBurrows = if (left) listOf(0, 1) else listOf(2, 1)
        target = when (type) { 0 -> home; else -> sideBurrows.first { it != home || !ratsOn } }
        if (c < n && type == 1) home = fleeHome(home, target)
    }
    val leftSide = n % 2 == 0
    val sx = (if (leftSide) .06f else .95f) * w
    val startY = th * .29f
    val ground = rim[target].y
    val speed = w * .11f
    val tw = 18.dp.toPx(); val tH = tw * a.spiderTop[0].height / a.spiderTop[0].width
    val sideW = 20.dp.toPx(); val spiW = 25.dp.toPx(); val ratW = 28.dp.toPx()
    val tAppear = .8f; val tDesc = 10f
    fun spiderWalk(x: Float, toLeft: Boolean, y: Float = ground) = critter(a.spiderSide[loop(t, 9f)], x, y, sideW, OLD_SIDE_BASE, toLeft)
    fun ratIdle(hm: Int) {
        // Крыса выглядывает из своей норы раз в ~11 с.
        val q = (t + hm * 3.7f) % 11f
        if (q < 2.4f) critter(ratF("peek", if (q < 1.2f) seq(q / 1.2f) else seq((2.4f - q) / 1.2f)), pos[hm].x, rim[hm].y, ratW, RAT_BASE)
    }
    var k = lt
    // 1. Спуск по паутине (голова вниз).
    if (k < tAppear + tDesc) {
        if (ratsOn) ratIdle(home)
        val sway = sin(t * 1.3f) * 3f * density
        val y = if (k < tAppear) startY else startY + (ground - startY - tH) * ((k - tAppear) / tDesc).let { it * it * (3 - 2 * it) }
        val x = sx + sway * ((y - startY) / (ground - startY)).coerceIn(0f, 1f)
        drawLine(LILAC.copy(alpha = .55f), Offset(sx, startY - 2f), Offset(x, y), 1f * density)
        val sc = if (k < tAppear) k / tAppear else 1f
        val f = a.spiderTop[if (k < tAppear) 0 else loop(t, 9f)]
        scale(sc, sc, Offset(x, y)) { rotate(180f, Offset(x, y + tH / 2f)) { img(f, x - tw / 2f, y, tw, tH) } }
        return
    }
    k -= tAppear + tDesc
    val tgt = pos[target]
    val fromLeft = sx < tgt.x
    if (type == 0) {
        // 2a. Паук идёт к норе, где сидит крыса.
        val stopX = tgt.x + if (fromLeft) -stopGap else stopGap
        val walk1 = (kotlin.math.abs(stopX - sx) / speed).coerceAtLeast(.4f)
        if (k < walk1) { ratIdle(home); spiderWalk(sx + (stopX - sx) * (k / walk1), !fromLeft); return }
        k -= walk1
        val scare = 1.4f
        if (k < scare) {
            val u = k / scare
            critter(ratF("snarl", seq(u)), tgt.x, rim[target].y, ratW, RAT_BASE, faceLeft = fromLeft)
            critter(spiF("scared", seq(u)), stopX, ground, spiW, SPI_BASE, faceLeft = !fromLeft)
            return
        }
        k -= scare
        val free = nearest(Offset(stopX, ground), setOf(target))
        val fx = pos[free].x
        val run = (kotlin.math.abs(fx - stopX) / (speed * 2.2f)).coerceAtLeast(.3f)
        if (k < run) {
            critter(ratF("dive", seq(k / run)), tgt.x, rim[target].y, ratW, RAT_BASE)
            critter(spiF("run", loop(t, 14f)), stopX + (fx - stopX) * (k / run), ground, spiW, SPI_BASE, faceLeft = fx < stopX)
            return
        }
        k -= run
        if (k < .6f) { critter(spiF("dive", seq(k / .6f)), fx, rim[free].y, spiW, SPI_BASE, faceLeft = fx < stopX); return }
        k -= .6f
        if (k < 6f) { evilEyes(free + 7, t, pos[free].x, pos[free].y, .9f * density, roam = false, alpha = (k / .5f).coerceAtMost(1f)); ratIdle(home); return }
        k -= 6f
        val edgeX = if (fx < w / 2f) -w * .06f else w * 1.06f
        if (k < .7f) { critter(spiF("climb", seq(k / .7f)), fx, rim[free].y, spiW, SPI_BASE, faceLeft = edgeX < fx); return }
        k -= .7f
        val walk2 = kotlin.math.abs(edgeX - fx) / speed
        if (k < walk2) spiderWalk(fx + (edgeX - fx) * (k / walk2), edgeX < fx, rim[free].y)
        ratIdle(home)
        return
    }
    // 2b. Паук идёт в свободную нору.
    val walk1 = (kotlin.math.abs(tgt.x - sx) / speed).coerceAtLeast(.6f)
    if (k < walk1) { if (ratsOn) ratIdle(home); spiderWalk(sx + (tgt.x - sx) * (k / walk1), !fromLeft); return }
    k -= walk1
    if (k < .6f) { if (ratsOn) ratIdle(home); critter(spiF("dive", seq(k / .6f)), tgt.x, rim[target].y, spiW, SPI_BASE, faceLeft = !fromLeft); return }
    k -= .6f
    val stay = if (type == 1) 9f else 7f
    if (k < stay) {
        if (type == 1) {
            // Крыса идёт к норе паука: паук скалится, крыса пугается и бежит в ближайшую свободную нору.
            val h = pos[home]
            val ratLeft = h.x < tgt.x
            val stopX = tgt.x + if (ratLeft) -stopGap else stopGap
            val newHome = fleeHome(home, target)
            var q = k - 1f
            if (q < 0f) { evilEyes(target + 7, t, tgt.x, tgt.y, .9f * density, roam = false, alpha = (k / .5f).coerceAtMost(1f)); return }
            if (q < .6f) { critter(ratF("climb", seq(q / .6f)), h.x, rim[home].y, ratW, RAT_BASE, faceLeft = !ratLeft); eyesIn(target, t, tgt); return }
            q -= .6f
            val walk = (kotlin.math.abs(stopX - h.x) / speed).coerceAtLeast(.4f)
            if (q < walk) { critter(ratF("walk", loop(t, 9f)), h.x + (stopX - h.x) * (q / walk), rim[home].y, ratW, RAT_BASE, faceLeft = !ratLeft); eyesIn(target, t, tgt); return }
            q -= walk
            if (q < 1.4f) {
                val u = q / 1.4f
                critter(spiF("snarl", seq(u)), tgt.x, rim[target].y, spiW, SPI_BASE, faceLeft = ratLeft)
                critter(ratF("scared", seq(u)), stopX, rim[target].y, ratW, RAT_BASE, faceLeft = !ratLeft)
                return
            }
            q -= 1.4f
            val nx = pos[newHome].x
            val run = (kotlin.math.abs(nx - stopX) / (speed * 2.2f)).coerceAtLeast(.3f)
            if (q < run) { critter(ratF("run", loop(t, 14f)), stopX + (nx - stopX) * (q / run), rim[target].y, ratW, RAT_BASE, faceLeft = nx < stopX); eyesIn(target, t, tgt); return }
            q -= run
            if (q < .6f) { critter(ratF("dive", seq(q / .6f)), nx, rim[newHome].y, ratW, RAT_BASE, faceLeft = nx < stopX); eyesIn(target, t, tgt); return }
            eyesIn(target, t, tgt)
            return
        }
        if (ratsOn) ratIdle(home)
        evilEyes(target + 7, t, tgt.x, tgt.y, .9f * density, roam = false, alpha = (k / .5f).coerceAtMost(1f))
        return
    }
    k -= stay
    val edgeX = if (tgt.x < w / 2f) -w * .06f else w * 1.06f
    val newHome = if (type == 1) fleeHome(home, target) else home
    if (ratsOn) ratIdle(newHome)
    if (k < .7f) { critter(spiF("climb", seq(k / .7f)), tgt.x, rim[target].y, spiW, SPI_BASE, faceLeft = edgeX < tgt.x); return }
    k -= .7f
    val walk2 = kotlin.math.abs(edgeX - tgt.x) / speed
    if (k < walk2) spiderWalk(tgt.x + (edgeX - tgt.x) * (k / walk2), edgeX < tgt.x, rim[target].y)
}

private fun DrawScope.eyesIn(i: Int, t: Float, at: Offset) = evilEyes(i + 7, t, at.x, at.y, .9f * density, roam = false)

/** Ведьма на метле медленно пролетает мимо луны под плашками — раз в ~75 с, то вправо, то влево. */
private fun DrawScope.witchFlight(t: Float, w: Float, h: Float, moon: Offset, mr: Float) {
    val a = HalloweenAssets
    if (a.witch.size < 8) return
    val period = 75f; val dur = 11f
    val n = ((t + 25f) / period).toInt()
    val local = (t + 25f) % period
    if (local > dur) return
    val ww = w * .3f; val wh = ww * a.witch[0].height / a.witch[0].width
    val u = local / dur
    val toRight = n % 2 == 0
    val xc = if (toRight) -ww + u * (w + 2 * ww) else w + ww - u * (w + 2 * ww)
    val yc = moon.y + mr * .15f - sin(u * PI.toFloat()) * mr * .5f + sin(t * 2f) * mr * .06f
    val nearMoon = kotlin.math.abs(xc - moon.x) < mr * 1.6f
    val f = when {
        nearMoon -> a.witch[4 + ((t * 3f).toInt() % 2)]
        n % 3 == 1 -> a.witch[6 + ((t * 2f).toInt() % 2)]
        else -> a.witch[(t * 8f).toInt() % 4]
    }
    val x = xc - ww / 2f; val y = yc - wh / 2f
    if (toRight) img(f, x, y, ww, wh) else scale(-1f, 1f, Offset(xc, yc)) { img(f, x, y, ww, wh) }
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
    // Свой слой: такт анимации перерисовывает только фон, а прокрутка карточек не перерисовывает фон.
    Canvas(modifier.graphicsLayer()) { halloweenScene(Holiday.clock.floatValue, dark) }
}

/** Где норы на картинках шапок: центр (доли ширины и высоты картинки). */
private val CAP_HOLES = listOf(
    listOf(.257f to .79f), listOf(.749f to .79f), listOf(.287f to .795f), listOf(.725f to .8f),
    listOf(.231f to .845f, .798f to .845f), listOf(.203f to .841f, .824f to .841f),
    listOf(.194f to .841f, .804f to .841f), listOf(.172f to .845f, .784f to .845f),
)

/**
 * Шапка плашки в хэллоуинский режим: холмик со мхом, травой и грибами садится на верхний край плашки
 * (трава и грибы торчат над ней), в холмике — норы. Время от времени из норы выглядывает и выползает паучок:
 * при двух норах перебегает по холмику в другую, при одной — гуляет по плашке и возвращается в ту же.
 * Ставится ДО обрезки плашки по форме — так шапка может выступать над её краем.
 */
fun Modifier.halloweenCap(seed: Int, force: Boolean = false): Modifier = drawWithContent {
    drawContent()
    if (!force && !Halloween.active) return@drawWithContent
    val a = HalloweenAssets
    if (!a.ready.value || a.caps.size < 8 || a.spiderSide.size < 4 || a.spiderTop.size < 4 || a.spiderPeek.size < 3) {
        proceduralCap(seed, force); return@drawWithContent
    }
    val rnd = Random(seed)
    val w = size.width
    val capH = 22.dp.toPx()
    val natural = capH * a.caps[0].width / a.caps[0].height
    // Целые полосы — с началом и концом: по ширине плашки укладывается целое число полос (чуть растянутых или сжатых).
    val ov = .06f
    val count = maxOf(1, kotlin.math.round(w / (natural * (1 - ov))).toInt())
    val pieceW = w / (count - (count - 1) * ov)
    // Самые глубокие ложбинки холмика (~80 % высоты картинки) приходятся на верхний край плашки — рамки не видно.
    val top = -capH * .8f
    var k = seed.mod(8)
    var x = 0f
    val holes = ArrayList<Offset>()
    repeat(count) {
        img(a.caps[k], x, top, pieceW, capH)
        CAP_HOLES[k].forEach { (hx, hy) ->
            val p = Offset(x + hx * pieceW, top + hy * capH)
            if (p.x > 10.dp.toPx() && p.x < w - 10.dp.toPx()) holes += p
        }
        x += pieceW * (1 - ov)
        k = (k + 3) % 8
    }
    if ((!Halloween.spiders.value && !force) || holes.isEmpty()) return@drawWithContent
    capLife(seed, holes, capH, Holiday.clock.floatValue)
}

/**
 * Жизнь на шапке: паучки и крысы. Кто живёт на плашке, решает её номер:
 *  - одна нора — паучок (гуляет по плашке петлёй) или крыса (выбегает, осматривается и возвращается);
 *  - две и больше — паучок или крыса перебегают в соседнюю нору, а на части плашек они встречаются:
 *    кто подошёл к занятой норе, пугается хозяина (тот выглядывает и скалится) и убегает в ближайшую свободную нору.
 */
private fun DrawScope.capLife(seed: Int, holes: List<Offset>, capH: Float, t: Float) {
    val a = HalloweenAssets
    val ratsOn = a.rat.size == 7 && a.spi.size == 5
    val period = 16f + seed.mod(5)
    val n = ((t + seed.mod(97)) / period).toInt()
    val local = (t + seed.mod(97)) % period
    val rimDy = capH * .08f                 // край норы — чуть ниже центра тёмного проёма
    val feetDy = -capH * .06f               // по холмику лапы идут чуть выше норы
    val ratW = 26.dp.toPx(); val spiW = 23.dp.toPx(); val sideW = 18.dp.toPx()
    val peekW = 18.dp.toPx(); val peekH = peekW * a.spiderPeek[0].height / a.spiderPeek[0].width
    val speed = 34.dp.toPx()                // в секунду
    fun oldPeek(at: Offset, u: Float) = img(a.spiderPeek[(u * 3).toInt().coerceIn(0, 2)], at.x - peekW / 2f, at.y + rimDy - peekH, peekW, peekH)
    fun spiderSide(x: Float, y: Float, toLeft: Boolean) = critter(a.spiderSide[loop(t, 10f)], x, y, sideW, OLD_SIDE_BASE, toLeft)
    val i0 = seed.mod(holes.size).let { if (holes.size >= 2 && it == holes.lastIndex) it - 1 else it }
    val first = holes[i0]
    val kind = seed.mod(3)                  // 0 — паучок, 1 — крыса, 2 — встреча (если нор хватает)
    if (holes.size == 1 || !ratsOn || kind != 2) {
        val rat = ratsOn && kind == 1
        if (local > 6f) return
        if (holes.size >= 2) {
            // Перебежка в соседнюю нору.
            val b = holes[i0 + 1]
            val left = b.x < first.x
            when {
                local < .7f -> if (rat) critter(ratF("peek", seq(local / .7f)), first.x, first.y + rimDy, ratW, RAT_BASE, left) else oldPeek(first, local / .7f)
                local > 5.3f -> if (rat) critter(ratF("dive", seq((local - 5.3f) / .7f)), b.x, b.y + rimDy, ratW, RAT_BASE, left) else oldPeek(b, (6f - local) / .7f)
                else -> {
                    val u = ((local - .7f) / 4.6f).let { it * it * (3 - 2 * it) }
                    val x = first.x + (b.x - first.x) * u
                    val y = first.y + feetDy + sin(u * PI.toFloat() * 5f) * .6f * density
                    if (rat) critter(ratF("walk", loop(t, 9f)), x, y, ratW, RAT_BASE, left) else spiderSide(x, y, left)
                }
            }
            return
        }
        if (rat) {
            // Одна нора: крыса выглядывает, выбегает, осматривается и возвращается в ту же нору.
            val out = 22.dp.toPx() * if ((seed and 2) == 0) 1f else -1f
            val left = out < 0
            when {
                local < .7f -> critter(ratF("peek", seq(local / .7f)), first.x, first.y + rimDy, ratW, RAT_BASE, left)
                local < 1.3f -> critter(ratF("climb", seq((local - .7f) / .6f)), first.x, first.y + rimDy, ratW, RAT_BASE, left)
                local < 2.6f -> critter(ratF("walk", loop(t, 9f)), first.x + out * ((local - 1.3f) / 1.3f), first.y + feetDy, ratW, RAT_BASE, left)
                local < 3.6f -> critter(ratF("peek", 3), first.x + out, first.y + feetDy, ratW, RAT_BASE, left)
                local < 4.9f -> critter(ratF("walk", loop(t, 9f)), first.x + out * (1f - (local - 3.6f) / 1.3f), first.y + feetDy, ratW, RAT_BASE, !left)
                else -> critter(ratF("dive", seq((local - 4.9f) / 1.1f)), first.x, first.y + rimDy, ratW, RAT_BASE, !left)
            }
            return
        }
        // Одна нора, паучок: гуляет по плашке петлёй и возвращается.
        when {
            local < .7f -> oldPeek(first, local / .7f)
            local > 5.3f -> oldPeek(first, (6f - local) / .7f)
            else -> {
                val u = (local - .7f) / 4.6f
                val ang = u * 2f * PI.toFloat()
                val r = 26.dp.toPx()
                val pos = Offset(first.x + r * 1.5f * sin(ang), first.y + r * (1 - cos(ang)))
                val d = Offset(r * 1.5f * cos(ang), r * sin(ang))
                val heading = atan2(d.y, d.x) * 180f / PI.toFloat() + 90f
                val f = a.spiderTop[loop(t, 10f)]
                val tw = 16.dp.toPx(); val th = tw * f.height / f.width
                rotate(heading, pos) { img(f, pos.x - tw / 2f, pos.y - th / 2f, tw, th) }
            }
        }
        return
    }
    // Встреча. Хозяин сидит в норе A, гость выходит из норы B и идёт к A.
    val ratVisits = n % 2 == 0
    val home = holes[i0]; val from = holes[i0 + 1]
    val gLeft = home.x < from.x                         // гость идёт влево?
    val stopX = home.x + if (gLeft) 16.dp.toPx() else -16.dp.toPx()
    val walkT = (kotlin.math.abs(stopX - from.x) / speed).coerceIn(.6f, 4f)
    var k = local
    fun guestOut(u: Float) = if (ratVisits) critter(ratF("climb", seq(u)), from.x, from.y + rimDy, ratW, RAT_BASE, gLeft)
        else critter(spiF("climb", seq(u)), from.x, from.y + rimDy, spiW, SPI_BASE, gLeft)
    if (k < .8f) { guestOut(k / .8f); return }
    k -= .8f
    if (k < walkT) {
        val x = from.x + (stopX - from.x) * (k / walkT)
        if (ratVisits) critter(ratF("walk", loop(t, 9f)), x, from.y + feetDy, ratW, RAT_BASE, gLeft) else spiderSide(x, from.y + feetDy, gLeft)
        return
    }
    k -= walkT
    val scare = 1.4f
    if (k < scare) {
        val u = k / scare
        if (ratVisits) {
            critter(spiF("snarl", seq(u)), home.x, home.y + rimDy, spiW, SPI_BASE, !gLeft)
            critter(ratF("scared", seq(u)), stopX, from.y + feetDy, ratW, RAT_BASE, gLeft)
        } else {
            critter(ratF("snarl", seq(u)), home.x, home.y + rimDy, ratW, RAT_BASE, !gLeft)
            critter(spiF("scared", seq(u)), stopX, from.y + feetDy, spiW, SPI_BASE, gLeft)
        }
        return
    }
    k -= scare
    // Бежит в ближайшую к нему свободную нору (кроме занятой хозяином).
    val free = holes.filter { it != home }.minBy { kotlin.math.abs(it.x - stopX) }
    val runT = (kotlin.math.abs(free.x - stopX) / (speed * 2.4f)).coerceIn(.3f, 2f)
    val runLeft = free.x < stopX
    if (k < runT) {
        val x = stopX + (free.x - stopX) * (k / runT)
        if (ratVisits) {
            critter(spiF("dive", seq(k / runT)), home.x, home.y + rimDy, spiW, SPI_BASE, !gLeft)
            critter(ratF("run", loop(t, 14f)), x, from.y + feetDy, ratW, RAT_BASE, runLeft)
        } else {
            critter(ratF("dive", seq(k / runT)), home.x, home.y + rimDy, ratW, RAT_BASE, !gLeft)
            critter(spiF("run", loop(t, 14f)), x, from.y + feetDy, spiW, SPI_BASE, runLeft)
        }
        return
    }
    k -= runT
    if (k < .6f) {
        if (ratVisits) critter(ratF("dive", seq(k / .6f)), free.x, free.y + rimDy, ratW, RAT_BASE, runLeft)
        else critter(spiF("dive", seq(k / .6f)), free.x, free.y + rimDy, spiW, SPI_BASE, runLeft)
    }
}

/** Запасная шапка, пока картинки не загрузились: нарисованный холмик с норами и паучком. */
private fun DrawScope.proceduralCap(seed: Int, force: Boolean) {
    val rnd = Random(seed)
    val ch = 9.dp.toPx()
    val w = size.width
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
    val two = (seed and 1) == 1
    val holes = if (two) listOf(w * (.15f + rnd.nextFloat() * .15f), w * (.62f + rnd.nextFloat() * .2f)) else listOf(w * (.25f + rnd.nextFloat() * .5f))
    val hw = 13.dp.toPx(); val hh = 6.dp.toPx(); val hy = ch * .62f
    holes.forEach { hx ->
        drawOval(Color(0xFF0D0711), Offset(hx - hw / 2f, hy - hh / 2f), Size(hw, hh))
        drawArc(LIME.copy(alpha = .7f), 190f, 160f, false, Offset(hx - hw / 2f, hy - hh / 2f), Size(hw, hh), style = Stroke(1f * density))
    }
    if (!Halloween.spiders.value && !force) return
    val t = Holiday.clock.floatValue
    val local = (t + seed.mod(97)) % (15f + seed.mod(5))
    if (local > 7f) return
    val a = holes[0]
    val u = ((local - .7f) / 5.6f).coerceIn(0f, 1f)
    val sc = when { local < .7f -> local / .7f; local > 6.3f -> (7f - local) / .7f; else -> 1f }
    val pos = if (two) Offset(a + (holes[1] - a) * u * u * (3 - 2 * u), ch * .3f) else Offset(a + 39.dp.toPx() * sin(u * 6.283f), hy + 26.dp.toPx() * (1 - cos(u * 6.283f)))
    spider(pos, 0f, sc.coerceIn(0f, 1f), t, 4.2f * density)
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
            Fall(kind, r.nextInt(6), r.nextFloat(), -.06f - speed * r.nextFloat() * 5f, speed, when (kind) { 2 -> 10f; 1 -> 22f; else -> 14f + r.nextFloat() * 8f },
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
