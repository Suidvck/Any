package com.suidvck.any.features.ai

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.suidvck.any.AiClient
import com.suidvck.any.R
import com.suidvck.any.features.miniapp.MiniAppPermissions
import com.suidvck.any.ui.AnyChrome
import com.suidvck.any.ui.ChoiceSheet
import com.suidvck.any.ui.FaIcons

data class AiProvider(val name: String, val base: String, val model: String)

class SettingsDialog(private val activity: AppCompatActivity) {
    companion object {
        const val DEFAULT_MAX_CONTEXT = 40
        val DEFAULT_PROVIDERS = listOf(
            AiProvider("Gemini (ฟรี)", AiClient.DEFAULT_BASE, AiClient.DEFAULT_MODEL),
            AiProvider("OpenAI", "https://api.openai.com/v1/", "gpt-4o-mini"),
            AiProvider("OpenRouter", "https://openrouter.ai/api/v1/", "google/gemini-2.5-flash"),
            AiProvider("Groq", "https://api.groq.com/openai/v1/", "llama-3.3-70b-versatile"),
            AiProvider("Custom", "", "")
        )
    }
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
    private fun roundedBg(color: Int, radiusPx: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radiusPx }

    fun show(
        providers: List<AiProvider>,
        isMiniAppActive: () -> Boolean,
        onPermissionsChanged: () -> Unit,
        onOpenPermissions: (onChanged: () -> Unit) -> Unit,
        onSaved: (adBlockEnabled: Boolean) -> Unit
    ) {
        val prefs = activity.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val savedProvider = prefs.getInt("provider", 0).coerceIn(0, providers.size - 1)
        val pad = dp(20)

        fun lp() = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }

        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(6), pad, dp(4))
            minimumWidth = dp(280)
        }

        val providerSpinner = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(0xFF211F26.toInt(), dp(14).toFloat())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            contentDescription = activity.getString(R.string.provider)
        }
        val providerValue = TextView(activity).apply {
            text = providers[savedProvider].name
            setTextColor(activity.getColor(R.color.white))
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        providerSpinner.addView(
            providerValue,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        providerSpinner.addView(
            FaIcons.make(activity, "chevron-down", 12f, activity.getColor(R.color.icon_muted)),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        )

        val baseInput = EditText(activity).apply {
            hint = activity.getString(R.string.base_url)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            setText(prefs.getString("base", AiClient.DEFAULT_BASE))
            setTextColor(activity.getColor(R.color.white))
            setHintTextColor(activity.getColor(R.color.input_hint))
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(0xFF211F26.toInt(), dp(14).toFloat())
        }
        val modelInput = EditText(activity).apply {
            hint = activity.getString(R.string.model)
            setSingleLine()
            setText(prefs.getString("model", AiClient.DEFAULT_MODEL))
            setTextColor(activity.getColor(R.color.white))
            setHintTextColor(activity.getColor(R.color.input_hint))
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(0xFF211F26.toInt(), dp(14).toFloat())
        }
        val keyInput = EditText(activity).apply {
            hint = activity.getString(R.string.api_key)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
            setText(prefs.getString("key", ""))
            setTextColor(activity.getColor(R.color.white))
            setHintTextColor(activity.getColor(R.color.input_hint))
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(0xFF211F26.toInt(), dp(14).toFloat())
        }
        val maxCtxInput = EditText(activity).apply {
            hint = activity.getString(R.string.max_context)
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            setText(prefs.getInt("maxContext", DEFAULT_MAX_CONTEXT).toString())
            setTextColor(activity.getColor(R.color.white))
            setHintTextColor(activity.getColor(R.color.input_hint))
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(0xFF211F26.toInt(), dp(14).toFloat())
        }
        val adBlockCheck = CheckBox(activity).apply {
            setText(R.string.adblock_label)
            isChecked = prefs.getBoolean("adblock", true)
            setTextColor(activity.getColor(R.color.white))
            buttonTintList = android.content.res.ColorStateList.valueOf(
                activity.getColor(R.color.brand)
            )
        }
        val webSearchCheck = CheckBox(activity).apply {
            setText(R.string.web_search_label)
            isChecked = prefs.getBoolean("web_search", true)
            setTextColor(activity.getColor(R.color.white))
            buttonTintList = android.content.res.ColorStateList.valueOf(
                activity.getColor(R.color.brand)
            )
        }

        val permissionSummary = TextView(activity).apply {
            textSize = 11f
            setTextColor(activity.getColor(R.color.icon_muted))
            text = activity.getString(
                R.string.miniapp_permissions_summary,
                MiniAppPermissions.load(activity).enabledCount,
                MiniAppPermissions.TOGGLE_COUNT
            )
        }
        val permissionsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(0xFF211F26.toInt(), dp(14).toFloat())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            contentDescription = activity.getString(R.string.miniapp_permissions)
            setOnClickListener {
                onOpenPermissions {
                    if (isMiniAppActive()) onPermissionsChanged()
                    permissionSummary.text = activity.getString(
                        R.string.miniapp_permissions_summary,
                        MiniAppPermissions.load(activity).enabledCount,
                        MiniAppPermissions.TOGGLE_COUNT
                    )
                }
            }
        }
        val permissionColumn = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        permissionColumn.addView(TextView(activity).apply {
            text = activity.getString(R.string.miniapp_permissions)
            setTextColor(activity.getColor(R.color.white))
            textSize = 14f
        })
        permissionColumn.addView(permissionSummary)
        permissionsRow.addView(
            permissionColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        permissionsRow.addView(
            FaIcons.make(activity, "chevron-right", 12f, activity.getColor(R.color.icon_muted))
        )

        var lastProvider = savedProvider
        providerSpinner.setOnClickListener {
            ChoiceSheet.show(
                activity,
                activity.getString(R.string.provider),
                null,
                providers.map { it.name },
                lastProvider
            ) { position ->
                if (position != lastProvider) {
                    lastProvider = position
                    providerValue.text = providers[position].name
                    val p = providers[position]
                    if (p.base.isNotEmpty()) {
                        baseInput.setText(p.base)
                        modelInput.setText(p.model)
                    }
                }
            }
        }

        col.addView(TextView(activity).apply {
            setText(R.string.provider)
            textSize = 12f
            setTextColor(activity.getColor(R.color.icon_muted))
        })
        val modelRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        modelRow.addView(
            modelInput,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        modelRow.addView(
            TextView(activity).apply {
                setText(R.string.search)
                setTextColor(activity.getColor(R.color.white))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                    roundedBg(0xFF2C2C35.toInt(), dp(14).toFloat()),
                    null
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    ModelPickerDialog(activity).show(
                        baseInput.text.toString().trim(),
                        keyInput.text.toString().trim()
                    ) { picked -> modelInput.setText(picked) }
                }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
        )

        col.addView(providerSpinner)
        col.addView(baseInput, lp())
        col.addView(modelRow, lp())
        col.addView(keyInput, lp())
        col.addView(TextView(activity).apply {
            setText(R.string.max_context)
            textSize = 12f
            setTextColor(activity.getColor(R.color.icon_muted))
        }, lp())
        col.addView(maxCtxInput)
        col.addView(adBlockCheck, lp())
        col.addView(webSearchCheck, lp())
        col.addView(permissionsRow, lp())

        val settingsDialog = MaterialAlertDialogBuilder(activity, R.style.AnyDialogTheme)
            .setTitle(R.string.settings)
            .setView(col)
            .setPositiveButton(R.string.save) { _, _ ->
                prefs.edit()
                    .putInt("provider", lastProvider)
                    .putString("base", baseInput.text.toString().trim().ifBlank { AiClient.DEFAULT_BASE })
                    .putString("model", modelInput.text.toString().trim().ifBlank { AiClient.DEFAULT_MODEL })
                    .putString("key", keyInput.text.toString().trim())
                    .putInt(
                        "maxContext",
                        (maxCtxInput.text.toString().toIntOrNull() ?: DEFAULT_MAX_CONTEXT).coerceIn(2, 200)
                    )
                    .putBoolean("adblock", adBlockCheck.isChecked)
                    .putBoolean("web_search", webSearchCheck.isChecked)
                    .apply()
                onSaved(adBlockCheck.isChecked)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        settingsDialog.show()
        AnyChrome.dialog(settingsDialog)
    
    }
}
