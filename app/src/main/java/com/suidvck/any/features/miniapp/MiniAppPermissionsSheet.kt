package com.suidvck.any.features.miniapp

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.suidvck.any.R
import com.suidvck.any.features.home.Appearance
import com.suidvck.any.ui.AnyChrome
import com.suidvck.any.ui.FaIcons
import kotlin.math.roundToInt

/**
 * Bottom sheet listing what an AI-generated HTML app may do. Changes are global and apply
 * immediately, except JavaScript/storage, which take effect the next time an app is opened.
 */
object MiniAppPermissionsSheet {

    private data class Toggle(
        val key: String,
        val title: Int,
        val subtitle: Int,
        val icon: String,
        val value: (MiniAppPermissions.State) -> Boolean
    )

    private val toggles = listOf(
        Toggle(
            MiniAppPermissions.JAVASCRIPT,
            R.string.miniapp_perm_js,
            R.string.miniapp_perm_js_sub,
            "code",
            { it.javaScript }
        ),
        Toggle(
            MiniAppPermissions.STORAGE,
            R.string.miniapp_perm_storage,
            R.string.miniapp_perm_storage_sub,
            "database",
            { it.storage }
        ),
        Toggle(
            MiniAppPermissions.NETWORK,
            R.string.miniapp_perm_network,
            R.string.miniapp_perm_network_sub,
            "globe",
            { it.network }
        ),
        Toggle(
            MiniAppPermissions.DOWNLOADS,
            R.string.miniapp_perm_download,
            R.string.miniapp_perm_download_sub,
            "download",
            { it.downloads }
        ),
        Toggle(
            MiniAppPermissions.MEDIA,
            R.string.miniapp_perm_media,
            R.string.miniapp_perm_media_sub,
            "camera",
            { it.media }
        ),
        Toggle(
            MiniAppPermissions.LOCATION,
            R.string.miniapp_perm_location,
            R.string.miniapp_perm_location_sub,
            "location-dot",
            { it.location }
        )
    )

    fun show(activity: Activity, onChanged: () -> Unit = {}) {
        val dialog = BottomSheetDialog(activity)
        val root = LayoutInflater.from(activity)
            .inflate(R.layout.miniapp_permissions_sheet, null, false)
        dialog.setContentView(root)
        (root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        root.findViewById<View>(R.id.miniapp_permissions_done)?.setOnClickListener {
            dialog.dismiss()
        }
        render(activity, root, onChanged)

        val maxHeight = (activity.resources.displayMetrics.heightPixels * 0.9f).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                dp(activity, 26) + bottom
            )
            insets
        }
        dialog.show()
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        AnyChrome.sheet(dialog)
        dialog.behavior.isFitToContents = true
        dialog.behavior.skipCollapsed = true
        root.post {
            val contentHeight = (root as? android.widget.ScrollView)?.getChildAt(0)?.height ?: 0
            val natural = if (contentHeight > 0) {
                contentHeight + root.paddingTop + root.paddingBottom
            } else {
                root.height
            }
            val target = natural.coerceAtMost(maxHeight)
            if (dialog.isShowing && target > 0) {
                root.layoutParams = root.layoutParams.apply { height = target }
                runCatching { dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED }
            }
        }
    }

    private fun render(activity: Activity, root: View, onChanged: () -> Unit) {
        val accent = Appearance.accentColor(activity)
        root.findViewById<TextView>(R.id.miniapp_permissions_done)?.setTextColor(accent)

        val container = root.findViewById<LinearLayout>(R.id.miniapp_permissions_container) ?: return
        container.removeAllViews()
        val state = MiniAppPermissions.load(activity)

        toggles.forEachIndexed { index, toggle ->
            val enabled = toggle.value(state)
            container.addView(
                toggleRow(activity, toggle, enabled, accent) {
                    MiniAppPermissions.set(activity, toggle.key, !enabled)
                    onChanged()
                    render(activity, root, onChanged)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { if (index > 0) topMargin = dp(activity, 6) }
            )
        }
    }

    private fun toggleRow(
        activity: Activity,
        toggle: Toggle,
        enabled: Boolean,
        accent: Int,
        onToggle: () -> Unit
    ): View {
        val title = activity.getString(toggle.title)
        val subtitle = activity.getString(toggle.subtitle)
        val stateLabel = activity.getString(
            if (enabled) R.string.miniapp_perm_on else R.string.miniapp_perm_off
        )

        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
            background = GradientDrawable().apply {
                cornerRadius = dp(activity, 14).toFloat()
                setColor(0x0FFFFFFF)
            }
            isClickable = true
            isFocusable = true
            contentDescription = "$title, $subtitle, $stateLabel"
            setOnClickListener { onToggle() }
        }

        row.addView(
            TextView(activity).apply {
                gravity = Gravity.CENTER
                FaIcons.setIcon(
                    this,
                    toggle.icon,
                    16f,
                    if (enabled) accent else 0xFF9A9AA5.toInt()
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x14FFFFFF)
                }
            },
            LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36))
        )

        val textColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        textColumn.addView(
            TextView(activity).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
            }
        )
        textColumn.addView(
            TextView(activity).apply {
                text = subtitle
                setTextColor(0xFF9A9AA5.toInt())
                textSize = 11f
                includeFontPadding = false
                setPadding(0, dp(activity, 2), 0, 0)
            }
        )
        row.addView(
            textColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(activity, 12)
                marginEnd = dp(activity, 10)
            }
        )

        row.addView(
            TextView(activity).apply {
                text = stateLabel
                setTextColor(if (enabled) Color.WHITE else 0xFF9A9AA5.toInt())
                textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                minWidth = dp(activity, 54)
                setPadding(dp(activity, 12), dp(activity, 7), dp(activity, 12), dp(activity, 7))
                background = GradientDrawable().apply {
                    cornerRadius = dp(activity, 18).toFloat()
                    setColor(if (enabled) accent else 0xFF23232B.toInt())
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isClickable = true
                isFocusable = false
                setOnClickListener { onToggle() }
            }
        )

        return row
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()
}
