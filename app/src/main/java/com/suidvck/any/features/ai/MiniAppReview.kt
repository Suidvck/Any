package com.suidvck.any.features.ai

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A model supplied proposal. Approval itself is deliberately held by the Android UI. */
data class MiniAppPlan(
    val appName: String,
    val description: String,
    val features: List<String>,
    val technologies: List<String>,
    val libraries: List<String>,
    val externalAssets: List<String>,
    val externalServices: List<String>,
    val licenseStatus: String,
    val licenseDetails: List<String>,
    val risks: List<String>
) {
    fun toReviewText(): String = buildString {
        appendLine("${appName.ifBlank { "Mini-App" }} — $description")
        section("Features", features)
        section("Technologies", technologies)
        section("Libraries and licenses", libraries + "License status: $licenseStatus" + licenseDetails)
        section("External assets", externalAssets)
        section("External services", externalServices)
        section("Risks", risks)
        append("License data is AI supplied and not independently verified by the app.")
    }

    private fun StringBuilder.section(title: String, lines: List<String>) {
        appendLine("\n$title:")
        if (lines.isEmpty()) appendLine("• None declared")
        else lines.forEach { appendLine("• $it") }
    }

    companion object {
        fun parse(response: String): MiniAppPlan? {
            val json = response.trim().removePrefix("```").removeSuffix("```").trim()
                .let { if (it.startsWith("json", true)) it.drop(4).trim() else it }
            val start = json.indexOf('{')
            val end = json.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return runCatching {
                val root = JSONObject(json.substring(start, end + 1))
                require(root.optBoolean("requires_user_approval", false))
                MiniAppPlan(
                    appName = root.optString("app_name").trim().take(100),
                    description = root.optString("description").trim().take(1200),
                    features = strings(root.optJSONArray("features")),
                    technologies = strings(root.optJSONArray("technologies")),
                    libraries = strings(root.optJSONArray("libraries")),
                    externalAssets = strings(root.optJSONArray("external_assets")),
                    externalServices = strings(root.optJSONArray("external_services")),
                    licenseStatus = root.optJSONObject("license_review")?.optString("status")
                        ?.takeIf { it.isNotBlank() } ?: "ยังไม่สามารถตรวจสอบได้",
                    licenseDetails = strings(root.optJSONObject("license_review")?.optJSONArray("details")),
                    risks = strings(root.optJSONArray("risks"))
                ).also { require(it.appName.isNotBlank() && it.description.isNotBlank()) }
            }.getOrNull()
        }

        private fun strings(array: JSONArray?): List<String> = buildList {
            if (array == null) return@buildList
            for (i in 0 until array.length()) {
                val value = when (val item = array.opt(i)) {
                    is JSONObject -> listOf("name", "url", "license", "source", "description")
                        .map { item.optString(it).trim() }.filter { it.isNotBlank() }.joinToString(" — ")
                    else -> item?.toString()?.trim().orEmpty()
                }
                if (value.isNotBlank()) add(value.take(800))
            }
        }
    }
}

/** App-owned, one-use capability issued only by the explicit plan approval button. */
class ApprovedMiniAppPlan internal constructor(
    val plan: MiniAppPlan,
    internal val nonce: String
)

class MiniAppApprovalGate {
    private var pending: MiniAppPlan? = null
    private var outstanding: ApprovedMiniAppPlan? = null

    @Synchronized fun present(plan: MiniAppPlan) {
        pending = plan
        outstanding = null
    }

    @Synchronized fun approve(plan: MiniAppPlan): ApprovedMiniAppPlan? {
        if (pending != plan) return null
        pending = null
        return ApprovedMiniAppPlan(plan, UUID.randomUUID().toString()).also { outstanding = it }
    }

    @Synchronized fun consume(grant: ApprovedMiniAppPlan): MiniAppPlan? {
        if (outstanding?.nonce != grant.nonce || outstanding?.plan != grant.plan) return null
        outstanding = null
        return grant.plan
    }

    @Synchronized fun cancel() {
        pending = null
        outstanding = null
    }
}

/** Fail closed when generated HTML adds network origins not named in the approved proposal. */
object MiniAppPlanValidator {
    private val urlRegex = Regex("(?i)(?:(?:https?|wss?)://|//)[^\\s\\\"'<>\\)]+")
    private val externalTagRegex = Regex("(?is)<(?:script|link|img|iframe|audio|video|source)[^>]+(?:src|href)\\s*=\\s*['\\\"]((?:(?:https?|wss?)://|//)[^'\\\"]+)")

    fun unapprovedOrigins(html: String, plan: MiniAppPlan): List<String> {
        val exactUrls = urlRegex.findAll((plan.libraries + plan.externalAssets + plan.externalServices).joinToString(" "))
            .mapNotNull { canonicalUrl(it.value) }.toSet()
        // A declared API service may have multiple paths; library and asset URLs must match the
        // specific reviewed resource so another package on the same CDN is still flagged.
        val serviceHosts = urlRegex.findAll(plan.externalServices.joinToString(" "))
            .mapNotNull { host(it.value) }.toSet()
        val found = (urlRegex.findAll(html).map { it.value } +
            externalTagRegex.findAll(html).map { it.groupValues[1] })
            .distinct()
        return found.filter { url ->
            val urlHost = host(url)
            urlHost != null && urlHost !in serviceHosts && canonicalUrl(url) !in exactUrls
        }.mapNotNull(::host).distinct().sorted().toList()
    }

    private fun host(raw: String): String? = runCatching {
        java.net.URI(normalize(raw)).host?.lowercase()
    }.getOrNull()

    private fun canonicalUrl(raw: String): String? = runCatching {
        val uri = java.net.URI(normalize(raw))
        val host = uri.host?.lowercase() ?: return null
        val port = if (uri.port == -1 || (uri.scheme == "https" && uri.port == 443) ||
            (uri.scheme == "http" && uri.port == 80)) "" else ":${uri.port}"
        "${uri.scheme.lowercase()}://$host$port${uri.rawPath.orEmpty().trimEnd('/')}${uri.rawQuery?.let { "?$it" }.orEmpty()}"
    }.getOrNull()

    private fun normalize(raw: String): String {
        val trimmed = raw.trimEnd('.', ',', ';', ')', ']', '}', '\\')
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("ws://", true) -> "http://${trimmed.substringAfter("://")}"
            trimmed.startsWith("wss://", true) -> "https://${trimmed.substringAfter("://")}"
            else -> trimmed
        }
    }
}
