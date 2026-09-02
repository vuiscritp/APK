package com.aidev.assistant.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aidev.assistant.data.AiClient
import com.aidev.assistant.data.AvailableModels
import com.aidev.assistant.data.ChatMessage
import com.aidev.assistant.data.ChatSession
import com.aidev.assistant.data.FirebaseRepository
import com.aidev.assistant.data.ModelChecker
import com.aidev.assistant.data.AIModel
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
    var models by remember { mutableStateOf(AvailableModels.list) }
    var selectedModel by remember { mutableStateOf(AvailableModels.list.first()) }
    var isLoading by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

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

    // Verify each model against the provider's live catalog once, on first open.
    // Catches a decommissioned model (e.g. Groq retiring llama-3.3-70b-versatile)
    // before the user hits a 404 mid-chat.
    LaunchedEffect(Unit) {
        val checked = ModelChecker.verify(AvailableModels.list)
        models = checked
        val currentStillValid = checked.firstOrNull { it.id == selectedModel.id }?.available ?: true
        if (!currentStillValid) {
            val fallback = checked.firstOrNull { it.available } ?: checked.first()
            selectedModel = fallback
            scope.launch {
                snackbarHostState.showSnackbar("${selectedModel.name} không khả dụng, đã chuyển sang ${fallback.name}")
            }
        }
    }

    // Auto-scroll to the newest message
    LaunchedEffect(messages.size, isLoading) {
        val target = messages.size - 1 + if (isLoading) 1 else 0
        if (target >= 0) listState.animateScrollToItem(target)
    }

    val isAtBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = listState.layoutInfo.totalItemsCount
            last == null || last.index >= totalItems - 1
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showModelPicker = true }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            "AI Dev Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedModel.available) Color(0xFF22C55E) else Color(0xFFEF4444))
                            )
                            Text(
                                selectedModel.name,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (messages.isNotEmpty()) showClearConfirm = true }
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear chat")
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
                    containerColor = MaterialTheme.colorScheme.background
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
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg, onRetry = { showModelPicker = true })
                    }
                    if (isLoading) {
                        item(key = "typing") { TypingIndicatorRow() }
                    }
                }

                AnimatedVisibility(
                    visible = !isAtBottom,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch { listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0)) }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to bottom")
                    }
                }
            }
        }
    }
    } // end ModalNavigationDrawer content

    if (showModelPicker) {
        ModelPickerSheet(
            models = models,
            selectedModel = selectedModel,
            onSelect = {
                selectedModel = it
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Xoá cuộc trò chuyện?") },
            text = { Text("Toàn bộ tin nhắn trong đoạn chat này sẽ bị xoá. Không thể hoàn tác.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        sessionId?.let { repo.clearMessages(it) }
                    }
                }) { Text("Xoá", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Huỷ") }
            }
        )
    }
}

@Composable
private fun ModelPickerSheet(
    models: List<AIModel>,
    selectedModel: AIModel,
    onSelect: (AIModel) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Chọn Model", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            models.forEach { model ->
                ListItem(
                    headlineContent = { Text(model.name) },
                    supportingContent = {
                        Text(
                            if (model.available) "${model.provider} · ${model.description}"
                            else "${model.provider} · Không khả dụng"
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            tint = if (model.available) LocalContentColor.current
                            else MaterialTheme.colorScheme.error
                        )
                    },
                    trailingContent = {
                        if (!model.available) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = "Unavailable",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = model.available) { onSelect(model) },
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

@Composable
private fun TypingIndicatorRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF22D3EE)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BouncingDot(delayMillis = 0)
                    BouncingDot(delayMillis = 150)
                    BouncingDot(delayMillis = 300)
                }
            }
        }
    }
}

@Composable
private fun BouncingDot(delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "typing")
    val scale by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delayMillis),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = scale))
    )
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val canSend = value.isNotBlank() && !isLoading

    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.outline

    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, borderColor, RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onAttach, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = "Attach")
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        "Hỏi AI Dev Assistant bất cứ điều gì…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 15.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    interactionSource = interactionSource,
                    maxLines = 5
                )
            }

            IconButton(onClick = onMic, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Mic, contentDescription = "Voice")
            }

            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (canSend) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                    contentColor = if (canSend) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
