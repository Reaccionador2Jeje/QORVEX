package dev.atdev.qorvex.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import dev.atdev.qorvex.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the QEMU runtime in a foreground service while a guest is running.
 * The Activity and service share one runtime instance so Compose observers
 * continue receiving state and serial output after Activity recreation.
 */
class QorvexRuntimeService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var runtime: QemuProcessRuntime
    private val notifications by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        runtime = getRuntime(applicationContext)
        createNotificationChannel()
        serviceScope.launch {
            combine(runtime.state, runtime.activeMachineId) { state, machineId -> state to machineId }
                .collect { (state, machineId) ->
                    if (isForeground) {
                        notifications.notify(NOTIFICATION_ID, buildNotification(state, machineId))
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(runtime.state.value, runtime.activeMachineId.value))
        isForeground = true
        if (intent?.action == ACTION_STOP) {
            val requestedId = intent.getStringExtra(EXTRA_MACHINE_ID)
            serviceScope.launch {
                val activeId = requestedId ?: runtime.activeMachineId.value
                if (activeId != null && runtime.activeMachineId.value == activeId) {
                    runtime.stop(activeId)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isForeground = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "QORVEX virtual machines", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps a user-started QORVEX virtual machine running."
                setShowBadge(false)
            }
            notifications.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: RuntimeState, machineId: String?): Notification {
        val status = when (state) {
            RuntimeState.STARTING -> "Starting virtual machine"
            RuntimeState.RUNNING -> "Virtual machine is running"
            RuntimeState.STOPPING -> "Stopping virtual machine"
            RuntimeState.EXITED -> "Virtual machine exited"
            RuntimeState.FAILED -> "Virtual machine failed"
            RuntimeState.STOPPED -> "QORVEX runtime is ready"
        }
        val profileName = machineId?.let { " • VM ${it.take(8)}" }.orEmpty()
        val openApp = PendingIntent.getActivity(
            this, OPEN_APP_REQUEST,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopService = PendingIntent.getService(
            this, STOP_REQUEST,
            Intent(this, QorvexRuntimeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("QORVEX")
            .setContentText(status + profileName)
            .setStyle(Notification.BigTextStyle().bigText("$status$profileName. QORVEX is managing the guest process."))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Stop VM", stopService).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "qorvex_vm_runtime"
        private const val NOTIFICATION_ID = 4101
        private const val OPEN_APP_REQUEST = 4102
        private const val STOP_REQUEST = 4103
        private const val ACTION_KEEP = "dev.atdev.qorvex.action.KEEP_RUNTIME"
        private const val ACTION_STOP = "dev.atdev.qorvex.action.STOP_RUNTIME"
        private const val EXTRA_MACHINE_ID = "machine_id"
        @Volatile private var sharedRuntime: QemuProcessRuntime? = null
        @Volatile private var isForeground = false

        fun getRuntime(context: Context): QemuProcessRuntime =
            sharedRuntime ?: synchronized(this) {
                sharedRuntime ?: QemuProcessRuntime(context.applicationContext).also { sharedRuntime = it }
            }

        fun start(context: Context) {
            val intent = Intent(context, QorvexRuntimeService::class.java).setAction(ACTION_KEEP)
            context.startForegroundService(intent)
        }

        fun requestStop(context: Context, machineId: String) {
            val intent = Intent(context, QorvexRuntimeService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_MACHINE_ID, machineId)
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, QorvexRuntimeService::class.java))
        }
    }
}