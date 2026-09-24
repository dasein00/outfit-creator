package com.dasein.poryadok.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dasein.poryadok.data.Settings
import com.dasein.poryadok.ui.theme.LocalExtra
import java.security.MessageDigest

object Pin {
    fun hash(pin: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(("poryadok:" + pin).toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    fun canUseBiometric(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun askBiometric(activity: FragmentActivity, onOk: () -> Unit) {
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Порядок")
            .setSubtitle("Подтвердите, что это вы")
            .setNegativeButtonText("Ввести PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun PinPad(
    title: String,
    subtitle: String,
    error: Boolean,
    resetKey: Int = 0,
    onBiometric: (() -> Unit)? = null,
    onComplete: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    LaunchedEffect(resetKey) { pin = "" }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🔒", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(subtitle, color = if (error) LocalExtra.current.danger else LocalExtra.current.dim)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.size(16.dp).then(
                        if (i < pin.length) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier.border(2.dp, LocalExtra.current.dim, CircleShape)
                    )
                )
            }
        }
        Spacer(Modifier.height(30.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Surface(
                        shape = CircleShape,
                        color = if (k.length == 1) LocalExtra.current.card else Color.Transparent,
                        modifier = Modifier.size(72.dp).clickable(enabled = k != "bio" || onBiometric != null) {
                            when (k) {
                                "del" -> pin = pin.dropLast(1)
                                "bio" -> onBiometric?.invoke()
                                else -> if (pin.length < 4) {
                                    pin += k
                                    if (pin.length == 4) onComplete(pin)
                                }
                            }
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when (k) {
                                "del" -> Icon(Icons.AutoMirrored.Filled.Backspace, "Стереть")
                                "bio" -> if (onBiometric != null) Icon(Icons.Default.Fingerprint, "Отпечаток", tint = MaterialTheme.colorScheme.primary)
                                else -> Text(k, fontSize = 26.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LockScreen(activity: FragmentActivity, settings: Settings, onUnlock: () -> Unit) {
    var attempts by remember { mutableStateOf(0) }
    val error = attempts > 0
    val bio = settings.biometric && remember { Pin.canUseBiometric(activity) }
    LaunchedEffect(Unit) { if (bio) Pin.askBiometric(activity, onUnlock) }
    PinPad(
        title = "Введите PIN",
        subtitle = if (error) "Неверный PIN" else "Ваши данные под защитой",
        error = error,
        resetKey = attempts,
        onBiometric = if (bio) ({ Pin.askBiometric(activity, onUnlock) }) else null,
    ) { entered ->
        if (Pin.hash(entered) == settings.pinHash) onUnlock() else attempts++
    }
}
