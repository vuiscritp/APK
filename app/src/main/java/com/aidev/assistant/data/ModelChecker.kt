package com.aidev.assistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Runs once when the app opens: asks each provider "does this model id still exist?"
 * so a decommissioned model (like Groq retiring llama-3.3-70b-versatile) is caught
 * and hidden/switched-away-from *before* the user hits a 404 mid-chat.
 *
 * Fails open: if a check can't complete (offline, rate-limited, no key yet), the model
 * is left as available=true — we only mark a model unavailable when the provider
 * explicitly confirms the id is not in its catalog.
 */
object ModelChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun verify(models: List<AIModel>): List<AIModel> = withContext(Dispatchers.IO) {
        models.map { model ->
            val ok = try {
                when (model.provider) {
                    "Groq" -> checkGroq(model.apiModelId)
                    "Google" -> checkGemini(model.apiModelId)
                    // OpenRouter's "auto" router and Cloudflare's model catalog aren't
                    // cheaply verifiable the same way; assume available.
                    else -> true
                }
            } catch (e: Exception) {
                true
            }
            model.copy(available = ok)
        }
    }

    private fun checkGroq(apiModelId: String): Boolean {
        val key = SecretVault.randomKey("groq") ?: return true
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/models")
            .addHeader("Authorization", "Bearer $key")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return true
            val data = JSONObject(response.body?.string().orEmpty()).getJSONArray("data")
            for (i in 0 until data.length()) {
                if (data.getJSONObject(i).getString("id") == apiModelId) return true
            }
            return false
        }
    }

    private fun checkGemini(apiModelId: String): Boolean {
        val key = SecretVault.randomKey("gemini") ?: return true
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return true
            val models = JSONObject(response.body?.string().orEmpty()).getJSONArray("models")
            val target = "models/$apiModelId"
            for (i in 0 until models.length()) {
                if (models.getJSONObject(i).getString("name") == target) return true
            }
            return false
        }
    }
}
