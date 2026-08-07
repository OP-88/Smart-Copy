package com.github.op88.smartcopy.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import com.github.op88.smartcopy.R
import com.github.op88.smartcopy.overlay.EdgeBubbleService
import kotlinx.coroutines.launch

/**
 * SettingsActivity
 *
 * Hosts [SettingsFragment] which renders the preferences.xml hierarchy
 * using AndroidX PreferenceFragmentCompat.
 *
 * All preference changes are persisted to DataStore via [Preferences].
 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

/**
 * SettingsFragment
 *
 * Renders all Smart Copy preferences and wires them to [Preferences] DataStore.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var prefs: Preferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        prefs = Preferences(requireContext())
        wirePreferences()
    }

    private fun wirePreferences() {
        // Clipboard TTL
        findPreference<ListPreference>("clipboard_ttl")?.setOnPreferenceChangeListener { _, newValue ->
            val seconds = (newValue as String).toLongOrNull() ?: 30L
            lifecycleScope.launch { prefs.setClipboardTtl(seconds) }
            true
        }

        // Edge bubble toggle — immediately starts or stops the lightweight bubble service
        findPreference<SwitchPreferenceCompat>("edge_bubble_enabled")?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            lifecycleScope.launch { prefs.setEdgeBubbleEnabled(enabled) }
            if (enabled) {
                EdgeBubbleService.start(requireContext())
            } else {
                EdgeBubbleService.stop(requireContext())
            }
            true
        }

        // Edge bubble side — restart service to pick up the new dock side
        findPreference<ListPreference>("edge_bubble_side")?.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch { prefs.setEdgeBubbleRight(newValue == "right") }
            // Restart the bubble service so it re-attaches on the correct side
            EdgeBubbleService.stop(requireContext())
            EdgeBubbleService.start(requireContext())
            true
        }

        // Magnifier zoom
        findPreference<SeekBarPreference>("magnifier_zoom")?.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch { prefs.setMagnifierZoom((newValue as Int).toFloat()) }
            true
        }

        // Haptic feedback
        findPreference<SwitchPreferenceCompat>("haptic_feedback")?.setOnPreferenceChangeListener { _, newValue ->
            lifecycleScope.launch { prefs.setHapticFeedback(newValue as Boolean) }
            true
        }
    }
}
