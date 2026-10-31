package com.suidvck.any.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import androidx.core.view.WindowInsetsControllerCompat
import com.suidvck.any.R

/**
 * Small visual fixes shared by every window:
 *
 *  - sheet/dialog windows get a transparent navigation bar so MIUI does not paint a
 *    white strip behind them, and light system-bar icons to match the dark app.
 */
object AnyChrome {

    /** Call right after [Dialog.show] for bottom sheets. */
    fun sheet(dialog: Dialog) {
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            styleBars(window)
        }
    }

    /** Call right after [Dialog.show] for alert dialogs (keeps their own background). */
    fun dialog(dialog: Dialog) {
        dialog.window?.let { window ->
            styleBars(window)
            window.setDimAmount(0.42f)
            // Some OEM skins ignore the theme's windowBackground for floating dialogs
            // and paint their own grey surface; re-apply ours on the live window.
            window.setBackgroundDrawableResource(R.drawable.any_dialog_bg)
        }
    }

    /** Applies the navigation bar treatment to the activity window. */
    fun activity(activity: Activity) {
        activity.window?.let { styleBars(it) }
    }

    private fun styleBars(window: Window) {
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        runCatching {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightNavigationBars = false
                isAppearanceLightStatusBars = false
            }
        }
    }
}
