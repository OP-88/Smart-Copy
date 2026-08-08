package com.github.op88.smartcopy

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.github.op88.smartcopy.overlay.OverlayService
import com.github.op88.smartcopy.settings.SettingsActivity
import com.github.op88.smartcopy.ui.theme.SmartCopyTheme

/**
 * MainActivity — Permission gate + launcher hub.
 *
 * Permission flow:
 *  1. SYSTEM_ALERT_WINDOW — must be granted first (checked on resume).
 *  2. MediaProjection consent — shown when user taps "Launch Smart Copy".
 *     Android 14+ requires the FGS to be started from the activity result
 *     callback, so we use ActivityResultLauncher here.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_QS_TRIGGERED = "qs_triggered"
    }

    // Step 2: receives the screen-capture consent result and starts the service
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startForegroundService(
                OverlayService.buildIntent(this, result.resultCode, result.data!!)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartCopyTheme {
                SmartCopyHome(
                    onLaunchOverlay  = ::requestOverlayOrLaunch,
                    onGrantOverlay   = ::openOverlaySettings,
                    onOpenSettings   = ::openSettings,
                )
            }
        }
        if (intent?.getBooleanExtra(EXTRA_QS_TRIGGERED, false) == true) {
            requestOverlayOrLaunch()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_QS_TRIGGERED, false)) {
            requestOverlayOrLaunch()
        }
    }

    /**
     * Called when the user taps "Launch Smart Copy".
     * Overlay permission must already be granted at this point
     * (the button is disabled otherwise). Shows the system
     * "Start recording?" consent dialog and starts the service
     * on RESULT_OK.
     */
    private fun requestOverlayOrLaunch() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
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
    onLaunchOverlay : () -> Unit,
    onGrantOverlay  : () -> Unit,
    onOpenSettings  : () -> Unit,
) {
    val context       = LocalContext.current
    val scrollState   = rememberScrollState()

    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    // Manual recheck — for OEM skins (MIUI/HyperOS etc.) that don't
    // fire ON_RESUME reliably after returning from system settings.
    val recheckPermission = { hasOverlayPermission = Settings.canDrawOverlays(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) recheckPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            // S|C monogram badge
            Surface(
                shape        = MaterialTheme.shapes.large,
                color        = Color(0xFF000000),
                border       = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFFFFF)),
                tonalElevation = 4.dp,
                modifier     = Modifier.size(80.dp),
            ) {
                Row(
                    modifier            = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment   = Alignment.CenterVertically,
                ) {
                    Text("S",  fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Color(0xFFFFFFFF))
                    Text("|",  fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Thin,       fontSize = 22.sp, color = Color(0xFFFFFFFF), modifier = Modifier.padding(horizontal = 1.dp))
                    Text("C",  fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Color(0xFFFFFFFF))
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text       = "Smart Copy",
                style      = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFFF1F5F9),
            )
            Text(
                text       = "Privacy-first  •  100% Offline  •  Zero Ads",
                style      = MaterialTheme.typography.bodyMedium,
                color      = Color(0xFF94A3B8),
                textAlign  = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
            )

            Spacer(Modifier.height(8.dp))

            // Permission card (red) or ready card (teal)
            if (!hasOverlayPermission) {
                PermissionCard(
                    onGrantClick   = onGrantOverlay,
                    onRecheckClick = recheckPermission,
                )
            } else {
                StatusCard()
            }

            Spacer(Modifier.height(8.dp))

            // Launch button — only active once overlay permission is granted
            Button(
                onClick  = onLaunchOverlay,
                enabled  = hasOverlayPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor        = Color(0xFF38BDF8),
                    disabledContainerColor = Color(0xFF1E3A4A),
                ),
            ) {
                Text(
                    text       = if (hasOverlayPermission) "Launch Smart Copy" else "Grant permission above first",
                    fontWeight = FontWeight.Bold,
                    color      = if (hasOverlayPermission) Color(0xFF0A0A0F) else Color(0xFF4A6A7A),
                    fontSize   = 16.sp,
                )
            }

            OutlinedButton(
                onClick  = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(text = "Settings", color = Color(0xFFCBD5E1), fontSize = 16.sp)
            }

            Spacer(Modifier.weight(1f))

            Text(
                text       = "Open Source  ·  Zero Telemetry  ·  No Network",
                style      = MaterialTheme.typography.labelSmall,
                color      = Color(0xFF475569),
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    onGrantClick   : () -> Unit,
    onRecheckClick : () -> Unit,
) {
    Card(
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF1C1917)),
        border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Overlay Permission Required", color = Color(0xFFFCA5A5), fontWeight = FontWeight.SemiBold)
            Text(
                "Smart Copy needs \"Display over other apps\" permission to freeze the screen and show the OCR overlay.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
            )
            // Step 1 — open system settings
            TextButton(onClick = onGrantClick) {
                Text("1. Open permission settings  →", color = Color(0xFF38BDF8))
            }
            // Step 2 — manual recheck once the user has toggled SmartCopy on
            TextButton(onClick = onRecheckClick) {
                Text("2. I've granted it  —  check again", color = Color(0xFF4ADE80))
            }
        }
    }
}

@Composable
private fun StatusCard() {
    Card(
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF0F2027)),
        border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22D3EE)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ready", color = Color(0xFF4ADE80), fontWeight = FontWeight.SemiBold)
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
