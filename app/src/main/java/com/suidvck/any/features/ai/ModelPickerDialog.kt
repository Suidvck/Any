package com.suidvck.any.features.ai

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.suidvck.any.AiClient
import com.suidvck.any.R
import com.suidvck.any.ui.AnyChrome

class ModelPickerDialog(private val activity: AppCompatActivity) {
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    fun show(baseUrl: String, apiKey: String, onPick: (String) -> Unit) {
        if (baseUrl.isBlank()) {
            val dialog = MaterialAlertDialogBuilder(activity, R.style.AnyDialogTheme)
                .setTitle(R.string.model_search)
                .setMessage(R.string.models_need_base)
                .setPositiveButton(android.R.string.ok, null)
                .create()
            dialog.show()
            AnyChrome.dialog(dialog)
            return
        }

        val loading = MaterialAlertDialogBuilder(activity, R.style.AnyDialogTheme)
            .setMessage(R.string.models_loading)
            .setCancelable(false)
            .create()
        loading.show()
        AnyChrome.dialog(loading)

        AiClient.listModels(baseUrl, apiKey) { result ->
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                loading.dismiss()
                result.onSuccess { models ->
                    if (models.isEmpty()) {
                        val empty = MaterialAlertDialogBuilder(activity, R.style.AnyDialogTheme)
                            .setTitle(R.string.model_search)
                            .setMessage(R.string.models_empty)
                            .setPositiveButton(android.R.string.ok, null)
                            .create()
                        empty.show()
                        AnyChrome.dialog(empty)
                        return@onSuccess
                    }

                    val filter = EditText(activity).apply {
                        hint = activity.getString(R.string.models_filter_hint)
                        setSingleLine()
                        inputType = InputType.TYPE_CLASS_TEXT
                        setTextColor(activity.getColor(R.color.white))
                        setHintTextColor(activity.getColor(R.color.input_hint))
                        textSize = 14f
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFF211F26.toInt()); cornerRadius = dp(14).toFloat() }
                    }
                    val list = ListView(activity).apply {
                        dividerHeight = 0
                        setSelector(android.R.color.transparent)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        overScrollMode = View.OVER_SCROLL_NEVER
                        isVerticalScrollBarEnabled = false
                    }
                    val adapter = object : ArrayAdapter<String>(
                        activity,
                        android.R.layout.simple_list_item_1,
                        ArrayList(models)
                    ) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val view = super.getView(position, convertView, parent)
                            (view as? TextView)?.apply {
                                setTextColor(activity.getColor(R.color.white))
                                textSize = 14f
                                setPadding(dp(14), dp(13), dp(14), dp(13))
                                maxLines = 1
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            }
                            return view
                        }
                    }
                    list.adapter = adapter
                    list.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(320)
                    )

                    lateinit var picker: AlertDialog
                    list.setOnItemClickListener { _, _, position, _ ->
                        val picked = list.getItemAtPosition(position) as? String
                        if (picked != null) {
                            onPick(picked)
                            picker.dismiss()
                        }
                    }
                    filter.doAfterTextChanged { text ->
                        val q = text?.toString()?.trim().orEmpty()
                        adapter.clear()
                        if (q.isEmpty()) {
                            adapter.addAll(models)
                        } else {
                            adapter.addAll(models.filter { m -> m.contains(q, ignoreCase = true) })
                        }
                        adapter.notifyDataSetChanged()
                    }

                    val col = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(16), dp(8), dp(16), 0)
                    }
                    col.addView(filter)
                    col.addView(
                        list,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(320)
                        ).apply { topMargin = dp(10) }
                    )

                    picker = MaterialAlertDialogBuilder(activity, R.style.AnyDialogTheme)
                        .setTitle(activity.getString(R.string.model_search) + " (" + models.size + ")")
                        .setView(col)
                        .setNegativeButton(R.string.cancel, null)
                        .create()
                    picker.show()
                    AnyChrome.dialog(picker)
                }.onFailure { e ->
                    val error = MaterialAlertDialogBuilder(activity, R.style.AnyDialogTheme)
                        .setTitle(R.string.model_search)
                        .setMessage(activity.getString(R.string.error_prefix) + (e.message ?: "unknown error"))
                        .setPositiveButton(android.R.string.ok, null)
                        .create()
                    error.show()
                    AnyChrome.dialog(error)
                }
            }
        }
    }

}
