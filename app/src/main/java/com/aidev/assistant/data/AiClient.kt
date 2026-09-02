package com.aidev.assistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Calls the real provider APIs (Groq / Gemini / OpenRouter / Cloudflare Workers AI)
 * using keys from SecretVault.
 */
object AiClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun generateReply(model: AIModel, prompt: String): String = withContext(Dispatchers.IO) {
        try {
            when (model.provider) {
                "Groq" -> openAiCompatible(
                    service = "groq",
                    url = "https://api.groq.com/openai/v1/chat/completions",
                    apiModel = model.apiModelId,
                    prompt = prompt
                )
                "Google" -> gemini(model.apiModelId, prompt)
                "OpenRouter" -> openAiCompatible(
                    service = "openrouter",
                    url = "https://openrouter.ai/api/v1/chat/completions",
                    apiModel = model.apiModelId,
                    prompt = prompt
                )
                "Cloudflare" -> cloudflare(model.apiModelId, prompt)
                else -> "⚠️ Unknown provider: ${model.provider}"
            }
        } catch (e: Exception) {
            "⚠️ ${model.name} request failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    // ---- Groq & OpenRouter share the OpenAI-compatible chat/completions shape ----
    private fun openAiCompatible(service: String, url: String, apiModel: String, prompt: String): String {
        val apiKey = SecretVault.randomKey(service)
            ?: return "⚠️ No $service API key configured."

        val body = JSONObject().apply {
            put("model", apiModel)
            put("messages", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
            ))
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return "⚠️ $service error ${response.code}: ${text.take(300)}"
            }
            val json = JSONObject(text)
            return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }

    // ---- Gemini (Google Generative Language API) ----
    private fun gemini(apiModelId: String, prompt: String): String {
        val apiKey = SecretVault.randomKey("gemini")
            ?: return "⚠️ No Gemini API key configured."

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$apiModelId:generateContent?key=$apiKey"

        val body = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().apply {
                    put("parts", JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
                }
            ))
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return "⚠️ Gemini error ${response.code}: ${text.take(300)}"
            }
            val json = JSONObject(text)
            return json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        }
    }

    // ---- Cloudflare Workers AI (key format stored as "token:accountId") ----
    private fun cloudflare(apiModelId: String, prompt: String): String {
        val raw = SecretVault.randomKey("cloudflare")
            ?: return "⚠️ No Cloudflare API key configured."
        val parts = raw.split(":", limit = 2)
        if (parts.size != 2) return "⚠️ Malformed Cloudflare key."
        val (token, accountId) = parts

        val url = "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/$apiModelId"

        val body = JSONObject().apply {
            put("messages", JSONArray().put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                }
            ))
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return "⚠️ Cloudflare error ${response.code}: ${text.take(300)}"
            }
            val json = JSONObject(text)
            return json.getJSONObject("result").getString("response").trim()
        }
    }
}
