package com.github.op88.smartcopy.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** DataStore instance — one per app, process-safe. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smartcopy_prefs")

/**
 * Preferences
 *
 * Type-safe DataStore wrapper for all Smart Copy user settings.
 * All reads return [Flow]s; use [clipboardTtlSeconds] for synchronous
 * one-shot reads inside coroutines.
 *
 * Keys correspond to the preferences.xml entries for UI binding.
 */
class Preferences(private val context: Context) {

    companion object {
        // ── Clipboard TTL ──────────────────────────────────────────────────
        /** Seconds before clipboard is auto-wiped. 0 = disabled. */
        val KEY_CLIPBOARD_TTL   = longPreferencesKey("clipboard_ttl_seconds")
        val DEFAULT_CLIPBOARD_TTL = 30L

        // ── Edge Bubble ────────────────────────────────────────────────────
        val KEY_EDGE_BUBBLE_ENABLED = booleanPreferencesKey("edge_bubble_enabled")
        val KEY_EDGE_BUBBLE_RIGHT   = booleanPreferencesKey("edge_bubble_right")

        // ── Magnifier ─────────────────────────────────────────────────────
        val KEY_MAGNIFIER_ZOOM = floatPreferencesKey("magnifier_zoom")
        val DEFAULT_MAGNIFIER_ZOOM = 3.0f

        // ── Haptic ────────────────────────────────────────────────────────
        val KEY_HAPTIC = booleanPreferencesKey("haptic_feedback")
    }

    // ── Flows (for observing preference changes in UI) ────────────────────

    val clipboardTtlFlow: Flow<Long> = context.dataStore.data
        .map { prefs -> prefs[KEY_CLIPBOARD_TTL] ?: DEFAULT_CLIPBOARD_TTL }

    val edgeBubbleEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_EDGE_BUBBLE_ENABLED] ?: true }

    val edgeBubbleRightFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_EDGE_BUBBLE_RIGHT] ?: true }

    val magnifierZoomFlow: Flow<Float> = context.dataStore.data
        .map { prefs -> prefs[KEY_MAGNIFIER_ZOOM] ?: DEFAULT_MAGNIFIER_ZOOM }

    val hapticFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_HAPTIC] ?: true }

    // ── One-shot coroutine reads (for use in background workers) ──────────

    suspend fun clipboardTtlSeconds(): Long =
        context.dataStore.data.first()[KEY_CLIPBOARD_TTL] ?: DEFAULT_CLIPBOARD_TTL

    suspend fun isMagnifierEnabled(): Boolean =
        (context.dataStore.data.first()[KEY_MAGNIFIER_ZOOM] ?: DEFAULT_MAGNIFIER_ZOOM) > 0f

    // ── Writes ─────────────────────────────────────────────────────────────

    suspend fun setClipboardTtl(seconds: Long) {
        context.dataStore.edit { prefs -> prefs[KEY_CLIPBOARD_TTL] = seconds }
    }

    suspend fun setEdgeBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_EDGE_BUBBLE_ENABLED] = enabled }
    }

    suspend fun setEdgeBubbleRight(right: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_EDGE_BUBBLE_RIGHT] = right }
    }

    suspend fun setMagnifierZoom(zoom: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_MAGNIFIER_ZOOM] = zoom.coerceIn(1f, 5f) }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_HAPTIC] = enabled }
    }
}
