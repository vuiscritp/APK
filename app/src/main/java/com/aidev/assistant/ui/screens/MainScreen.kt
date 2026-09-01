package com.aidev.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aidev.assistant.data.AiClient
import com.aidev.assistant.data.AvailableModels
import com.aidev.assistant.data.ChatMessage
import com.aidev.assistant.data.ChatSession
import com.aidev.assistant.data.FirebaseRepository
import com.aidev.assistant.ui.components.MessageBubble
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val repo = remember { FirebaseRepository() }
    val scope = rememberCoroutineScope()

    var sessionId by remember { mutableStateOf<String?>(null) }
    var sessions by remember { mutableStateOf<List<ChatSession>>(emptyList()) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(AvailableModels.list.first()) }
    var isLoading by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val listState = rememberLazyListState()

    // Resume the most recently updated session if one exists, otherwise start a new one
    LaunchedEffect(Unit) {
        val existing = repo.observeSessions().first()
        sessionId = existing.firstOrNull()?.id ?: repo.createSession("AI Dev Session")
    }

    // Keep the drawer's session list in sync
    LaunchedEffect(Unit) {
        repo.observeSessions().collect { sessions = it }
    }

    // Observe messages
    LaunchedEffect(sessionId) {
        sessionId?.let { id ->
            repo.observeMessages(id).collect { messages = it }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Chats",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()
                if (sessions.isEmpty()) {
                    Text(
                        "No chats yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                sessions.forEach { session ->
                    NavigationDrawerItem(
                        label = { Text(session.title) },
                        selected = session.id == sessionId,
                        onClick = {
                            sessionId = session.id
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "AI Dev Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            selectedModel.name + " · " + selectedModel.provider,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { showModelPicker = true }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Models")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            sessionId = repo.createSession("New Chat")
                            messages = emptyList()
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                isLoading = isLoading,
                onSend = {
                    if (input.isBlank() || sessionId == null) return@ChatInputBar
                    val userMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = "user",
                        content = input.trim(),
                        model = selectedModel.id
                    )
                    scope.launch {
                        repo.saveMessage(sessionId!!, userMsg)
                        input = ""
                        isLoading = true
                        try {
                            val replyText = AiClient.generateReply(selectedModel, userMsg.content)
                            val aiMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                role = "assistant",
                                content = replyText,
                                model = selectedModel.id
                            )
                            repo.saveMessage(sessionId!!, aiMsg)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                onAttach = { /* file / image / video picker */ },
                onMic = { /* voice input */ }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (messages.isEmpty() && !isLoading) {
                EmptyState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg)
                    }
                    if (isLoading) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Thinking…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    } // end ModalNavigationDrawer content

    if (showModelPicker) {
        ModalBottomSheet(onDismissRequest = { showModelPicker = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Model", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                AvailableModels.list.forEach { model ->
                    ListItem(
                        headlineContent = { Text(model.name) },
                        supportingContent = { Text("${model.provider} · ${model.description}") },
                        leadingContent = {
                            Icon(Icons.Default.Memory, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedModel = model
                                showModelPicker = false
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = if (model.id == selectedModel.id)
                                MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF6366F1), Color(0xFF22D3EE))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Code,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "AI Dev Assistant",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask about code, debug errors, design architecture\nor search the latest docs.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(28.dp))
        SuggestionChips()
    }
}

@Composable
private fun SuggestionChips() {
    val suggestions = listOf(
        "Explain this Kotlin coroutine",
        "Fix NullPointerException",
        "Design a clean architecture",
        "Write unit tests for…"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { text ->
                    SuggestionChip(
                        onClick = { },
                        label = { Text(text, fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onMic: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onAttach) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach")
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask me anything about code…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            IconButton(onClick = onMic) {
                Icon(Icons.Default.Mic, contentDescription = "Voice")
            }
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank() && !isLoading,
                shape = CircleShape
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

