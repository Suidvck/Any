package com.suidvck.any.features.ai

import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.suidvck.any.AiClient
import com.suidvck.any.R

/** Owns the plan → approval → generation flow. The host only handles chat state and app display. */
class MiniAppReviewFlow(
    private val activity: AppCompatActivity,
    private val extractHtml: (String) -> String?,
    private val onMessage: (String) -> Unit,
    private val onBusyChanged: (Boolean) -> Unit,
    private val onGenerated: (html: String, editingId: String?) -> Unit
) {
    private val approvalGate = MiniAppApprovalGate()

    fun start(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        image: String?,
        editingId: String?,
        oldHtml: String?,
        onProgress: (String) -> Unit,
        onFinished: () -> Unit,
        onError: (String) -> Unit
    ) {
        AiClient.streamChat(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            systemExtra = AiPrompts.miniAppPlanning(
                prompt,
                oldHtml?.let { "Existing Mini-App HTML (${it.length} characters)." }
            ),
            history = listOf(AiMessage("user", prompt, image)),
            webSearch = false,
            onDelta = { onProgress("กำลังวิเคราะห์แผนและความเสี่ยง…") },
            onDone = { response ->
                onFinished()
                val plan = MiniAppPlan.parse(response)
                if (plan == null) {
                    onMessage("อ่านแผนจาก AI ไม่ได้ จึงยังไม่ได้เริ่มสร้างแอพ กรุณาลองใหม่")
                } else {
                    showPlan(plan, prompt, editingId, oldHtml, baseUrl, apiKey, model)
                }
            },
            onError = { error -> onError(error.message ?: "unknown error") }
        )
    }

    private fun showPlan(
        plan: MiniAppPlan,
        prompt: String,
        editingId: String?,
        oldHtml: String?,
        baseUrl: String,
        apiKey: String,
        model: String,
        onApprove: (ApprovedMiniAppPlan) -> Unit = { grant ->
            generate(grant, prompt, editingId, oldHtml, baseUrl, apiKey, model)
        }
    ) {
        if (activity.isFinishing) return
        approvalGate.present(plan)
        val content = ScrollView(activity).apply {
            setPadding(dp(22), dp(14), dp(22), dp(8))
            addView(TextView(activity).apply {
                text = plan.toReviewText()
                textSize = 15f
                setTextColor(0xFFEAEAF0.toInt())
            })
        }
        val dialog = MaterialAlertDialogBuilder(activity, R.style.AnyDialogTheme)
            .setTitle("ตรวจสอบแผนก่อนสร้าง")
            .setView(content)
            .setPositiveButton("Approve & Generate", null)
            .setNeutralButton("Modify Plan", null)
            .setNegativeButton("Cancel") { _, _ ->
                approvalGate.cancel()
                onMessage("ยกเลิกการสร้างแล้ว ยังไม่มีการสร้างหรือบันทึก Mini-App")
            }
            .create()
        dialog.setOnCancelListener { approvalGate.cancel() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val grant = approvalGate.approve(plan) ?: return@setOnClickListener
                dialog.dismiss()
                onApprove(grant)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                approvalGate.cancel()
                val edit = EditText(activity).apply {
                    hint = "เช่น ใช้ Vanilla JS เท่านั้น และไม่เรียก API ภายนอก"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    minLines = 3
                    setTextColor(0xFFEAEAF0.toInt())
                }
                MaterialAlertDialogBuilder(activity, R.style.AnyDialogTheme)
                    .setTitle("ขอปรับแผน")
                    .setMessage("อธิบายการเปลี่ยนแปลงที่ต้องการ แผนใหม่จะต้องได้รับอนุมัติก่อนสร้าง")
                    .setView(edit)
                    .setPositiveButton("ส่งให้ปรับแผน") { _, _ ->
                        val change = edit.text.toString().trim()
                        if (change.isNotEmpty()) {
                            revisePlan(plan, prompt, change, editingId, oldHtml, baseUrl, apiKey, model)
                        }
                    }
                    .setNegativeButton("กลับ", null)
                    .show()
            }
        }
        dialog.show()
    }

    private fun revisePlan(
        oldPlan: MiniAppPlan,
        prompt: String,
        change: String,
        editingId: String?,
        oldHtml: String?,
        baseUrl: String,
        apiKey: String,
        model: String
    ) {
        onBusyChanged(true)
        var cancelled = false
        val progress = MaterialAlertDialogBuilder(activity, R.style.AnyDialogTheme)
            .setTitle("กำลังปรับแผน")
            .setMessage("ยังไม่มีการสร้างโค้ด")
            .setNegativeButton("Cancel") { _, _ ->
                cancelled = true
                onBusyChanged(false)
            }
            .create()
        progress.show()
        progress.setOnCancelListener {
            cancelled = true
            onBusyChanged(false)
        }
        AiClient.streamChat(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            systemExtra = AiPrompts.miniAppPlanning(
                "$prompt\n\nUser requested plan change: $change\n\nPrevious proposal:\n${oldPlan.toReviewText()}"
            ),
            history = listOf(AiMessage("user", "$prompt\n\nRevise the previous plan: $change")),
            webSearch = false,
            onDelta = {},
            onDone = { response ->
                progress.dismiss()
                onBusyChanged(false)
                if (cancelled || activity.isFinishing) return@streamChat
                val revised = MiniAppPlan.parse(response)
                if (revised == null) onMessage("อ่านแผนที่ปรับแล้วไม่ได้ จึงยังไม่ได้สร้างแอพ")
                else showPlan(revised, prompt, editingId, oldHtml, baseUrl, apiKey, model)
            },
            onError = { error ->
                progress.dismiss()
                onBusyChanged(false)
                if (!cancelled && !activity.isFinishing) {
                    onMessage(activity.getString(R.string.error_prefix) + (error.message ?: "unknown error"))
                }
            }
        )
    }

    private fun generate(
        grant: ApprovedMiniAppPlan,
        prompt: String,
        editingId: String?,
        oldHtml: String?,
        baseUrl: String,
        apiKey: String,
        model: String
    ) {
        val plan = approvalGate.consume(grant) ?: return
        onBusyChanged(true)
        var cancelled = false
        val progress = MaterialAlertDialogBuilder(activity, R.style.AnyDialogTheme)
            .setTitle("กำลังสร้าง Mini-App")
            .setMessage("ตรวจสอบการใช้ทรัพยากรตามแผนที่อนุมัติแล้ว")
            .setNegativeButton("Cancel") { _, _ ->
                cancelled = true
                onBusyChanged(false)
            }
            .create()
        progress.show()
        progress.setOnCancelListener {
            cancelled = true
            onBusyChanged(false)
        }
        AiClient.streamChat(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            systemExtra = AiPrompts.miniAppGeneration(plan, prompt, oldHtml),
            history = listOf(AiMessage("user", prompt)),
            webSearch = false,
            onDelta = {},
            onDone = { response ->
                progress.dismiss()
                onBusyChanged(false)
                if (cancelled || activity.isFinishing) return@streamChat
                val html = extractHtml(response)
                if (html.isNullOrBlank()) {
                    onMessage("AI ไม่ได้ส่ง HTML ตามแผน จึงไม่ได้บันทึกหรือเปิดแอพ")
                    return@streamChat
                }
                val extraOrigins = MiniAppPlanValidator.unapprovedOrigins(html, plan)
                if (extraOrigins.isNotEmpty()) {
                    val revised = plan.copy(
                        externalServices = plan.externalServices + extraOrigins.map { "พบในผลสร้าง: $it" },
                        risks = plan.risks + "โค้ดที่ได้มี origin ซึ่งไม่มีในแผนเดิม: " +
                            "${extraOrigins.joinToString()}. ต้องอนุมัติแผนใหม่"
                    )
                    onMessage("พบ URL ภายนอกที่ไม่ได้อยู่ในแผนเดิม โค้ดยังไม่ถูกบันทึกหรือแสดง")
                    showPlan(revised, prompt, editingId, oldHtml, baseUrl, apiKey, model) { newGrant ->
                        generate(newGrant, prompt, editingId, oldHtml, baseUrl, apiKey, model)
                    }
                } else {
                    onGenerated(html, editingId)
                }
            },
            onError = { error ->
                progress.dismiss()
                onBusyChanged(false)
                if (!cancelled && !activity.isFinishing) {
                    onMessage(activity.getString(R.string.error_prefix) + (error.message ?: "unknown error"))
                }
            }
        )
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    companion object {
        fun shouldReviewRequest(prompt: String, editing: Boolean): Boolean {
            if (editing) return true
            val normalized = prompt.lowercase(java.util.Locale.ROOT)
            val action = listOf(
                "สร้าง", "ทำ", "เขียน", "อยากได้", "ต้องการ", "ขอ", "ช่วย", "build", "create",
                "make", "generate", "develop", "code", "design", "i want", "i need", "please"
            ).any(normalized::contains)
            val target = listOf(
                "แอพ", "แอป", "app", "application", "mini-app", "mini app", "web app", "website",
                "เว็บแอพ", "เกม", "game", "tool", "calculator", "เครื่องคิดเลข", "dashboard",
                "todo", "ตารางงาน", "note", "notes", "timer", "stopwatch", "chatbot", "planner",
                "tracker", "form", "assistant"
            ).any(normalized::contains)
            return action && target
        }
    }
}
