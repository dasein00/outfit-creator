package com.dasein.poryadok.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

data class Settings(
    val theme: String = "system",
    val accent: Int = 0,
    val name: String = "",
    val currency: String = "₽",
    val pinHash: String = "",
    val biometric: Boolean = false,
    val focusWork: Int = 25,
    val focusBreak: Int = 5,
    val focusLong: Int = 15,
    val summaryHour: Int = 8,
    val summaryOn: Boolean = true,
    val eveningReviewOn: Boolean = true,
    val lastRecurringDay: Long = 0,
    val wardrobeSeed: Int = 0,
    val onboarded: Boolean = false,
    val focusEndsAt: Long = 0,
    val focusPhase: String = "",
    val focusTaskId: Long = 0,
    val focusCycle: Int = 0,
)

class Prefs(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = intPreferencesKey("accent")
        val name = stringPreferencesKey("name")
        val currency = stringPreferencesKey("currency")
        val pinHash = stringPreferencesKey("pinHash")
        val biometric = booleanPreferencesKey("biometric")
        val focusWork = intPreferencesKey("focusWork")
        val focusBreak = intPreferencesKey("focusBreak")
        val focusLong = intPreferencesKey("focusLong")
        val summaryHour = intPreferencesKey("summaryHour")
        val summaryOn = booleanPreferencesKey("summaryOn")
        val eveningReviewOn = booleanPreferencesKey("eveningReviewOn")
        val lastRecurringDay = longPreferencesKey("lastRecurringDay")
        val wardrobeSeed = intPreferencesKey("wardrobeSeed")
        val onboarded = booleanPreferencesKey("onboarded")
        val focusEndsAt = longPreferencesKey("focusEndsAt")
        val focusPhase = stringPreferencesKey("focusPhase")
        val focusTaskId = longPreferencesKey("focusTaskId")
        val focusCycle = intPreferencesKey("focusCycle")
    }

    val settings: Flow<Settings> = context.store.data.map { p -> p.toSettings() }

    suspend fun now(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        theme = this[K.theme] ?: "system",
        accent = this[K.accent] ?: 0,
        name = this[K.name] ?: "",
        currency = this[K.currency] ?: "₽",
        pinHash = this[K.pinHash] ?: "",
        biometric = this[K.biometric] ?: false,
        focusWork = this[K.focusWork] ?: 25,
        focusBreak = this[K.focusBreak] ?: 5,
        focusLong = this[K.focusLong] ?: 15,
        summaryHour = this[K.summaryHour] ?: 8,
        summaryOn = this[K.summaryOn] ?: true,
        eveningReviewOn = this[K.eveningReviewOn] ?: true,
        lastRecurringDay = this[K.lastRecurringDay] ?: 0,
        wardrobeSeed = this[K.wardrobeSeed] ?: 0,
        onboarded = this[K.onboarded] ?: false,
        focusEndsAt = this[K.focusEndsAt] ?: 0,
        focusPhase = this[K.focusPhase] ?: "",
        focusTaskId = this[K.focusTaskId] ?: 0,
        focusCycle = this[K.focusCycle] ?: 0,
    )

    suspend fun update(block: (Settings) -> Settings) {
        context.store.edit { p ->
            val s = block(p.toSettings())
            p[K.theme] = s.theme
            p[K.accent] = s.accent
            p[K.name] = s.name
            p[K.currency] = s.currency
            p[K.pinHash] = s.pinHash
            p[K.biometric] = s.biometric
            p[K.focusWork] = s.focusWork
            p[K.focusBreak] = s.focusBreak
            p[K.focusLong] = s.focusLong
            p[K.summaryHour] = s.summaryHour
            p[K.summaryOn] = s.summaryOn
            p[K.eveningReviewOn] = s.eveningReviewOn
            p[K.lastRecurringDay] = s.lastRecurringDay
            p[K.wardrobeSeed] = s.wardrobeSeed
            p[K.onboarded] = s.onboarded
            p[K.focusEndsAt] = s.focusEndsAt
            p[K.focusPhase] = s.focusPhase
            p[K.focusTaskId] = s.focusTaskId
            p[K.focusCycle] = s.focusCycle
        }
    }
}
