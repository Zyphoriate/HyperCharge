package cn.buffcow.hypersc.utils

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages a persistent notification that shows the current smart charge
 * protection threshold. Runs in the .remote process of Security Center.
 *
 * Listens to ACTION_BATTERY_CHANGED to show/hide the notification based on
 * charging state, and receives cross-process UpdateNotification events from
 * the main process via RemoteEventHelper.
 *
 * @author qingyu
 */
object ProtectNotificationHelper : RemoteEventHelper.EventListener {

    private const val TAG = "HyperSmartCharge"
    private const val NOTIFICATION_ID = 1008611
    private const val CHANNEL_ID = "com.miui.powercenter.low"

    private var notificationShowed = false
    private val batteryRegistered = AtomicBoolean(false)

    // ---- Battery state receiver ----

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)

            when (status) {
                BatteryManager.BATTERY_STATUS_FULL,
                BatteryManager.BATTERY_STATUS_CHARGING,
                -> if (level <= 100 && plugged != 0) createAndShowNotification(context)

                else -> removeNotification(context)
            }
        }
    }

    // ---- Public API ----

    /**
     * Register the battery change receiver in the .remote process.
     * Called once when the module is loaded into the remote process.
     */
    fun registerBatteryReceiver(context: Context) {
        if (batteryRegistered.compareAndSet(false, true)) {
            context.applicationContext.registerReceiver(
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            RemoteEventHelper.register(context, this)
            Log.d(TAG, "registered battery changed receiver.")
        }
    }

    // ---- RemoteEventHelper.EventListener ----

    override fun onReceive(context: Context, event: RemoteEventHelper.Event, intent: Intent) {
        Log.d(TAG, "receive client event: $event")
        when (event) {
            RemoteEventHelper.Event.UnregisterBatteryReceiver -> {
                unregisterBatteryReceiver(context)
            }

            is RemoteEventHelper.Event.UpdateNotification -> {
                event.percentValue?.let { value ->
                    if (notificationShowed) {
                        publishNotification(context, value)
                    } else if (batteryRegistered.get()) {
                        // Manually trigger battery check to decide whether to show
                        context.registerReceiver(
                            null,
                            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                        )?.let { batteryReceiver.onReceive(context, it) }
                    }
                } ?: removeNotification(context)
            }
        }
    }

    // ---- Notification management ----

    fun createAndShowNotification(context: Context) {
        if (notificationShowed) return
        val perChg = ChargeProtectionUtils.getSmartChargePercentValue(context)
        Log.d(TAG, "showNotification - smart charge percent value: $perChg")
        perChg ?: return

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getHostString(context, "battery_and_property_ordinary_notify", "Battery Protection"),
                NotificationManager.IMPORTANCE_LOW
            )
        )

        publishNotification(context, "$perChg", notificationManager)
        notificationShowed = true
    }

    @SuppressLint("NotificationPermission")
    private fun publishNotification(
        context: Context,
        percentValue: String,
        notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java),
    ) {
        val iconResId = getHostResourceId(context, "ic_performance_notification", "drawable")

        val icon = if (iconResId != 0) {
            Icon.createWithResource(context.packageName, iconResId)
        } else {
            // Fallback: use a built-in icon
            Icon.createWithResource("android", android.R.drawable.ic_lock_idle_charging)
        }

        val intent = Intent().apply {
            setClassName(
                context.packageName,
                "com.miui.powercenter.nightcharge.ChargerProtectActivity"
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = getHostString(context, "pc_health_charge_protect_title", "Charge Protection")
        val content = getHostString(
            context,
            "pc_health_charge_protect_noti_summary_title",
            "Will stop charging at %s"
        ).replace("%s", "$percentValue%")

        Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
            .apply { notificationManager.notify(NOTIFICATION_ID, this) }
    }

    private fun removeNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        notificationShowed = false
    }

    private fun unregisterBatteryReceiver(context: Context) {
        if (batteryRegistered.compareAndSet(true, false)) {
            context.applicationContext.unregisterReceiver(batteryReceiver)
            removeNotification(context)
            Log.d(TAG, "unregistered battery changed receiver.")
        }
    }

    // ---- Resource helpers (access host app resources) ----

    @Suppress("DiscouragedApi")
    private fun getHostResourceId(context: Context, name: String, type: String): Int {
        return context.resources.getIdentifier(name, type, context.packageName)
    }

    private fun getHostString(context: Context, name: String, fallback: String): String {
        return try {
            val resId = context.resources.getIdentifier(name, "string", context.packageName)
            if (resId != 0) context.resources.getString(resId) else fallback
        } catch (_: Exception) {
            fallback
        }
    }
}
