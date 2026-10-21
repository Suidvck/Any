package com.suidvck.any.features.system

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.suidvck.any.features.data.Automation
import com.suidvck.any.features.data.AutomationStore
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules the user's daily automations as unique WorkManager jobs.
 *
 * Every automation owns the unique work name `auto_<id>` (tag `automation`).
 * Enqueuing with [ExistingWorkPolicy.REPLACE] keeps a single pending run per
 * automation, so calling [rescheduleAll] is always safe (on app start, after an
 * edit, after a toggle...). The worker re-enqueues the next occurrence after
 * firing.
 */
object AutomationScheduler {

    /** Tag attached to every automation work request. */
    const val TAG_AUTOMATION = "automation"

    private const val NAME_PREFIX = "auto_"
    private const val TAG = "AutomationScheduler"

    /**
     * Re-syncs WorkManager with the stored automations: enabled ones get a
     * pending job, disabled ones are cancelled. Never throws.
     */
    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        try {
            val items = AutomationStore(app).list()
            for (a in items) {
                if (a.enabled) {
                    schedule(app, a)
                } else {
                    cancel(app, a.id)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "rescheduleAll failed", t)
        }
    }

    /** Cancels the pending job of one automation (deleted or disabled). */
    fun cancel(context: Context, id: String) {
        try {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(nameFor(id))
        } catch (t: Throwable) {
            Log.w(TAG, "cancel failed for $id", t)
        }
    }

    /**
     * Enqueues (or replaces) the next occurrence of [a].
     * The delay targets today's HH:mm when it is still in the future,
     * otherwise tomorrow's HH:mm.
     */
    internal fun schedule(context: Context, a: Automation) {
        if (!a.enabled) {
            cancel(context, a.id)
            return
        }
        try {
            val request = OneTimeWorkRequestBuilder<AutomationWorker>()
                .setInitialDelay(delayUntilNext(a.hour, a.minute), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(AutomationWorker.KEY_AUTOMATION_ID to a.id))
                .addTag(TAG_AUTOMATION)
                .addTag(nameFor(a.id))
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(nameFor(a.id), ExistingWorkPolicy.REPLACE, request)
        } catch (t: Throwable) {
            Log.w(TAG, "schedule failed for ${a.id}", t)
        }
    }

    /**
     * Milliseconds between [now] and the next occurrence of [hour]:[minute].
     * Returns a value in (0, 24h]. When the time is exactly now it targets
     * tomorrow, matching "today if future, else tomorrow".
     */
    internal fun delayUntilNext(
        hour: Int,
        minute: Int,
        now: Long = System.currentTimeMillis()
    ): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return (cal.timeInMillis - now).coerceAtLeast(0L)
    }

    private fun nameFor(id: String): String = NAME_PREFIX + id
}
