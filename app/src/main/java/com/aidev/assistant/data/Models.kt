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
    val description: String
)

object AvailableModels {
    val list = listOf(
        AIModel("groq-llama", "Llama 3.3 70B", "Groq", "Fast & powerful"),
        AIModel("gemini-flash", "Gemini 2.0 Flash", "Google", "Multimodal"),
        AIModel("openrouter-auto", "OpenRouter Auto", "OpenRouter", "Best available"),
        AIModel("cf-llama", "Llama 3.1 8B", "Cloudflare", "Edge inference")
    )
}
