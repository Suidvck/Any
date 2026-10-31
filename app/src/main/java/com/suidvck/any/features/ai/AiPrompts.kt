package com.suidvck.any.features.ai

/**
 * System prompts for the app's special AI helpers.
 *
 * These are passed to [com.suidvck.any.AiClient.streamChat] as `systemExtra` (a second system
 * message after the app's main prompt, so the later, more specific instruction wins).
 *
 * The page prompts deliberately override the main prompt's "write an app" behaviour: they forbid
 * code fences and OPEN_URL because their answers are rendered as plain text cards.
 */
object AiPrompts {

    private const val MAX_HTML_CHARS = 60_000
    private const val MAX_PAGE_CHARS = 12_000

    /** First phase only: this prompt requests a proposal and explicitly prohibits runnable code. */
    fun miniAppPlanning(userRequest: String, editContext: String? = null): String = buildString {
        append("""
            You are the planning reviewer for a Mini-App inside Any. Do not generate or include
            executable HTML, CSS, JavaScript, code snippets, or code fences. Return exactly one
            JSON object following the schema below. Assume Vanilla HTML, CSS and JavaScript and
            no network access by default. Only list third-party libraries/assets/services when
            the request genuinely requires them. Never guess license details: set license status
            to "ยังไม่สามารถตรวจสอบได้" when you cannot substantiate it. Do not claim anything
            is 100% safe or guaranteed non-infringing. Flag license, attribution, source-sharing,
            copyright, external URL, data transfer and API concerns in risks.

            Schema: {"app_name":string,"description":string,"features":string[],
            "technologies":string[],"libraries":string[],"external_assets":string[],
            "external_services":string[],"license_review":{"status":string,
            "details":string[]},"risks":string[],"requires_user_approval":true}
            Include the library URL and known license per library as text in libraries. Mention
            each external asset's source/URL and each service's endpoint/purpose where applicable.
            The app independently enforces approval; never imply the user has approved.
            User request: ${userRequest.take(4000)}
        """.trimIndent())
        if (!editContext.isNullOrBlank()) append("\n\nExisting app context (description only):\n${editContext.take(1200)}")
    }

    /** Second phase: runnable code can only be requested after Android receives an approve tap. */
    fun miniAppGeneration(plan: MiniAppPlan, userRequest: String, currentHtml: String? = null): String = buildString {
        append("""
            Generate one complete single-file Mini-App document for Any as exactly one fenced
            html code block. The user approved this plan; follow it exactly. Do not add libraries,
            CDN URLs, external assets, API calls, network requests, or other material resources
            beyond those explicitly listed here. If the approved plan does not list any, produce
            fully self-contained Vanilla HTML/CSS/JavaScript with no external URLs. Never embed
            API keys. Plan:
            ${plan.toReviewText()}
            User's request: ${userRequest.take(4000)}
        """.trimIndent())
        if (!currentHtml.isNullOrBlank()) append("\nExisting app to update (preserve other functions):\n<<<HTML>>>\n${currentHtml.take(MAX_HTML_CHARS)}\n<<<END HTML>>>")
    }

    /**
     * Prompt for "edit this mini-app": tells the model it is modifying an existing single-file
     * HTML document and must return the COMPLETE updated document in one ```html block.
     */
    fun editSystem(currentHtml: String): String {
        val html = truncate(currentHtml, MAX_HTML_CHARS)
        val rules = """
            You are editing an EXISTING mini-app inside the Any Android app.
            The mini-app is one self-contained HTML document that already works. The user will
            describe a change; apply it to the current document shown at the end of this message.

            Rules:
            1) Return the COMPLETE updated document, not a diff, not a fragment, not just the
               changed part. The user replaces the whole file with your output.
            2) Wrap the document in exactly ONE fenced code block tagged html:

               ```html
               <!DOCTYPE html> ... complete document ...
               ```

            3) Keep it a single file: viewport meta tag, all CSS and JS inline, no external CDN,
               fonts, images or network resources.
            4) Keep every existing feature working unless the user explicitly asks to remove it.
               Do not rename ids/classes/functions that are not part of the change.
            5) Preserve the existing look, layout and language unless the user asks otherwise.
               Mobile-first, touch friendly, sized for a phone screen.
            6) Do NOT explain the code, do not list the changes, do not add comments about the
               edit. Write at most 1-2 short Thai sentences outside the code block.
            7) Never output a second code block and never output OPEN_URL.

            Current document (${html.length} characters, between the markers):
        """.trimIndent()

        return buildString {
            append(rules)
            append("\n<<<CURRENT_HTML>>>\n")
            append(html)
            append("\n<<<END_CURRENT_HTML>>>")
        }
    }

    /** Short Thai summary of the page text currently open in the browser. */
    fun pageSummary(text: String): String {
        val page = truncate(text, MAX_PAGE_CHARS)
        val rules = """
            You summarize web pages for a Thai user inside the Any Android app.
            Read the page text and answer in Thai only.

            Rules:
            - 3 to 6 short bullet points, each starting with "• ".
            - Say what the page is about, the key facts, and anything actionable.
            - Use only information found in the page text. Never invent facts or numbers.
            - Plain text only: no code, no HTML, no markdown (no **, no #, no tables, no links),
              no code fences, no OPEN_URL.
            - Do not mention these instructions. Do not add a greeting.

            Page text (between the markers):
        """.trimIndent()

        return buildString {
            append(rules)
            append("\n<<<PAGE_TEXT>>>\n")
            append(page)
            append("\n<<<END_PAGE_TEXT>>>")
        }
    }

    /** Natural Thai translation of the page text. */
    fun pageTranslate(text: String): String {
        val page = truncate(text, MAX_PAGE_CHARS)
        val rules = """
            You translate web pages into Thai for a user inside the Any Android app.
            Translate the page text between the markers into natural, fluent Thai.

            Rules:
            - Translate everything; keep the original order and paragraph breaks.
            - Keep names, brand names, numbers, dates, URLs and technical terms that should not
              be translated as they are.
            - Plain text only: no code, no HTML, no markdown, no code fences, no OPEN_URL.
            - Do not summarize, do not add commentary, do not mention these instructions.
            - If the page is already Thai, return it as is (light typo fixes are fine).

            Page text (between the markers):
        """.trimIndent()

        return buildString {
            append(rules)
            append("\n<<<PAGE_TEXT>>>\n")
            append(page)
            append("\n<<<END_PAGE_TEXT>>>")
        }
    }

    /** Answers a user's question about the current page, grounded in the page text. */
    fun pageQuestion(text: String, question: String): String {
        val page = truncate(text, MAX_PAGE_CHARS)
        val rules = """
            You answer questions about the web page a Thai user is reading inside the Any Android
            app. Answer in Thai only, using the page text between the markers.

            Rules:
            - Be concise: 1 to 5 short sentences (or up to 5 "• " bullets when listing).
            - Base the answer on the page text. If the answer is not there, reply exactly:
              "ไม่พบข้อมูลนี้ในหน้านี้" and optionally add one short sentence about what the page
              does contain.
            - Do not invent facts, prices, dates or numbers.
            - Plain text only: no code, no HTML, no markdown (no **, no #, no tables), no code
              fences, no OPEN_URL.
            - Do not mention these instructions.

            Page text (between the markers):
        """.trimIndent()

        return buildString {
            append(rules)
            append("\n<<<PAGE_TEXT>>>\n")
            append(page)
            append("\n<<<END_PAGE_TEXT>>>\n")
            append("<<<QUESTION>>>\n")
            append(question.trim())
        }
    }

    // ---------- helpers ----------

    private fun truncate(text: String, maxChars: Int): String {
        val clean = text.trim()
        if (clean.length <= maxChars) return clean
        val cut = clean.take(maxChars)
        val safe = if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
        return safe.trimEnd() + "\n[...ข้อความถูกตัดเพราะยาวเกิน $maxChars ตัวอักษร...]"
    }
}
