package com.dasein.poryadok.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dasein.poryadok.R
import com.dasein.poryadok.ui.theme.LocalExtra
import com.dasein.poryadok.ui.theme.Palette

/** Набор иконок приложения (из фирменного спрайта DASEIN). */
object Ic {
    val home = R.drawable.ic_home
    val calendar = R.drawable.ic_calendar
    val check = R.drawable.ic_check
    val stats = R.drawable.ic_stats
    val pie = R.drawable.ic_pie
    val grid = R.drawable.ic_grid
    val search = R.drawable.ic_search
    val settings = R.drawable.ic_settings
    val flame = R.drawable.ic_flame
    val target = R.drawable.ic_target
    val timer = R.drawable.ic_timer
    val salad = R.drawable.ic_salad
    val scale = R.drawable.ic_scale
    val dumbbell = R.drawable.ic_dumbbell
    val smile = R.drawable.ic_smile
    val moon = R.drawable.ic_moon
    val drop = R.drawable.ic_drop
    val notebook = R.drawable.ic_notebook
    val trophy = R.drawable.ic_trophy
    val hanger = R.drawable.ic_hanger
    val wheel = R.drawable.ic_wheel
    val review = R.drawable.ic_review
    val bell = R.drawable.ic_bell
    val sliders = R.drawable.ic_sliders
    val wallet = R.drawable.ic_wallet
    val coins = R.drawable.ic_coins
    val bank = R.drawable.ic_bank
    val card = R.drawable.ic_card
    val receipt = R.drawable.ic_receipt
    val leaves = R.drawable.ic_leaves
    val heart = R.drawable.ic_heart
    val people = R.drawable.ic_people
    val cart = R.drawable.ic_cart
    val globe = R.drawable.ic_globe
    val pin = R.drawable.ic_pin
    val map = R.drawable.ic_map
    val camera = R.drawable.ic_camera
    val image = R.drawable.ic_image
    val document = R.drawable.ic_document
    val folder = R.drawable.ic_folder
    val sun = R.drawable.ic_sun
    val rain = R.drawable.ic_rain
    val thermo = R.drawable.ic_thermo
    val book = R.drawable.ic_book
    val bulb = R.drawable.ic_bulb
    val cap = R.drawable.ic_cap
    val gift = R.drawable.ic_gift
    val trash = R.drawable.ic_trash
}

/**
 * Иконка из набора. Кремовые детали плохо видны на светлом фоне,
 * поэтому в светлой теме иконка по умолчанию лежит на тёмной плашке.
 */
@Composable
fun AppIcon(
    @DrawableRes res: Int,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
    badge: Boolean = !LocalExtra.current.dark,
    dimmed: Boolean = false,
) {
    val a = if (dimmed) .5f else 1f
    if (badge) {
        Box(
            modifier.size(size * 1.45f).clip(RoundedCornerShape(size * .4f)).background(Palette.Ink2).alpha(a),
            contentAlignment = Alignment.Center,
        ) { Image(painterResource(res), null, Modifier.size(size)) }
    } else {
        Image(painterResource(res), null, modifier.size(size).alpha(a))
    }
}

/** Кнопка с иконкой набора для верхней панели. */
@Composable
fun IconAction(@DrawableRes res: Int, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Image(painterResource(res), contentDescription = description, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun Empty(@DrawableRes icon: Int, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(icon, 44.dp, badge = true)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, color = LocalExtra.current.dim, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}
