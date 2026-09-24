package com.dasein.poryadok

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dasein.poryadok.ui.AppRoot
import com.dasein.poryadok.ui.LockScreen
import com.dasein.poryadok.ui.theme.PoryadokTheme

object Lock {
    val unlocked = mutableStateOf(false)
    private var stoppedAt = 0L

    fun onStop() { stoppedAt = System.currentTimeMillis() }
    fun onStart() {
        if (stoppedAt != 0L && System.currentTimeMillis() - stoppedAt > 30_000) unlocked.value = false
    }
}

class MainActivity : FragmentActivity() {
    /** Экран, который нужно открыть из уведомления или виджета. */
    val deepLink = mutableStateOf<String?>(null)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Graph.init(this)
        deepLink.value = intent?.getStringExtra(EXTRA_ROUTE)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            val settings by Graph.prefs.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            PoryadokTheme(theme = s.theme, accent = s.accent) {
                val unlocked by Lock.unlocked
                if (s.pinHash.isNotEmpty() && !unlocked) {
                    LockScreen(this, s) { Lock.unlocked.value = true }
                } else {
                    AppRoot(s, deepLink)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { deepLink.value = it }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
