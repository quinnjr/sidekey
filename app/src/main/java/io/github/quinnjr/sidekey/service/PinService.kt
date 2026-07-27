package io.github.quinnjr.sidekey.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import io.github.quinnjr.sidekey.core.AndroidGlobalSettings
import io.github.quinnjr.sidekey.core.Behavior
import io.github.quinnjr.sidekey.core.GlobalSettingsPort
import io.github.quinnjr.sidekey.core.KEY_PBLP
import io.github.quinnjr.sidekey.core.KeyBehaviorRepo
import io.github.quinnjr.sidekey.core.PinDecision
import io.github.quinnjr.sidekey.core.PinPolicy

/**
 * Watches [KEY_PBLP] and re-asserts the desired value when the system overwrites it.
 *
 * A foreground service rather than plain background work, because Samsung's
 * `RestrictedReceiverFilter` throttles work started from `BOOT_COMPLETED`. It self-stops
 * once [PinPolicy] reports [PinDecision.Stop], so the notification is transient — roughly a
 * minute after boot.
 */
class PinService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: GlobalSettingsPort
    private lateinit var repo: KeyBehaviorRepo
    private lateinit var policy: PinPolicy
    private var desired = DEFAULT_DESIRED
    private var startedAt = 0L
    private var observer: ContentObserver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        desired = intent?.getIntExtra(EXTRA_DESIRED, DEFAULT_DESIRED) ?: DEFAULT_DESIRED
        settings = AndroidGlobalSettings(this)
        repo = KeyBehaviorRepo(settings)
        policy = PinPolicy(desired, STABILITY_MS)
        startedAt = SystemClock.elapsedRealtime()

        startForeground(NOTIFICATION_ID, buildNotification())
        registerObserver()

        // The system write may already have landed before we got here.
        evaluate()
        handler.postDelayed(::evaluate, STABILITY_MS)

        return START_STICKY
    }

    private fun registerObserver() {
        if (observer != null) return
        val created = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) = evaluate()
        }
        contentResolver.registerContentObserver(Settings.Global.getUriFor(KEY_PBLP), false, created)
        observer = created
    }

    private fun evaluate() {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val observed = settings.getInt(KEY_PBLP)
        when (policy.decide(observed, elapsed)) {
            PinDecision.Write -> {
                val result = repo.apply(Behavior.fromInt(desired))
                Log.i(TAG, "re-asserted $KEY_PBLP: observed=$observed desired=$desired -> $result")
            }

            PinDecision.Idle -> Unit

            PinDecision.Stop -> {
                Log.i(TAG, "value stable at $desired for ${elapsed}ms, stopping")
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        observer?.let(contentResolver::unregisterContentObserver)
        observer = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Pinning the side key", NotificationManager.IMPORTANCE_MIN)
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Restoring the side key power menu")
            .setContentText("Watching for the system to overwrite the setting")
            .setSmallIcon(android.R.drawable.ic_lock_power_off)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val EXTRA_DESIRED = "desired"

        private const val TAG = "SideKey"
        private const val CHANNEL = "pin"
        private const val NOTIFICATION_ID = 1
        private const val DEFAULT_DESIRED = 1

        /** Comfortably past the ~3.7 s post-boot rewrite measured on One UI 8.5. */
        private const val STABILITY_MS = 60_000L

        fun start(context: Context, desired: Int) {
            val intent = Intent(context, PinService::class.java)
                .putExtra(EXTRA_DESIRED, desired)
            context.startForegroundService(intent)
        }
    }
}
