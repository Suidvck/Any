package com.suidvck.any.features.system

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.suidvck.any.R
import com.suidvck.any.features.data.Automation
import com.suidvck.any.features.data.AutomationStore
import com.suidvck.any.features.data.MiniAppStore
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Bottom sheet that lists the user's daily automations and lets them add,
 * edit, enable/disable or delete entries. All edits are persisted through
 * [AutomationStore] and immediately re-synced with [AutomationScheduler].
 */
object AutomationSheet {

    private val SHEET = 0xFF17171B.toInt()
    private val CARD = 0xFF23232B.toInt()
    private val BRAND = 0xFF5F6CF5.toInt()
    private val TEXT = 0xFFFFFFFF.toInt()
    private val MUTED = 0xFF9A9AA5.toInt()
    private val ROSE = 0xFFE2708A.toInt()

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private const val PREFS = "settings"
    private const val PREF_NOTIF_ASKED = "automation_notif_asked"
    private const val REQUEST_NOTIFICATIONS = 4201

    fun show(activity: Activity, onChange: (() -> Unit)? = null) {
        if (activity.isFinishing || activity.isDestroyed) return

        val ctx = activity
        val app = activity.applicationContext
        val store = AutomationStore(app)
        val dialog = BottomSheetDialog(ctx)

        val listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = NestedScrollView(ctx).apply {
            isFillViewport = true
            addView(listBox, FrameLayout.LayoutParams(MATCH, WRAP))
        }

        fun refresh() {
            listBox.removeAllViews()
            val items = try {
                store.list().sortedWith(compareBy<Automation>({ it.hour }, { it.minute }))
            } catch (t: Throwable) {
                emptyList()
            }

            if (items.isEmpty()) {
                listBox.addView(emptyState(ctx), LinearLayout.LayoutParams(MATCH, MATCH))
            } else {
                for (a in items) {
                    val row = automationRow(
                        ctx = ctx,
                        a = a,
                        onToggle = { checked ->
                            store.setEnabled(a.id, checked)
                            AutomationScheduler.rescheduleAll(app)
                            onChange?.invoke()
                        },
                        onEdit = {
                            openEditor(ctx, store, a) {
                                refresh()
                                onChange?.invoke()
                            }
                        },
                        onDelete = {
                            confirmDelete(ctx, a) {
                                store.delete(a.id)
                                AutomationScheduler.cancel(app, a.id)
                                AutomationScheduler.rescheduleAll(app)
                                refresh()
                                onChange?.invoke()
                            }
                        }
                    )
                    listBox.addView(row)
                }
            }

            val rowCount = items.size
            val maxHeight = (ctx.resources.displayMetrics.heightPixels * 0.52f).toInt()
            val height = if (rowCount == 0) {
                dp(ctx, 168)
            } else {
                (rowCount * dp(ctx, 74) + dp(ctx, 8)).coerceAtMost(maxHeight)
            }
            scroll.layoutParams = LinearLayout.LayoutParams(MATCH, height).apply {
                topMargin = dp(ctx, 12)
            }
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), dp(ctx, 18))
        }

        // Drag handle.
        root.addView(
            View(ctx).apply { background = rounded(ctx, 0x33FFFFFF, 999f) },
            LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(ctx, 12)
            }
        )

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            textView(ctx, "ตั้งเวลาอัตโนมัติ", 20f, TEXT, true),
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        header.addView(
            TextView(ctx).apply {
                text = "＋ เพิ่ม"
                setTextColor(BRAND)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
                background = rounded(ctx, 0x265F6CF5, 999f)
                isClickable = true
                setOnClickListener {
                    openEditor(ctx, store, null) {
                        refresh()
                        onChange?.invoke()
                    }
                }
            }
        )
        root.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(
            textView(ctx, "ให้ Any ทำงานแทนคุณตามเวลา · แจ้งเตือนทุกวัน", 12f, MUTED).apply {
                setPadding(0, dp(ctx, 5), 0, 0)
            },
            LinearLayout.LayoutParams(MATCH, WRAP)
        )

        root.addView(scroll, LinearLayout.LayoutParams(MATCH, WRAP))
        refresh()

        dialog.setContentView(root)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            if (sheet != null) {
                sheet.setBackgroundDrawable(sheetBackground(ctx))
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        dialog.show()
    }

    // ---------------------------------------------------------------- rows

    private fun emptyState(ctx: Context): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(ctx, 12), dp(ctx, 24), dp(ctx, 12), dp(ctx, 24))
        addView(textView(ctx, "ยังไม่มีการตั้งเวลา", 15f, TEXT, true))
        addView(
            textView(ctx, "กด “เพิ่ม” เพื่อให้ Any ทำงานให้คุณตามเวลา", 12f, MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(ctx, 6), 0, 0)
            }
        )
    }

    private fun automationRow(
        ctx: Context,
        a: Automation,
        onToggle: (Boolean) -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 6), dp(ctx, 12))
            setBackgroundResource(R.drawable.automation_bg_row)
            isClickable = true
            isLongClickable = true
            setOnClickListener { onEdit() }
            setOnLongClickListener { onDelete(); true }
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = dp(ctx, 8)
            }
        }

        row.addView(
            textView(ctx, automationTimeLabel(a.hour, a.minute), 21f, if (a.enabled) TEXT else MUTED, true)
                .apply {
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    minWidth = dp(ctx, 66)
                }
        )

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 10), 0, dp(ctx, 8), 0)
        }
        column.addView(
            textView(ctx, a.label, 13f, if (a.enabled) TEXT else MUTED).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        )
        column.addView(
            textView(ctx, AutomationInfo.kindLabel(a.kind), 11f, MUTED).apply {
                setPadding(0, dp(ctx, 2), 0, 0)
            }
        )
        row.addView(column, LinearLayout.LayoutParams(0, WRAP, 1f))

        row.addView(
            ImageButton(ctx).apply {
                setImageResource(R.drawable.automation_ic_delete)
                background = null
                setColorFilter(MUTED)
                setPadding(dp(ctx, 5), dp(ctx, 5), dp(ctx, 5), dp(ctx, 5))
                contentDescription = "ลบ"
                setOnClickListener { onDelete() }
            },
            LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34))
        )

        var enabledNow = a.enabled
        val toggle = TextView(ctx)
        fun paintToggle() {
            toggle.text = if (enabledNow) "เปิด" else "ปิด"
            toggle.setTextColor(if (enabledNow) Color.WHITE else MUTED)
            toggle.background = GradientDrawable().apply {
                cornerRadius = dp(ctx, 16).toFloat()
                setColor(if (enabledNow) BRAND else CARD)
            }
        }
        toggle.apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 14), dp(ctx, 7), dp(ctx, 14), dp(ctx, 7))
            minWidth = dp(ctx, 60)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                enabledNow = !enabledNow
                paintToggle()
                onToggle(enabledNow)
            }
        }
        paintToggle()
        row.addView(toggle, LinearLayout.LayoutParams(WRAP, WRAP))

        return row
    }

    // -------------------------------------------------------------- editor

    private fun openEditor(
        activity: Activity,
        store: AutomationStore,
        existing: Automation?,
        onSaved: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val ctx = activity
        val apps = try {
            MiniAppStore(ctx.applicationContext).list()
        } catch (t: Throwable) {
            emptyList()
        }

        val now = Calendar.getInstance()
        var hour = existing?.hour ?: now.get(Calendar.HOUR_OF_DAY)
        var minute = existing?.minute ?: now.get(Calendar.MINUTE)
        val initialKind = existing?.kind
            ?.let { kind -> AutomationInfo.KINDS.indexOf(kind) }
            ?.takeIf { it >= 0 }
            ?: 0
        var lastKind = initialKind
        var appIndex = apps.indexOfFirst { it.id == existing?.value }.let {
            if (it < 0) 0 else it
        }

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 18), dp(ctx, 20), dp(ctx, 8))
            background = rounded(ctx, SHEET, 20f)
        }

        content.addView(
            textView(
                ctx,
                if (existing == null) "เพิ่มการตั้งเวลา" else "แก้ไขการตั้งเวลา",
                18f,
                TEXT,
                true
            )
        )
        content.addView(
            textView(ctx, "Any จะแจ้งเตือนและเปิดสิ่งที่เลือกให้อัตโนมัติ", 12f, MUTED).apply {
                setPadding(0, dp(ctx, 4), 0, 0)
            }
        )

        content.addView(sectionLabel(ctx, "เวลา"))
        val timeButton = TextView(ctx).apply {
            text = automationTimeLabel(hour, minute)
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10))
            background = rounded(ctx, CARD, 14f)
            isClickable = true
            setOnClickListener {
                TimePickerDialog(ctx, { _, h, m ->
                    hour = h
                    minute = m
                    text = automationTimeLabel(hour, minute)
                }, hour, minute, true).show()
            }
        }
        content.addView(timeButton, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = dp(ctx, 6)
        })

        content.addView(sectionLabel(ctx, "ประเภท"))
        val kindSpinner = Spinner(ctx).apply {
            adapter = darkAdapter(ctx, AutomationInfo.KINDS.map { AutomationInfo.kindLabel(it) })
            backgroundTintList = ColorStateList.valueOf(MUTED)
            setSelection(initialKind)
        }
        content.addView(kindSpinner, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = dp(ctx, 2)
        })

        val valueLabel = sectionLabel(ctx, "รายละเอียด")
        content.addView(valueLabel, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = dp(ctx, 12)
        })

        val valueInput = EditText(ctx).apply {
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            background = rounded(ctx, CARD, 14f)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        content.addView(valueInput, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = dp(ctx, 6)
        })

        val appSpinner = Spinner(ctx).apply {
            adapter = darkAdapter(ctx, apps.map { it.name })
            backgroundTintList = ColorStateList.valueOf(MUTED)
            setSelection(appIndex)
            visibility = View.GONE
        }
        content.addView(appSpinner, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = dp(ctx, 6)
        })

        val noApps = textView(ctx, "ยังไม่มีแอพที่บันทึกไว้ — สร้างแอพใน Any ก่อน", 12f, MUTED).apply {
            setPadding(0, dp(ctx, 8), 0, 0)
            visibility = View.GONE
        }
        content.addView(noApps, LinearLayout.LayoutParams(MATCH, WRAP))

        fun syncFields(index: Int) {
            when (AutomationInfo.KINDS[index]) {
                AutomationWorker.KIND_URL -> {
                    valueLabel.text = "ลิงก์เว็บ"
                    valueInput.visibility = View.VISIBLE
                    valueInput.hint = "https://example.com"
                    valueInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    valueInput.setSingleLine(true)
                    valueInput.minLines = 1
                    valueInput.maxLines = 1
                    valueInput.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    appSpinner.visibility = View.GONE
                    noApps.visibility = View.GONE
                }
                AutomationWorker.KIND_PROMPT -> {
                    valueLabel.text = "คำสั่งให้ AI"
                    valueInput.visibility = View.VISIBLE
                    valueInput.hint = "เช่น สรุปข่าวเทคโนโลยีวันนี้ให้ฟังหน่อย"
                    valueInput.inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    valueInput.setSingleLine(false)
                    valueInput.minLines = 3
                    valueInput.maxLines = 5
                    valueInput.gravity = Gravity.TOP or Gravity.START
                    appSpinner.visibility = View.GONE
                    noApps.visibility = View.GONE
                }
                AutomationWorker.KIND_CHAT -> {
                    valueLabel.text = "ชื่อแชท (ไม่บังคับ)"
                    valueInput.visibility = View.VISIBLE
                    valueInput.hint = "เว้นว่างเพื่อเปิดแชทใหม่"
                    valueInput.inputType = InputType.TYPE_CLASS_TEXT
                    valueInput.setSingleLine(true)
                    valueInput.minLines = 1
                    valueInput.maxLines = 1
                    valueInput.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    appSpinner.visibility = View.GONE
                    noApps.visibility = View.GONE
                }
                else -> {
                    valueLabel.text = "แอพที่ต้องการเปิด"
                    valueInput.visibility = View.GONE
                    appSpinner.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
                    noApps.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        syncFields(initialKind)
        if (existing != null && existing.kind != AutomationWorker.KIND_APP) {
            valueInput.setText(existing.value)
        }

        kindSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != lastKind) {
                    lastKind = position
                    valueInput.setText("")
                }
                syncFields(position.coerceIn(0, AutomationInfo.KINDS.size - 1))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        appSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                appIndex = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        fun collect(): Automation? {
            val kindPosition = kindSpinner.selectedItemPosition
                .coerceIn(0, AutomationInfo.KINDS.size - 1)
            val kind = AutomationInfo.KINDS[kindPosition]
            val value = when (kind) {
                AutomationWorker.KIND_APP -> apps.getOrNull(
                    appSpinner.selectedItemPosition.coerceIn(0, (apps.size - 1).coerceAtLeast(0))
                )?.id ?: ""
                else -> valueInput.text?.toString()?.trim().orEmpty()
            }

            when (kind) {
                AutomationWorker.KIND_URL -> if (!isHttpUrl(value)) {
                    toast(ctx, "กรุณากรอกลิงก์ที่ขึ้นต้นด้วย http:// หรือ https://")
                    return null
                }
                AutomationWorker.KIND_PROMPT -> if (value.isBlank()) {
                    toast(ctx, "กรุณากรอกคำสั่งให้ AI")
                    return null
                }
                AutomationWorker.KIND_APP -> if (value.isBlank()) {
                    toast(ctx, "ยังไม่มีแอพที่บันทึกไว้")
                    return null
                }
            }

            val short = (when (kind) {
                AutomationWorker.KIND_APP -> apps.firstOrNull { it.id == value }?.name ?: value
                AutomationWorker.KIND_CHAT -> if (value.isBlank()) "แชทใหม่" else value
                else -> value
            }).let { if (it.length > 28) it.take(27) + "…" else it }

            return Automation(
                id = existing?.id ?: UUID.randomUUID().toString().take(8),
                hour = hour,
                minute = minute,
                kind = kind,
                value = value,
                label = "${automationTimeLabel(hour, minute)} · " +
                    "${AutomationInfo.kindLabel(kind)} · $short",
                enabled = existing?.enabled ?: true
            )
        }

        val builder = MaterialAlertDialogBuilder(ctx)
            .setView(content)
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("บันทึก", null)
        if (existing != null) builder.setNeutralButton("ลบ", null)
        val dialog = builder.create()

        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val width = (ctx.resources.displayMetrics.widthPixels * 0.9f)
                    .toInt()
                    .coerceAtMost(dp(ctx, 460))
                window.setLayout(width, WRAP)
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
                setTextColor(BRAND)
                setOnClickListener {
                    val automation = collect() ?: return@setOnClickListener
                    store.upsert(automation)
                    AutomationScheduler.rescheduleAll(ctx.applicationContext)
                    onSaved()
                    dialog.dismiss()
                    if (existing == null) requestNotificationPermission(activity)
                }
            }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(MUTED)
            if (existing != null) {
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.apply {
                    setTextColor(ROSE)
                    setOnClickListener {
                        dialog.dismiss()
                        confirmDelete(activity, existing) {
                            store.delete(existing.id)
                            AutomationScheduler.cancel(ctx.applicationContext, existing.id)
                            AutomationScheduler.rescheduleAll(ctx.applicationContext)
                            onSaved()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(activity: Activity, a: Automation, onDeleted: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("ลบการตั้งเวลา?")
            .setMessage("${automationTimeLabel(a.hour, a.minute)} · ${a.label}\nรายการนี้จะถูกลบถาวร")
            .setNegativeButton("ยกเลิก", null)
            .setPositiveButton("ลบ", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
                setTextColor(ROSE)
                setOnClickListener {
                    onDeleted()
                    dialog.dismiss()
                }
            }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setTextColor(MUTED)
        }
        dialog.show()
    }

    // ------------------------------------------------------------- helpers

    private fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted || prefs.getBoolean(PREF_NOTIF_ASKED, false)) return
        prefs.edit().putBoolean(PREF_NOTIF_ASKED, true).apply()
        try {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS
            )
        } catch (t: Throwable) {
            // Activity may be finishing; the user can still enable it in settings.
        }
    }

    private fun isHttpUrl(value: String): Boolean {
        if (value.isBlank()) return false
        return try {
            val uri = android.net.Uri.parse(value)
            val scheme = uri.scheme?.lowercase(Locale.US)
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (t: Throwable) {
            false
        }
    }

    private fun darkAdapter(ctx: Context, items: List<String>): ArrayAdapter<String> {
        val adapter = object : ArrayAdapter<String>(
            ctx,
            android.R.layout.simple_spinner_item,
            items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.let {
                    it.setTextColor(TEXT)
                    it.textSize = 14f
                    it.maxLines = 1
                    it.ellipsize = TextUtils.TruncateAt.END
                }
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.let {
                    it.setTextColor(TEXT)
                    it.textSize = 14f
                    it.setBackgroundColor(CARD)
                    it.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12))
                }
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }

    private fun sectionLabel(ctx: Context, text: String): TextView =
        textView(ctx, text, 12f, MUTED).apply {
            setPadding(0, dp(ctx, 14), 0, 0)
            letterSpacing = 0.02f
        }

    private fun textView(
        ctx: Context,
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false
    ): TextView = TextView(ctx).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private fun rounded(ctx: Context, color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp * ctx.resources.displayMetrics.density
        }

    private fun sheetBackground(ctx: Context): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(SHEET)
        val r = dp(ctx, 22).toFloat()
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    }

    private fun dp(ctx: Context, value: Int): Int =
        (value * ctx.resources.displayMetrics.density + 0.5f).toInt()

    private fun toast(ctx: Context, message: String) {
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }
}
