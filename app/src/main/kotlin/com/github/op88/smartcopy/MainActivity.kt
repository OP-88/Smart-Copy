package com.github.op88.smartcopy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.op88.smartcopy.overlay.OverlayService
import com.github.op88.smartcopy.settings.SettingsActivity
import com.github.op88.smartcopy.ui.theme.SmartCopyTheme

/**
 * MainActivity — Permission gate + launcher hub.
 *
 * Responsibilities:
 *  1. Check SYSTEM_ALERT_WINDOW permission on first launch.
 *  2. Request MediaProjection via startActivityForResult (stored for OverlayService).
 *  3. Provide quick-launch buttons for the overlay and settings.
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** Extra set by [SmartCopyTileService] to auto-launch the overlay immediately. */
        const val EXTRA_QS_TRIGGERED = "qs_triggered"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartCopyTheme {
                SmartCopyHome(
                    onLaunchOverlay = ::launchOverlay,
                    onOpenSettings = ::openSettings,
                )
            }
        }
        // Handle case where QS tile launched us cold (first onCreate)
        if (intent?.getBooleanExtra(EXTRA_QS_TRIGGERED, false) == true) {
            launchOverlay()
        }
    }

    /**
     * Called when the QS tile fires and the activity is already in the back stack.
     * Auto-launches the overlay immediately without requiring a button tap.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_QS_TRIGGERED, false)) {
            launchOverlay()
        }
    }

    private fun launchOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            // Prompt user to grant SYSTEM_ALERT_WINDOW
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        Intent(this, OverlayService::class.java).also { startForegroundService(it) }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Composable UI
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun SmartCopyHome(
    onLaunchOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val hasOverlayPermission = remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A0F), Color(0xFF111827))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Monogram badge — matches the actual S|C logo
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color(0xFF000000),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFFFFF)),
                tonalElevation = 4.dp,
                modifier = Modifier.size(80.dp),
            ) {
                Row(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "S",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        color = Color(0xFFFFFFFF),
                    )
                    Text(
                        text = "|",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Thin,
                        fontSize = 22.sp,
                        color = Color(0xFFFFFFFF),
                        modifier = Modifier.padding(horizontal = 1.dp),
                    )
                    Text(
                        text = "C",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        color = Color(0xFFFFFFFF),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Smart Copy",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF1F5F9),
            )
            Text(
                text = "Privacy-first • 100% Offline • Zero Ads",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(Modifier.height(8.dp))

            // Permission status card
            if (!hasOverlayPermission.value) {
                PermissionCard(onGrantClick = onLaunchOverlay)
            } else {
                StatusCard()
            }

            Spacer(Modifier.height(8.dp))

            // Launch overlay button
            Button(
                onClick = onLaunchOverlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
            ) {
                Text(
                    text = "▶  Launch Smart Copy",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0A0A0F),
                    fontSize = 16.sp,
                )
            }

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(text = "⚙  Settings", color = Color(0xFFCBD5E1), fontSize = 16.sp)
            }

            Spacer(Modifier.weight(1f))

            // Footer
            Text(
                text = "Open Source · Zero Telemetry · No Network",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF475569),
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun PermissionCard(onGrantClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1917)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚠ Overlay Permission Required", color = Color(0xFFFCA5A5), fontWeight = FontWeight.SemiBold)
            Text(
                "Smart Copy needs \"Display over other apps\" permission to freeze the screen and show the OCR overlay.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
            )
            TextButton(onClick = onGrantClick) {
                Text("Grant Permission →", color = Color(0xFF38BDF8))
            }
        }
    }
}

@Composable
private fun StatusCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2027)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22D3EE)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("✓ Ready", color = Color(0xFF4ADE80), fontWeight = FontWeight.SemiBold)
            listOf(
                "Overlay permission granted",
                "OCR engine loaded (offline)",
                "Clipboard TTL active",
                "Zero network permissions",
            ).forEach { item ->
                Text("  · $item", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
            }
        }
    }
}
