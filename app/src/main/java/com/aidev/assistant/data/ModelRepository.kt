package com.aidev.assistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Scans the FULL live model catalog of every provider on app start, instead of
 * just checking whether a fixed hand-picked list still exists. Because the picker
 * is built directly from what each provider currently reports, a decommissioned
 * model (like Groq retiring llama-3.3-70b-versatile) simply never appears in the
 * list in the first place.
 *
 * Fails open per-provider: if a provider's catalog can't be fetched (offline, no
 * key yet, rate limited), that provider is silently skipped for this run rather
 * than blocking the others. If EVERY provider fetch fails, the static
 * AvailableModels.list is used as an offline fallback.
 */
object ModelRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAll(): List<AIModel> = withContext(Dispatchers.IO) {
        val results = coroutineScope {
            val groq = async { safeCall { fetchGroq() } }
            val gemini = async { safeCall { fetchGemini() } }
            val openRouter = async { safeCall { fetchOpenRouter() } }
            val cloudflare = async { safeCall { fetchCloudflare() } }
            listOf(groq, gemini, openRouter, cloudflare).awaitAll().flatten()
        }
        results.ifEmpty { AvailableModels.list }
    }

    private fun safeCall(block: () -> List<AIModel>): List<AIModel> =
        try { block() } catch (e: Exception) { emptyList() }

    /** Turns "openai/gpt-oss-120b" or "@cf/meta/llama-3.1-8b-instruct" into "GPT OSS 120B". */
    private fun prettify(rawId: String): String {
        val last = rawId.substringAfterLast('/')
        return last.split('-', '_')
            .joinToString(" ") { part ->
                if (part.isNotEmpty()) part.replaceFirstChar { it.uppercase(Locale.getDefault()) } else part
            }
    }

    // ---- Groq: GET /openai/v1/models ----
    private fun fetchGroq(): List<AIModel> {
        val key = SecretVault.randomKey("groq") ?: return emptyList()
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/models")
            .addHeader("Authorization", "Bearer $key")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val data = JSONObject(response.body?.string().orEmpty()).getJSONArray("data")
            val excluded = listOf("whisper", "tts", "guard", "playai") // audio/moderation, not chat
            val out = mutableListOf<AIModel>()
            for (i in 0 until data.length()) {
                val id = data.getJSONObject(i).getString("id")
                if (excluded.any { id.contains(it, ignoreCase = true) }) continue
                out += AIModel(
                    id = "groq:$id",
                    name = prettify(id),
                    provider = "Groq",
                    description = id,
                    apiModelId = id
                )
            }
            return out
        }
    }

    // ---- Gemini: GET /v1beta/models ----
    private fun fetchGemini(): List<AIModel> {
        val key = SecretVault.randomKey("gemini") ?: return emptyList()
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val models = JSONObject(response.body?.string().orEmpty()).getJSONArray("models")
            val out = mutableListOf<AIModel>()
            for (i in 0 until models.length()) {
                val obj = models.getJSONObject(i)
                val methods = obj.optJSONArray("supportedGenerationMethods")
                var supportsChat = false
                if (methods != null) {
                    for (j in 0 until methods.length()) {
                        if (methods.getString(j) == "generateContent") { supportsChat = true; break }
                    }
                }
                if (!supportsChat) continue
                val apiId = obj.getString("name").removePrefix("models/")
                out += AIModel(
                    id = "gemini:$apiId",
                    name = obj.optString("displayName", prettify(apiId)),
                    provider = "Google",
                    description = obj.optString("description", "").take(60),
                    apiModelId = apiId
                )
            }
            return out
        }
    }

    // ---- OpenRouter: GET /api/v1/models (public catalog, no key required to list) ----
    private fun fetchOpenRouter(): List<AIModel> {
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val data = JSONObject(response.body?.string().orEmpty()).getJSONArray("data")
            val out = mutableListOf<AIModel>()
            for (i in 0 until data.length()) {
                val obj = data.getJSONObject(i)
                val id = obj.getString("id")
                out += AIModel(
                    id = "openrouter:$id",
                    name = obj.optString("name", prettify(id)),
                    provider = "OpenRouter",
                    description = id,
                    apiModelId = id
                )
            }
            return out
        }
    }

    // ---- Cloudflare Workers AI model catalog (key stored as "token:accountId") ----
    private fun fetchCloudflare(): List<AIModel> {
        val raw = SecretVault.randomKey("cloudflare") ?: return emptyList()
        val parts = raw.split(":", limit = 2)
        if (parts.size != 2) return emptyList()
        val (token, accountId) = parts
        val request = Request.Builder()
            .url("https://api.cloudflare.com/client/v4/accounts/$accountId/ai/models/search?task=Text+Generation&per_page=100")
            .addHeader("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val json = JSONObject(response.body?.string().orEmpty())
            if (!json.optBoolean("success", false)) return emptyList()
            val result = json.optJSONArray("result") ?: return emptyList()
            val out = mutableListOf<AIModel>()
            for (i in 0 until result.length()) {
                val obj = result.getJSONObject(i)
                val apiId = obj.getString("name") // e.g. "@cf/meta/llama-3.1-8b-instruct"
                out += AIModel(
                    id = "cloudflare:$apiId",
                    name = prettify(apiId),
                    provider = "Cloudflare",
                    description = "Edge inference",
                    apiModelId = apiId
                )
            }
            return out
        }
    }
}
