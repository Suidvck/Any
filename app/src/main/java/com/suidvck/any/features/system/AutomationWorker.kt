package com.suidvck.any.features.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.suidvck.any.MainActivity
import com.suidvck.any.R
import com.suidvck.any.features.data.Automation
import com.suidvck.any.features.data.AutomationStore
import com.suidvck.any.features.data.MiniAppStore
import java.util.Locale

/**
 * Runs one automation when its scheduled time arrives.
 *
 * The work only contains the automation id; the current record is read back
 * from [AutomationStore] so edits/deletes done after scheduling are respected.
 * After notifying the user the next daily occurrence is enqueued.
 */
class AutomationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val app = applicationContext
        return try {
            val id = inputData.getString(KEY_AUTOMATION_ID)
            if (id.isNullOrBlank()) {
                Result.success()
            } else {
                val automation = AutomationStore(app).list().firstOrNull { it.id == id }
                if (automation == null || !automation.enabled) {
                    // Deleted or switched off while the job was pending.
                    Result.success()
                } else {
                    notifyUser(app, automation)
                    // Re-enqueue the next occurrence (daily). The scheduler
                    // computes the delay from "now", so this always lands on the
                    // next HH:mm, today or tomorrow.
                    AutomationScheduler.schedule(app, automation)
                    Result.success()
                }
            }
        } catch (t: Throwable) {
            // A reminder must never crash the app or fail the work chain.
            Log.w(TAG, "Automation run failed", t)
            Result.success()
        }
    }

    private fun notifyUser(app: Context, a: Automation) {
        createChannel(app)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "POST_NOTIFICATIONS not granted, skipping notification for ${a.id}")
            return
        }

        val description = AutomationInfo.describe(app, a)
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.automation_ic_notification)
            .setContentTitle(
                a.label.ifBlank {
                    "${automationTimeLabel(a.hour, a.minute)} · ${AutomationInfo.kindLabel(a.kind)}"
                }
            )
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setColor(ACCENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(app, a))
            .setWhen(System.currentTimeMillis())
            .build()

        try {
            NotificationManagerCompat.from(app).notify(notificationId(a.id), notification)
        } catch (t: Throwable) {
            Log.w(TAG, "notify failed for ${a.id}", t)
        }
    }

    private fun createChannel(app: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "แจ้งเตือนเมื่อถึงเวลาที่ตั้งไว้ใน Any"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Opens MainActivity with the extras the orchestrator consumes:
     * `openUrl` (url), `openChat` (chat), `autoPrompt` (prompt), `openAppId` (app).
     */
    private fun contentIntent(app: Context, a: Automation): PendingIntent {
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            when (a.kind) {
                KIND_URL -> putExtra(EXTRA_OPEN_URL, a.value)
                KIND_CHAT -> putExtra(EXTRA_OPEN_CHAT, true)
                KIND_PROMPT -> putExtra(EXTRA_AUTO_PROMPT, a.value)
                KIND_APP -> putExtra(EXTRA_OPEN_APP_ID, a.value)
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(app, notificationId(a.id), intent, flags)
    }

    companion object {
        /** Input-data key holding the automation id. */
        const val KEY_AUTOMATION_ID = "automation_id"

        /** Notification channel id (contract). */
        const val CHANNEL_ID = "any_automation"

        /** Notification channel display name (contract). */
        const val CHANNEL_NAME = "Any Automations"

        const val EXTRA_OPEN_URL = "openUrl"
        const val EXTRA_OPEN_CHAT = "openChat"
        const val EXTRA_AUTO_PROMPT = "autoPrompt"
        const val EXTRA_OPEN_APP_ID = "openAppId"

        internal const val KIND_URL = "url"
        internal const val KIND_CHAT = "chat"
        internal const val KIND_PROMPT = "prompt"
        internal const val KIND_APP = "app"

        private const val TAG = "AutomationWorker"
        private val ACCENT = 0xFF5F6CF5.toInt()

        private fun notificationId(id: String): Int = ("auto_$id").hashCode() and 0x7FFFFFFF
    }
}

/** Shared Thai labels / notification copy for the automation UI and worker. */
internal object AutomationInfo {

    /** Kind keys, in the same order as the sheet's spinner. */
    val KINDS = arrayOf(
        AutomationWorker.KIND_URL,
        AutomationWorker.KIND_CHAT,
        AutomationWorker.KIND_PROMPT,
        AutomationWorker.KIND_APP
    )

    fun kindLabel(kind: String): String = when (kind) {
        AutomationWorker.KIND_URL -> "เปิดเว็บ"
        AutomationWorker.KIND_CHAT -> "เปิดแชท"
        AutomationWorker.KIND_PROMPT -> "ส่งคำสั่ง AI"
        AutomationWorker.KIND_APP -> "เปิดแอพ"
        else -> "อัตโนมัติ"
    }

    /** One-line Thai description of what the automation will do. */
    fun describe(context: Context, a: Automation): String = when (a.kind) {
        AutomationWorker.KIND_URL -> "เปิดเว็บ: ${a.value}"
        AutomationWorker.KIND_CHAT ->
            if (a.value.isBlank()) "เปิดแชทใหม่ใน Any" else "เปิดแชท: ${a.value}"
        AutomationWorker.KIND_PROMPT -> "ส่งคำสั่งให้ AI: ${a.value}"
        AutomationWorker.KIND_APP -> "เปิดแอพ: ${appName(context, a.value)}"
        else -> "เปิด Any"
    }

    private fun appName(context: Context, id: String): String =
        runCatching { MiniAppStore(context).list().firstOrNull { it.id == id }?.name }
            .getOrNull()
            ?: "แอพที่บันทึกไว้"
}

internal fun automationTimeLabel(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour, minute)
