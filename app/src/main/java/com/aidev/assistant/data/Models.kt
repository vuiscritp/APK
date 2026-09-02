package com.aidev.assistant.data

data class ChatMessage(
    val id: String = "",
    val role: String = "user",          // user | assistant | system
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val model: String = "",
    val attachments: List<String> = emptyList() // urls or local paths
)

data class ChatSession(
    val id: String = "",
    val title: String = "New Chat",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
)

data class AIModel(
    val id: String,
    val name: String,
    val provider: String,
    val description: String,
    val apiModelId: String,       // exact model identifier sent to the provider's API
    val available: Boolean = true // kept for compatibility; models from ModelRepository are already live-verified
)

object AvailableModels {
    val list = listOf(
        AIModel("groq-llama", "GPT-OSS 120B", "Groq", "Fast & powerful", "openai/gpt-oss-120b"),
        AIModel("gemini-flash", "Gemini 2.0 Flash", "Google", "Multimodal", "gemini-2.0-flash"),
        AIModel("openrouter-auto", "OpenRouter Auto", "OpenRouter", "Best available", "openrouter/auto"),
        AIModel("cf-llama", "Llama 3.1 8B", "Cloudflare", "Edge inference", "@cf/meta/llama-3.1-8b-instruct")
    )
}
