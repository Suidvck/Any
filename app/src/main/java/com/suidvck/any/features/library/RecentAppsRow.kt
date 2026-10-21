package com.suidvck.any.features.library

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.suidvck.any.features.data.MiniApp
import com.suidvck.any.features.data.MiniAppStore
import java.util.WeakHashMap

/**
 * Home-screen strip of up to six recently used mini-apps plus a leading
 * "ทั้งหมด" chip that opens the full library. Collapses to GONE when the
 * user has not saved any app yet.
 */
class RecentAppsRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private var store: MiniAppStore? = null
    private var openCallback: ((MiniApp) -> Unit)? = null
    private var seeAllCallback: (() -> Unit)? = null

    init {
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
        clipToPadding = false
        overScrollMode = View.OVER_SCROLL_NEVER
        visibility = View.GONE
        addView(
            row,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
            )
        )
        RecentAppsRowRegistry.register(this)
    }

    fun bind(store: MiniAppStore, onOpen: (MiniApp) -> Unit, onSeeAll: () -> Unit) {
        this.store = store
        this.openCallback = onOpen
        this.seeAllCallback = onSeeAll
        render()
    }

    /** Re-reads the store and redraws with the callbacks from the last [bind]. */
    internal fun refresh() {
        if (store != null && openCallback != null && seeAllCallback != null) render()
    }

    private fun render() {
        val store = this.store ?: return
        val onOpen = this.openCallback ?: return
        val onSeeAll = this.seeAllCallback ?: return

        row.removeAllViews()
        val apps: List<MiniApp> = runCatching { store.list() }.getOrElse { emptyList() }
        if (apps.isEmpty()) {
            visibility = View.GONE
            return
        }

        visibility = View.VISIBLE
        scrollTo(0, 0)
        row.addView(buildAllChip(onSeeAll))
        apps.take(MAX_APPS).forEach { app -> row.addView(buildAppChip(app, onOpen)) }
    }

    private fun buildAllChip(onSeeAll: () -> Unit): View {
        val chip = baseChip()
        chip.contentDescription = "เปิดรายการแอพทั้งหมด"

        val icon = TextView(context).apply {
            text = "🗂️"
            textSize = 17f
        }
        val label = TextView(context).apply {
            text = "ทั้งหมด"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(BRAND_LIGHT)
            maxLines = 1
        }
        chip.addView(icon)
        chip.addView(
            label,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(6)
            }
        )
        chip.setOnClickListener { onSeeAll() }
        chip.layoutParams = chipParams()
        return chip
    }

    private fun buildAppChip(app: MiniApp, onOpen: (MiniApp) -> Unit): View {
        val chip = baseChip()
        chip.contentDescription = "เปิดแอพ ${app.name}"

        val emoji = TextView(context).apply {
            text = app.emoji.ifBlank { "🧩" }
            textSize = 18f
        }
        val name = TextView(context).apply {
            text = clipName(app.name)
            textSize = 13f
            setTextColor(CHIP_TEXT)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        chip.addView(emoji)
        chip.addView(
            name,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(6)
            }
        )
        chip.setOnClickListener { onOpen(app) }
        chip.layoutParams = chipParams()
        return chip
    }

    private fun baseChip(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = chipBackground()
            isClickable = true
            isFocusable = true
        }

    private fun chipParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(CHIP_HEIGHT_DP)).apply {
            marginEnd = dp(8)
        }

    private fun chipBackground(): RippleDrawable {
        val radius = dp(20).toFloat()
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(CHIP_FILL)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, mask)
    }

    private fun clipName(raw: String): String {
        val name = raw.trim().ifEmpty { "ไม่มีชื่อ" }
        if (name.codePointCount(0, name.length) <= MAX_NAME_CHARS) return name
        return name.substring(0, name.offsetByCodePoints(0, MAX_NAME_CHARS)).trimEnd() + "…"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        private const val CHIP_HEIGHT_DP = 64
        private const val MAX_APPS = 6
        private const val MAX_NAME_CHARS = 10
        private val CHIP_FILL = 0xCC23232B.toInt()
        private val CHIP_TEXT = 0xFFEDEDF2.toInt()
        private val BRAND_LIGHT = 0xFFB9BEFF.toInt()
    }
}

/**
 * Tiny weak registry so an open [RecentAppsRow] can redraw itself after the
 * library sheet renames or deletes an app. No strong references are kept.
 */
internal object RecentAppsRowRegistry {
    private val rows = WeakHashMap<RecentAppsRow, Boolean>()

    fun register(row: RecentAppsRow) {
        rows[row] = true
    }

    fun refreshAll() {
        rows.keys.toList().forEach { it.refresh() }
    }
}
