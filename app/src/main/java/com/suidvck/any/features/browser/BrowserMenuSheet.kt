package com.suidvck.any.features.browser

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import com.suidvck.any.features.data.HistoryEntry
import com.suidvck.any.features.data.HistoryStore
import com.suidvck.any.features.data.TabEntry
import com.suidvck.any.features.data.TabStore
import com.suidvck.any.ui.FaIcons

/*
 * Browser menu sheet: tabs / history / bookmarks plus the desktop-mode and
 * force-dark switches. Everything is built in code so the feature owns a single
 * file and never touches the existing layouts or res/values files.
 */

private const val PREFS_NAME = "settings"
private const val KEY_DESKTOP = "desktop_mode"
private const val KEY_FORCE_DARK = "force_dark"

private val SHEET_BG = 0xFF17171B.toInt()
private val CARD_BG = 0xFF23232B.toInt()
private val BRAND = 0xFF5F6CF5.toInt()
private val TEXT_MAIN = 0xFFFFFFFF.toInt()
private val TEXT_MUTED = 0xFF9A9AA5.toInt()
private val ROSE = 0xFFE2708A.toInt()
private val STAR_ON = 0xFFFFC65C.toInt()
private val DIVIDER = 0x1FFFFFFF
private val HANDLE_BG = 0xFF2E2E38.toInt()
private val SWITCH_TRACK_OFF = 0xFF3A3A46.toInt()
private val SWITCH_THUMB_OFF = 0xFF8E8E9A.toInt()

private val MP = ViewGroup.LayoutParams.MATCH_PARENT
private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Bottom sheet with three sections:
 *  - แท็บ: open tabs (select / close / add new URL)
 *  - ประวัติ: recent history with bookmark stars and a clear action
 *  - บุ๊กมาร์ก: bookmarked pages, star removes
 *
 * The two switches at the bottom persist to SharedPreferences `"settings"`
 * and call [onChanged] immediately so the host can re-apply the WebView flags.
 */
object BrowserMenuSheet {

    fun show(
        activity: Activity,
        tabs: TabStore,
        history: HistoryStore,
        onLoad: (url: String?, appId: String?) -> Unit,
        onCloseTab: (String) -> Unit,
        onChanged: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        BrowserSheet(activity, tabs, history, onLoad, onCloseTab, onChanged).show()
    }
}

private class BrowserSheet(
    private val activity: Activity,
    private val tabs: TabStore,
    private val history: HistoryStore,
    private val onLoad: (String?, String?) -> Unit,
    private val onCloseTab: (String) -> Unit,
    private val onChanged: (() -> Unit)?
) {

    private val density = activity.resources.displayMetrics.density
    private val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private lateinit var dialog: BottomSheetDialog
    private lateinit var subtitleView: TextView

    private lateinit var tabSection: View
    private lateinit var historySection: View
    private lateinit var bookmarkSection: View

    private lateinit var tabList: LinearLayout
    private lateinit var historyList: LinearLayout
    private lateinit var bookmarkList: LinearLayout

    private lateinit var tabScroll: ScrollView
    private lateinit var historyScroll: ScrollView
    private lateinit var bookmarkScroll: ScrollView

    private lateinit var historyCount: TextView
    private lateinit var bookmarkCount: TextView
    private lateinit var clearButton: TextView

    private fun dp(value: Int): Int = (value * density).toInt()

    fun show() {
        val root = buildRoot()
        dialog = BottomSheetDialog(activity)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.behavior.skipCollapsed = true
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.6f)
        // The sheet's own FrameLayout would otherwise draw Material's light surface
        // behind our rounded dark background.
        (root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        try {
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        } catch (_: Throwable) {
        }
        showSection(0)
    }

    // ---------- layout ----------

    private fun buildRoot(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = sheetBackground()
            setPadding(dp(18), dp(4), dp(18), dp(12))
        }

        root.addView(
            View(activity).apply { background = roundBg(HANDLE_BG, dp(2).toFloat()) },
            LinearLayout.LayoutParams(dp(40), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
            }
        )

        root.addView(
            buildHeader(),
            LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(10) }
        )

        val tabStrip = buildTabStrip()
        root.addView(tabStrip, LinearLayout.LayoutParams(MP, dp(48)).apply { topMargin = dp(2) })

        tabSection = buildTabSection()
        historySection = buildHistorySection().apply { visibility = View.GONE }
        bookmarkSection = buildBookmarkSection().apply { visibility = View.GONE }

        val body = FrameLayout(activity)
        body.addView(tabSection, FrameLayout.LayoutParams(MP, WC))
        body.addView(historySection, FrameLayout.LayoutParams(MP, WC))
        body.addView(bookmarkSection, FrameLayout.LayoutParams(MP, WC))
        root.addView(body, LinearLayout.LayoutParams(MP, WC))

        root.addView(
            View(activity).apply { setBackgroundColor(DIVIDER) },
            LinearLayout.LayoutParams(MP, dp(1)).apply { topMargin = dp(8) }
        )
        root.addView(
            switchRow(KEY_DESKTOP, "โหมดเดสก์ท็อป", "ขอเว็บไซต์แบบหน้าจอคอมพิวเตอร์"),
            LinearLayout.LayoutParams(MP, WC)
        )
        root.addView(
            switchRow(KEY_FORCE_DARK, "บังคับโหมดมืด", "ปรับหน้าเว็บที่สว่างให้เป็นโทนมืด"),
            LinearLayout.LayoutParams(MP, WC)
        )
        return root
    }

    private fun buildHeader(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(activity).apply {
            text = "เบราว์เซอร์"
            setTextColor(TEXT_MAIN)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })

        subtitleView = TextView(activity).apply {
            setTextColor(TEXT_MUTED)
            textSize = 11.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        }
        column.addView(subtitleView, LinearLayout.LayoutParams(MP, WC))

        row.addView(column, LinearLayout.LayoutParams(0, WC, 1f))
        row.addView(iconButton("xmark", TEXT_MUTED, 38, description = "ปิด") { dismiss() })
        return row
    }

    private fun buildTabStrip(): TabLayout {
        val strip = TabLayout(activity).apply {
            tabMode = TabLayout.MODE_FIXED
            tabGravity = TabLayout.GRAVITY_FILL
            setSelectedTabIndicatorColor(BRAND)
            setTabIndicatorFullWidth(false)
            setTabTextColors(TEXT_MUTED, TEXT_MAIN)
            setTabRippleColor(ColorStateList.valueOf(0x1AFFFFFF))
            setBackgroundColor(Color.TRANSPARENT)
        }
        strip.addTab(strip.newTab().setText("แท็บ"))
        strip.addTab(strip.newTab().setText("ประวัติ"))
        strip.addTab(strip.newTab().setText("บุ๊กมาร์ก"))
        strip.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showSection(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = showSection(tab.position)
        })
        return strip
    }

    private fun buildTabSection(): View {
        val section = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        tabList = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        tabScroll = MaxHeightScrollView(activity, maxBodyHeight()).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
        }
        tabScroll.addView(tabList, ViewGroup.LayoutParams(MP, WC))

        section.addView(tabScroll, LinearLayout.LayoutParams(MP, WC))
        section.addView(
            buildNewTabRow(),
            LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(10) }
        )
        return section
    }

    private fun buildHistorySection(): View {
        val section = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        historyCount = TextView(activity).apply {
            textSize = 11f
            setTextColor(TEXT_MUTED)
        }
        clearButton = TextView(activity).apply {
            text = "ล้างประวัติ"
            textSize = 12f
            setTextColor(ROSE)
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = rippleBg(Color.TRANSPARENT, dp(9).toFloat())
            isClickable = true
            isFocusable = true
            setOnClickListener { confirmClearHistory() }
        }

        val head = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), 0, dp(2))
        }
        head.addView(historyCount, LinearLayout.LayoutParams(0, WC, 1f))
        head.addView(clearButton, LinearLayout.LayoutParams(WC, WC))
        section.addView(head, LinearLayout.LayoutParams(MP, WC))

        historyList = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        historyScroll = MaxHeightScrollView(activity, maxBodyHeight()).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
        }
        historyScroll.addView(historyList, ViewGroup.LayoutParams(MP, WC))
        section.addView(historyScroll, LinearLayout.LayoutParams(MP, WC))
        return section
    }

    private fun buildBookmarkSection(): View {
        val section = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        bookmarkCount = TextView(activity).apply {
            textSize = 11f
            setTextColor(TEXT_MUTED)
            setPadding(dp(12), dp(6), dp(12), dp(2))
        }
        section.addView(bookmarkCount, LinearLayout.LayoutParams(MP, WC))

        bookmarkList = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        bookmarkScroll = MaxHeightScrollView(activity, maxBodyHeight()).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
        }
        bookmarkScroll.addView(bookmarkList, ViewGroup.LayoutParams(MP, WC))
        section.addView(bookmarkScroll, LinearLayout.LayoutParams(MP, WC))
        return section
    }

    private fun buildNewTabRow(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val input = EditText(activity).apply {
            hint = "วางลิงก์หรือ URL"
            setTextColor(TEXT_MAIN)
            setHintTextColor(TEXT_MUTED)
            textSize = 14f
            isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            background = roundBg(CARD_BG, dp(14).toFloat())
            setPadding(dp(14), 0, dp(14), 0)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    openRawUrl(this)
                    true
                } else {
                    false
                }
            }
        }

        val openButton = TextView(activity).apply {
            text = "เปิด"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = rippleBg(BRAND, dp(14).toFloat())
            isClickable = true
            isFocusable = true
            setPadding(dp(18), 0, dp(18), 0)
            setOnClickListener { openRawUrl(input) }
        }

        row.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(openButton, LinearLayout.LayoutParams(WC, dp(48)).apply { marginStart = dp(8) })
        return row
    }

    private fun switchRow(key: String, title: String, description: String): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(7), dp(2), dp(7))
        }

        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(activity).apply {
            text = title
            setTextColor(TEXT_MAIN)
            textSize = 14f
        })
        column.addView(TextView(activity).apply {
            text = description
            setTextColor(TEXT_MUTED)
            textSize = 11f
            setPadding(0, dp(1), 0, 0)
        })

        val toggle = buildToggle(prefs.getBoolean(key, false)) { checked ->
            prefs.edit().putBoolean(key, checked).apply()
            onChanged?.invoke()
        }

        row.addView(column, LinearLayout.LayoutParams(0, WC, 1f))
        row.addView(toggle, LinearLayout.LayoutParams(WC, WC).apply { marginStart = dp(10) })
        return row
    }

    /** Compact เปิด/ปิด pill instead of SwitchCompat (some ROMs crash measuring its empty text). */
    private fun buildToggle(initial: Boolean, onChange: (Boolean) -> Unit): TextView {
        var checked = initial
        val pill = TextView(activity)
        fun render() {
            pill.text = if (checked) "เปิด" else "ปิด"
            pill.setTextColor(if (checked) Color.WHITE else TEXT_MUTED)
            pill.background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (checked) BRAND else CARD_BG)
            }
        }
        pill.apply {
            textSize = 13f
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            minWidth = dp(64)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                checked = !checked
                render()
                onChange(checked)
            }
        }
        render()
        return pill
    }

    // ---------- sections ----------

    private fun showSection(index: Int) {
        tabSection.visibility = if (index == 0) View.VISIBLE else View.GONE
        historySection.visibility = if (index == 1) View.VISIBLE else View.GONE
        bookmarkSection.visibility = if (index == 2) View.VISIBLE else View.GONE
        when (index) {
            0 -> renderTabs()
            1 -> renderHistory()
            else -> renderBookmarks()
        }
        updateSubtitle()
    }

    private fun renderTabs() {
        val scrollY = tabScroll.scrollY
        tabList.removeAllViews()

        val items = try {
            tabs.list()
        } catch (_: Throwable) {
            emptyList<TabEntry>()
        }
        val currentId = try {
            tabs.current()?.id
        } catch (_: Throwable) {
            null
        }
        val limited = items.take(12)

        if (limited.isEmpty()) {
            tabList.addView(emptyView("ยังไม่มีแท็บที่เปิดอยู่ · พิมพ์ลิงก์ด้านล่างเพื่อเปิด"))
        } else {
            for (entry in limited) {
                tabList.addView(tabRow(entry, entry.id == currentId))
            }
        }
        tabScroll.post { tabScroll.scrollTo(0, scrollY) }
    }

    private fun renderHistory() {
        val scrollY = historyScroll.scrollY
        historyList.removeAllViews()

        val items = try {
            history.recent(100)
        } catch (_: Throwable) {
            emptyList<HistoryEntry>()
        }
        historyCount.text = "ทั้งหมด ${items.size} รายการ"
        clearButton.isEnabled = items.isNotEmpty()
        clearButton.alpha = if (items.isEmpty()) 0.4f else 1f

        if (items.isEmpty()) {
            historyList.addView(emptyView("ยังไม่มีประวัติการเข้าชม"))
        } else {
            for (entry in items) historyList.addView(historyRow(entry))
        }
        historyScroll.post { historyScroll.scrollTo(0, scrollY) }
    }

    private fun renderBookmarks() {
        val scrollY = bookmarkScroll.scrollY
        bookmarkList.removeAllViews()

        val items = try {
            history.bookmarks()
        } catch (_: Throwable) {
            emptyList<HistoryEntry>()
        }
        bookmarkCount.text = "${items.size} รายการ"

        if (items.isEmpty()) {
            bookmarkList.addView(emptyView("ยังไม่มีบุ๊กมาร์ก · แตะรูปดาวในประวัติเพื่อบันทึก"))
        } else {
            for (entry in items) bookmarkList.addView(historyRow(entry))
        }
        bookmarkScroll.post { bookmarkScroll.scrollTo(0, scrollY) }
    }

    private fun updateSubtitle() {
        val current = try {
            tabs.current()
        } catch (_: Throwable) {
            null
        }
        subtitleView.text = when {
            current == null -> "ยังไม่มีแท็บที่เปิดอยู่"
            current.title.isNotBlank() -> current.title
            else -> labelFor(current)
        }
    }

    // ---------- rows ----------

    private fun tabRow(entry: TabEntry, current: Boolean): View {
        val close = iconButton("xmark", TEXT_MUTED, description = "ปิดแท็บ") {
            try {
                onCloseTab(entry.id)
            } catch (_: Throwable) {
            }
            renderTabs()
            updateSubtitle()
        }
        val title = titleFor(entry)
        val subtitle = labelFor(entry)
        return listRow(
            title = title,
            subtitle = if (subtitle == title) "" else subtitle,
            highlighted = current,
            bold = current,
            badge = if (current) "• กำลังเปิด" else null,
            trailing = close
        ) {
            val picked = try {
                tabs.select(entry.id)
            } catch (_: Throwable) {
                null
            } ?: entry
            dismiss()
            onLoad(picked.url, picked.appId)
        }
    }

    private fun historyRow(entry: HistoryEntry): View {
        val starred = entry.bookmarked
        val star = iconButton(
            "star",
            if (starred) STAR_ON else TEXT_MUTED,
            regular = !starred,
            description = if (starred) "เอาบุ๊กมาร์กออก" else "บันทึกบุ๊กมาร์ก"
        ) {
            try {
                history.toggleBookmark(entry.url)
            } catch (_: Throwable) {
            }
            renderHistory()
            renderBookmarks()
        }
        val urlText = prettyUrl(entry.url)
        val title = if (entry.title.isBlank()) urlText else entry.title
        return listRow(
            title = title,
            subtitle = if (urlText == title) "" else urlText,
            highlighted = false,
            bold = false,
            badge = null,
            trailing = star
        ) {
            dismiss()
            onLoad(entry.url, null)
        }
    }

    private fun listRow(
        title: String,
        subtitle: String,
        highlighted: Boolean,
        bold: Boolean,
        badge: String?,
        trailing: View?,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(7), dp(9))
            background = rippleBg(if (highlighted) CARD_BG else Color.TRANSPARENT, dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(2) }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(activity).apply {
            text = title
            setTextColor(TEXT_MAIN)
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(MP, WC))

        if (subtitle.isNotBlank()) {
            column.addView(TextView(activity).apply {
                text = subtitle
                setTextColor(TEXT_MUTED)
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
                setPadding(0, dp(2), 0, 0)
            }, LinearLayout.LayoutParams(MP, WC))
        }
        row.addView(column, LinearLayout.LayoutParams(0, WC, 1f))

        if (!badge.isNullOrBlank()) {
            row.addView(TextView(activity).apply {
                text = badge
                setTextColor(BRAND)
                textSize = 10f
                maxLines = 1
            }, LinearLayout.LayoutParams(WC, WC).apply { marginStart = dp(6) })
        }
        if (trailing != null) row.addView(trailing)
        return row
    }

    private fun emptyView(text: String): View = TextView(activity).apply {
        this.text = text
        setTextColor(TEXT_MUTED)
        textSize = 12.5f
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(26), dp(10), dp(26))
        layoutParams = LinearLayout.LayoutParams(MP, WC)
    }

    private fun iconButton(
        icon: String,
        color: Int,
        sizeDp: Int = 34,
        regular: Boolean = false,
        description: String? = null,
        onClick: () -> Unit
    ): TextView = TextView(activity).apply {
        FaIcons.setIcon(this, icon, 15f, color, regular)
        gravity = Gravity.CENTER
        contentDescription = description
        isClickable = true
        isFocusable = true
        background = circleRipple()
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        setOnClickListener { onClick() }
    }

    // ---------- actions ----------

    private fun openRawUrl(input: EditText) {
        val raw = input.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return
        val url = normalizeUrl(raw)
        try {
            tabs.addTab(url, null, url)
        } catch (_: Throwable) {
        }
        input.setText("")
        input.clearFocus()
        dismiss()
        onLoad(url, null)
    }

    private fun confirmClearHistory() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("ล้างประวัติ")
            .setMessage("ลบประวัติการเข้าชมทั้งหมด? บุ๊กมาร์กจะยังคงอยู่")
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("ล้าง") { _, _ ->
                try {
                    history.clearAll()
                } catch (_: Throwable) {
                }
                renderHistory()
                renderBookmarks()
            }
            .show()
    }

    private fun dismiss() {
        try {
            if (dialog.isShowing) dialog.dismiss()
        } catch (_: Throwable) {
        }
    }

    // ---------- helpers ----------

    private fun maxBodyHeight(): Int {
        val screen = activity.resources.displayMetrics.heightPixels
        val target = (screen * 0.42f).toInt()
        val ceiling = (screen - dp(330)).coerceAtLeast(dp(140))
        return target.coerceAtMost(ceiling).coerceAtLeast(dp(120))
    }

    private fun labelFor(entry: TabEntry): String {
        val url = entry.url
        return when {
            !url.isNullOrBlank() -> prettyUrl(url)
            !entry.appId.isNullOrBlank() -> "แอพที่บันทึกไว้"
            else -> "แอพในเครื่อง"
        }
    }

    private fun titleFor(entry: TabEntry): String {
        val title = entry.title.trim()
        return if (title.isEmpty() || title == "about:blank") labelFor(entry) else title
    }

    private fun prettyUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val noScheme = if (trimmed.contains("://")) trimmed.substringAfter("://") else trimmed
        return noScheme.removePrefix("www.").trimEnd('/').ifEmpty { trimmed }
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val lower = trimmed.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> trimmed
            lower.startsWith("about:") || lower.startsWith("file:") -> trimmed
            else -> "https://$trimmed"
        }
    }

    private fun sheetBackground(): Drawable {
        val radius = dp(22).toFloat()
        return GradientDrawable().apply {
            setColor(SHEET_BG)
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        }
    }

    private fun roundBg(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
    }

    private fun rippleBg(baseColor: Int, radius: Float): Drawable {
        return RippleDrawable(
            ColorStateList.valueOf(0x1FFFFFFF),
            roundBg(baseColor, radius),
            roundBg(Color.WHITE, radius)
        )
    }

    private fun circleRipple(color: Int = 0x26FFFFFF): Drawable {
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(color), null, mask)
    }
}

/** ScrollView that caps its measured height so short lists stay compact. */
private class MaxHeightScrollView(
    context: Context,
    private val maxHeightPx: Int
) : ScrollView(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (maxHeightPx <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val limited = MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, limited)
    }
}
