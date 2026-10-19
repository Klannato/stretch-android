package com.nous.stretchup

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nous.stretchup.data.Prefs
import com.nous.stretchup.session.Slot
import com.nous.stretchup.ui.HomeScreen
import com.nous.stretchup.ui.SessionScreen
import com.nous.stretchup.ui.StretchTheme

class MainActivity : ComponentActivity() {

    private var pendingSlot by mutableStateOf<Slot?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reminders.ensureChannel(this)
        Reminders.scheduleAll(this)
        pendingSlot = slotFrom(intent)

        setContent {
            StretchTheme {
                val vm: AppViewModel = viewModel()
                AppRoot(vm, pendingSlot) { pendingSlot = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        slotFrom(intent)?.let { pendingSlot = it }
    }

    private fun slotFrom(intent: Intent?): Slot? =
        if (intent?.getBooleanExtra(Reminders.EXTRA_AUTOSTART, false) == true) {
            Reminders.slotOf(intent.getStringExtra(Reminders.EXTRA_SLOT))
        } else {
            null
        }
}

@Composable
private fun AppRoot(vm: AppViewModel, pendingSlot: Slot?, onSlotConsumed: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var exactAlarmsOk by remember { mutableStateOf(Reminders.canScheduleExact(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                exactAlarmsOk = Reminders.canScheduleExact(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsEnabled = granted }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !prefs.askedNotifications) {
            prefs.askedNotifications = true
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(pendingSlot) {
        pendingSlot?.let {
            vm.startSession(it)
            onSlotConsumed()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (vm.inSession) {
                SessionScreen(vm)
            } else {
                HomeScreen(
                    vm = vm,
                    notificationsEnabled = notificationsEnabled,
                    exactAlarmsOk = exactAlarmsOk,
                    onOpenNotificationSettings = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:${context.packageName}"))
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    onOpenExactAlarmSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(Uri.parse("package:${context.packageName}"))
                                )
                            }
                        }
                    },
                    onPickTime = { initial, onSet ->
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> onSet(hour * 60 + minute) },
                            initial / 60,
                            initial % 60,
                            false
                        ).show()
                    }
                )
            }
        }
    }
}
