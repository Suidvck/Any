package com.suidvck.any.features.ai

/**
 * One conversation turn handed to [com.suidvck.any.AiClient.streamChat].
 *
 * @param role "user" or "assistant" (anything other than "assistant" is sent as "user").
 * @param text the visible message text.
 * @param imageDataUrl optional `data:image/jpeg;base64,...` URL attached to a user turn; when
 *   present the request uses the OpenAI multimodal `content` array (`text` + `image_url`).
 */
data class AiMessage(
    val role: String,
    val text: String,
    val imageDataUrl: String? = null
)
