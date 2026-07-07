package dev.max.pomodoro

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// The whole design is two grays.
val Dark = Color(0xFF1C1C1C)
val Bright = Color(0xFFD4D4D4)

val Context.store by preferencesDataStore("settings")
val FOCUS_KEY = intPreferencesKey("focus")
val BREAK_KEY = intPreferencesKey("break")

enum class Phase { IDLE, FOCUS, BREAK }

/** Process-wide session state, shared by the activity and the accessibility service. */
object Session {
    var phase by mutableStateOf(Phase.IDLE)
    var secondsLeft by mutableIntStateOf(0)
    private var job: Job? = null

    fun start(ctx: Context, focusMin: Int, breakMin: Int) {
        if (phase != Phase.IDLE) return
        val app = ctx.applicationContext
        job = MainScope().launch {
            phase = Phase.FOCUS
            countdown(app, focusMin * 60)
            vibrate(app)
            phase = Phase.BREAK
            countdown(app, breakMin * 60)
            vibrate(app)
            phase = Phase.IDLE
        }
    }

    private suspend fun countdown(app: Context, totalSeconds: Int) {
        val end = SystemClock.elapsedRealtime() + totalSeconds * 1000L
        while (true) {
            val left = ((end - SystemClock.elapsedRealtime() + 999) / 1000).toInt()
            if (left <= 0) break
            secondsLeft = left
            // Watchdog: accessibility got disabled mid-session -> keep the screen locked.
            if (!accessibilityEnabled(app)) {
                val dpm = app.getSystemService(DevicePolicyManager::class.java)
                if (dpm.isAdminActive(ComponentName(app, AdminReceiver::class.java))) dpm.lockNow()
            }
            delay(500)
        }
        secondsLeft = 0
    }
}

fun accessibilityEnabled(ctx: Context): Boolean =
    Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.contains("${ctx.packageName}/") == true

fun adminActive(ctx: Context): Boolean =
    ctx.getSystemService(DevicePolicyManager::class.java)
        .isAdminActive(ComponentName(ctx, AdminReceiver::class.java))

fun vibrate(ctx: Context) {
    val v = if (Build.VERSION.SDK_INT >= 31)
        ctx.getSystemService(VibratorManager::class.java).defaultVibrator
    else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
    v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
}

class AdminReceiver : DeviceAdminReceiver()

/** Bounces any foreign foreground app (incl. launcher/recents) back to this app while running. */
class BlockerService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (Session.phase == Phase.IDLE) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != packageName) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
    }

    override fun onInterrupt() {}
}

class MainActivity : ComponentActivity() {
    private val permsOk = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ponytail: blocking read of a tiny prefs file at startup, simplest correct load
        val prefs = runBlocking { store.data.first() }
        setContent {
            App(
                permsOk = permsOk.value,
                initialFocus = prefs[FOCUS_KEY] ?: 25,
                initialBreak = prefs[BREAK_KEY] ?: 5,
                onGrant = ::grantNextPermission,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        permsOk.value = accessibilityEnabled(this) && adminActive(this)
    }

    private fun grantNextPermission() {
        when {
            !accessibilityEnabled(this) ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            !adminActive(this) ->
                startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            ComponentName(this, AdminReceiver::class.java)
                        )
                        .putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            getString(R.string.admin_explanation)
                        )
                )
        }
    }
}

@Composable
fun App(permsOk: Boolean, initialFocus: Int, initialBreak: Int, onGrant: () -> Unit) {
    val running = Session.phase != Phase.IDLE
    BackHandler(enabled = running) {} // no way out during a session

    Column(
        modifier = Modifier.fillMaxSize().background(Dark).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (running) RunningScreen() else IdleScreen(permsOk, initialFocus, initialBreak, onGrant)
    }
}

@Composable
fun RunningScreen() {
    Text(
        text = if (Session.phase == Phase.FOCUS) "Focus" else "Break",
        color = Bright, fontSize = 28.sp,
    )
    Text(
        text = "%d:%02d".format(Session.secondsLeft / 60, Session.secondsLeft % 60),
        color = Bright, fontSize = 72.sp, fontFamily = FontFamily.Monospace,
    )
}

@Composable
fun IdleScreen(permsOk: Boolean, initialFocus: Int, initialBreak: Int, onGrant: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val buttonColors = ButtonDefaults.buttonColors(containerColor = Bright, contentColor = Dark)

    if (!permsOk) {
        Text(
            "Pomodoro needs the accessibility service and device admin to block the phone.",
            color = Bright,
        )
        Button(onClick = onGrant, colors = buttonColors, modifier = Modifier.padding(top = 24.dp)) {
            Text("Grant permissions")
        }
        return
    }

    var focus by remember { mutableIntStateOf(initialFocus) }
    var brk by remember { mutableIntStateOf(initialBreak) }
    val sliderColors = SliderDefaults.colors(
        thumbColor = Bright, activeTrackColor = Bright,
        inactiveTrackColor = Bright.copy(alpha = 0.3f),
    )

    Text("Focus  $focus min", color = Bright, fontSize = 20.sp)
    Slider(
        value = focus.toFloat(), onValueChange = { focus = it.toInt() },
        valueRange = 1f..60f, colors = sliderColors, modifier = Modifier.fillMaxWidth(),
    )
    Text("Break  $brk min", color = Bright, fontSize = 20.sp, modifier = Modifier.padding(top = 24.dp))
    Slider(
        value = brk.toFloat(), onValueChange = { brk = it.toInt() },
        valueRange = 1f..30f, colors = sliderColors, modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            MainScope().launch {
                ctx.store.edit { it[FOCUS_KEY] = focus; it[BREAK_KEY] = brk }
            }
            Session.start(ctx, focus, brk)
        },
        colors = buttonColors,
        modifier = Modifier.padding(top = 40.dp),
    ) {
        Text("Start", fontSize = 20.sp)
    }
}
