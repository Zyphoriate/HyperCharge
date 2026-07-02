package cn.buffcow.hypersc.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Cross-process event bus using Android BroadcastReceiver + Parcelable events.
 *
 * Used to communicate between the main process (UI/hook) and the
 * .remote process (battery monitoring / notification).
 *
 * Events are sent with RECEIVER_NOT_EXPORTED to restrict to same-package receivers.
 *
 * @author qingyu
 */
object RemoteEventHelper {

    private val listeners = CopyOnWriteArrayList<EventListener>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getParcelableExtra(EXTRA_REMOTE_EVENT, Event::class.java)?.let { event ->
                listeners.forEach { it.onReceive(context, event, intent) }
            }
        }
    }

    fun register(context: Context, listener: EventListener) {
        if (listeners.addIfAbsent(listener)) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            context.registerReceiver(
                receiver,
                IntentFilter(ACTION_SEND_REMOTE_EVENT),
                Context.RECEIVER_NOT_EXPORTED
            )
        }
    }

    fun sendEvent(context: Context, event: Event) {
        context.sendBroadcast(
            Intent(ACTION_SEND_REMOTE_EVENT).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_REMOTE_EVENT, event)
            }
        )
    }

    // ---- Event types ----

    @Parcelize
    sealed class Event : Parcelable {
        /** Request the remote process to stop battery monitoring. */
        data object UnregisterBatteryReceiver : Event()

        /** Update the notification with a new percent value (null = remove). */
        data class UpdateNotification(val percentValue: String?) : Event()
    }

    fun interface EventListener {
        fun onReceive(context: Context, event: Event, intent: Intent)
    }
}

private const val EXTRA_REMOTE_EVENT = "com.miui.security-center.extra.REMOTE_EVENT"
private const val ACTION_SEND_REMOTE_EVENT = "com.miui.security-center.action.SEND_REMOTE_EVENT"
