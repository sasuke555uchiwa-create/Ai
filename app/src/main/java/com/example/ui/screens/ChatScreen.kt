package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ActiveToolType
import com.example.data.model.AppLanguage
import com.example.data.model.AppStrings
import com.example.data.model.AvailableModels
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.WebSource
import com.example.ui.components.FormattedAiContent
import com.example.ui.components.PlusToolsBottomSheet
import com.example.ui.theme.SasukeAmberWarning
import com.example.ui.theme.SasukeCobaltBlue
import com.example.ui.theme.SasukeDarkCardBorder
import com.example.ui.theme.SasukeElectricViolet
import com.example.ui.theme.SasukeGreenSuccess
import com.example.ui.theme.SasukeLightViolet
import com.example.ui.theme.SasukeNeonCyan
import com.example.ui.theme.SasukeRedError
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    currentLanguage: AppLanguage,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var modelMenuExpanded by remember { mutableStateOf(false) }

    // Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val out = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                        val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        viewModel.setSelectedImage(base64)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Document Picker
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    var displayName = "document.txt"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            displayName = cursor.getString(nameIndex) ?: "document.txt"
                        }
                    }

                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val text = stream.bufferedReader().use { it.readText() }
                        viewModel.attachDocument(displayName, text.take(30000))
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to load document: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Auto-scroll on changes
    LaunchedEffect(uiState.messages.size, uiState.streamingText) {
        if (uiState.messages.isNotEmpty() || uiState.streamingText.isNotEmpty()) {
            val totalItems = uiState.messages.size + (if (uiState.isStreaming) 1 else 0)
            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("chat_screen")
    ) {
        // Chat Header Toolbar: Model selector & New Chat action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Model selector dropdown
            Box {
                val currentModel = AvailableModels.allModels.firstOrNull { it.id == uiState.selectedModelId }
                    ?: AvailableModels.GEMINI_3_5_FLASH

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { modelMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("model_selector_dropdown")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SasukeNeonCyan)
                        )
                        Text(
                            text = currentModel.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    AvailableModels.textChatModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = model.displayName,
                                        fontWeight = if (model.id == uiState.selectedModelId) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = "${model.speedRating} • ${model.contextWindow}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                viewModel.setModel(model.id)
                                modelMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Right header controls: Grounding toggle & New Chat
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.isSearchGroundingEnabled,
                    onClick = { viewModel.toggleSearchGrounding() },
                    label = {
                        Text(
                            text = if (uiState.isSearchGroundingEnabled) "Search ON" else "Search",
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (uiState.isSearchGroundingEnabled) SasukeNeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectedLabelColor = SasukeNeonCyan
                    )
                )

                IconButton(
                    onClick = { viewModel.startNewChat() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = AppStrings.get("new_chat", currentLanguage),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // API Key status warning if missing
        if (!uiState.isApiKeyConfigured) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = SasukeRedError
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.get("api_key_missing", currentLanguage),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Add GEMINI_API_KEY in AI Studio Secrets or enter a custom key in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    TextButton(onClick = onNavigateToSettings) {
                        Text("Settings", color = SasukeNeonCyan)
                    }
                }
            }
        }

        // Messages List Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (uiState.messages.isEmpty() && !uiState.isStreaming) {
                // Empty State with instant suggestions
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.sasuke_ai_icon_1791648464068),
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "SASUKE AI",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tap '+' to access image, video, search, coding & arena tools directly inside chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    val suggestions = listOf(
                        "Explain quantum computing in simple steps",
                        "Generate a cyberpunk anime ninja portrait",
                        "Write a high-performance LRU Cache in Kotlin",
                        "قارن بين نماذج الذكاء الاصطناعي في حل الرياضيات"
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(0.92f)
                    ) {
                        suggestions.forEach { prompt ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.sendMessage(prompt) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                        .testTag("messages_list"),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            onRegenerate = { viewModel.regenerateLast() },
                            onCheckVideoStatus = { opId ->
                                viewModel.checkVideoStatusInChat(message.id, opId)
                            },
                            onRateArena = { isA, star ->
                                viewModel.rateArenaInChat(message.id, isA, star)
                            },
                            onChooseWinner = { winner ->
                                viewModel.chooseArenaWinnerInChat(message.id, winner)
                            }
                        )
                    }

                    // Live Streaming bubble
                    if (uiState.isStreaming) {
                        item(key = "streaming_bubble") {
                            AssistantStreamingBubble(
                                text = uiState.streamingText,
                                sources = uiState.streamingSources
                            )
                        }
                    }

                    // Error item
                    if (uiState.errorMessage != null) {
                        item(key = "error_bubble") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = SasukeRedError
                                    )
                                    Text(
                                        text = uiState.errorMessage ?: "Unknown error",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.regenerateLast() }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Retry",
                                            tint = SasukeNeonCyan
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        // Active Tool Staging Chip & Attachments Bar above input
        if (uiState.activeTool != ActiveToolType.NONE || uiState.selectedImageBase64 != null || uiState.attachedFileName != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .testTag("active_tool_staging_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        when (uiState.activeTool) {
                            ActiveToolType.IMAGE_GEN -> {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SasukeNeonCyan, modifier = Modifier.size(16.dp))
                                Text("Generate Image (1:1)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SasukeNeonCyan)
                            }
                            ActiveToolType.VIDEO_GEN -> {
                                Icon(Icons.Default.Videocam, contentDescription = null, tint = SasukeAmberWarning, modifier = Modifier.size(16.dp))
                                Text("Generate Video (Veo 3.1)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SasukeAmberWarning)
                            }
                            ActiveToolType.WEB_SEARCH -> {
                                Icon(Icons.Default.Language, contentDescription = null, tint = SasukeGreenSuccess, modifier = Modifier.size(16.dp))
                                Text("Live Web Grounding", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SasukeGreenSuccess)
                            }
                            ActiveToolType.DOCUMENT -> {
                                Icon(Icons.Default.Description, contentDescription = null, tint = SasukeNeonCyan, modifier = Modifier.size(16.dp))
                                Text("Doc: ${uiState.attachedFileName ?: "Attached"}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SasukeNeonCyan)
                            }
                            ActiveToolType.MODEL_ARENA -> {
                                Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, tint = SasukeLightViolet, modifier = Modifier.size(16.dp))
                                Text("Compare: Flash vs Pro", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SasukeLightViolet)
                            }
                            ActiveToolType.CODING -> {
                                Icon(Icons.Default.Code, contentDescription = null, tint = SasukeNeonCyan, modifier = Modifier.size(16.dp))
                                Text("Coding Assistant Mode", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SasukeNeonCyan)
                            }
                            ActiveToolType.STUDY -> {
                                Icon(Icons.Default.Science, contentDescription = null, tint = SasukeGreenSuccess, modifier = Modifier.size(16.dp))
                                Text("Science & STEM Study Mode", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SasukeGreenSuccess)
                            }
                            ActiveToolType.VOICE -> {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SasukeLightViolet, modifier = Modifier.size(16.dp))
                                Text("Conversational Voice Mode", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SasukeLightViolet)
                            }
                            ActiveToolType.REASONING -> {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SasukeNeonCyan, modifier = Modifier.size(16.dp))
                                Text("Deep Reasoning (Pro 3.1)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SasukeNeonCyan)
                            }
                            else -> {
                                if (uiState.selectedImageBase64 != null) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SasukeNeonCyan, modifier = Modifier.size(16.dp))
                                    Text("Image Attached", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SasukeNeonCyan)
                                }
                            }
                        }
                    }

                    // Remove tool button
                    IconButton(
                        onClick = {
                            viewModel.clearActiveTool()
                            viewModel.setSelectedImage(null)
                            viewModel.clearAttachedDocument()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear Tool", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Bottom persistent input bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                .imePadding()
                .testTag("persistent_input_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Prominent "+" Button opening Plus Tools Bottom Sheet
                IconButton(
                    onClick = { viewModel.setPlusMenuOpen(true) },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(SasukeDarkCardBorder, MaterialTheme.colorScheme.surfaceVariant)
                            ),
                            shape = CircleShape
                        )
                        .testTag("plus_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Sasuke Tools Menu",
                        tint = SasukeNeonCyan,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Text field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        val placeholder = when (uiState.activeTool) {
                            ActiveToolType.IMAGE_GEN -> "Describe the image to generate..."
                            ActiveToolType.VIDEO_GEN -> "Describe the video scene for Veo..."
                            ActiveToolType.MODEL_ARENA -> "Enter prompt to compare Flash vs Pro..."
                            ActiveToolType.DOCUMENT -> "Ask about the uploaded document..."
                            ActiveToolType.CODING -> "Describe feature or paste code..."
                            ActiveToolType.STUDY -> "Enter science concept or ask for quiz..."
                            else -> AppStrings.get("type_message", currentLanguage)
                        }
                        Text(text = placeholder, fontSize = 13.sp)
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SasukeNeonCyan,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input"),
                    maxLines = 4
                )

                // Send button
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() || uiState.selectedImageBase64 != null || uiState.attachedFileContent != null) {
                            val textToSend = inputText
                            inputText = ""
                            viewModel.sendMessage(textToSend)
                        }
                    },
                    enabled = !uiState.isStreaming && (inputText.isNotBlank() || uiState.selectedImageBase64 != null || uiState.attachedFileContent != null),
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(SasukeLightViolet, SasukeCobaltBlue)
                            ),
                            shape = CircleShape
                        )
                        .testTag("send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    // Plus Tools Bottom Sheet
    if (uiState.isPlusMenuOpen) {
        PlusToolsBottomSheet(
            currentLanguage = currentLanguage,
            onSelectTool = { tool ->
                when (tool) {
                    ActiveToolType.ADD_IMAGE -> {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                        viewModel.setPlusMenuOpen(false)
                    }
                    ActiveToolType.DOCUMENT -> {
                        documentPickerLauncher.launch(
                            arrayOf("text/*", "application/json", "text/plain", "text/csv")
                        )
                        viewModel.setPlusMenuOpen(false)
                    }
                    else -> {
                        viewModel.setActiveTool(tool)
                    }
                }
            },
            onDismiss = { viewModel.setPlusMenuOpen(false) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onRegenerate: () -> Unit,
    onCheckVideoStatus: (String) -> Unit = {},
    onRateArena: (Boolean, Int) -> Unit = { _, _ -> },
    onChooseWinner: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var arenaTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    if (message.role == MessageRole.USER) {
        // User Message
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("user_message_${message.id}"),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp),
                color = SasukeElectricViolet,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Tool Badge on user message if present
                    if (!message.toolType.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.25f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "Tool: ${message.toolType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SasukeNeonCyan,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Attached Document chip
                    if (!message.attachedFileName.isNullOrEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                text = message.attachedFileName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Attached image
                    if (message.imageBase64 != null) {
                        val bitmap = remember(message.imageBase64) {
                            try {
                                val bytes = Base64.decode(message.imageBase64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .padding(bottom = 8.dp)
                            )
                        }
                    }

                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    } else {
        // Assistant Message
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("assistant_message_${message.id}"),
            horizontalArrangement = Arrangement.Start
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.96f)) {
                Surface(
                    shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.sasuke_ai_icon_1791648464068),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                )
                                Text(
                                    text = message.modelId ?: "Sasuke AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SasukeNeonCyan
                                )
                            }

                            if (message.latencyMs != null) {
                                Text(
                                    text = "${message.latencyMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // IN-CHAT WORKFLOW 1: Generated Image
                        if (!message.generatedImageBase64.isNullOrEmpty()) {
                            val bitmap = remember(message.generatedImageBase64) {
                                try {
                                    val bytes = Base64.decode(message.generatedImageBase64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                } catch (e: Exception) {
                                    null
                                }
                            }

                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Generated Artwork",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, SasukeDarkCardBorder, RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Image Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            saveImageToGallery(context, message.generatedImageBase64)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SasukeCobaltBlue),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save Image", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // IN-CHAT WORKFLOW 2: Generated Video Status Card
                        if (message.toolType == "VIDEO_GEN") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SasukeAmberWarning.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Videocam, contentDescription = null, tint = SasukeAmberWarning)
                                        Text(
                                            text = "Veo 3.1 Video Render Job",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SasukeAmberWarning
                                        )
                                    }
                                    Text(
                                        text = message.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (!message.videoOperationId.isNullOrEmpty()) {
                                        Button(
                                            onClick = { onCheckVideoStatus(message.videoOperationId) },
                                            colors = ButtonDefaults.buttonColors(containerColor = SasukeAmberWarning),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Check Cloud Status", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // IN-CHAT WORKFLOW 3: Model Arena Comparison Card
                        if (message.toolType == "ARENA" && message.comparisonResponseA != null && message.comparisonResponseB != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SasukeLightViolet.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    TabRow(
                                        selectedTabIndex = arenaTab,
                                        containerColor = Color.Transparent,
                                        contentColor = SasukeNeonCyan
                                    ) {
                                        Tab(
                                            selected = arenaTab == 0,
                                            onClick = { arenaTab = 0 },
                                            text = {
                                                Text("Model A (${message.latencyA ?: 0}ms)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        )
                                        Tab(
                                            selected = arenaTab == 1,
                                            onClick = { arenaTab = 1 },
                                            text = {
                                                Text("Model B (${message.latencyB ?: 0}ms)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    FormattedAiContent(
                                        text = if (arenaTab == 0) message.comparisonResponseA else message.comparisonResponseB
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    // Ratings & Winner Selection directly on card
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Rate:", style = MaterialTheme.typography.labelSmall, color = SasukeNeonCyan)
                                        Row {
                                            val currentRating = if (arenaTab == 0) message.ratingA else message.ratingB
                                            (1..5).forEach { star ->
                                                IconButton(
                                                    onClick = { onRateArena(arenaTab == 0, star) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (star <= currentRating) Icons.Default.Star else Icons.Outlined.StarBorder,
                                                        contentDescription = null,
                                                        tint = if (star <= currentRating) SasukeAmberWarning else Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Button(
                                                onClick = { onChooseWinner(if (arenaTab == 0) "Model A" else "Model B") },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (message.winner == (if (arenaTab == 0) "Model A" else "Model B")) SasukeGreenSuccess else SasukeElectricViolet
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(if (message.winner == (if (arenaTab == 0) "Model A" else "Model B")) "Winner!" else "Select Winner", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (message.toolType != "VIDEO_GEN" && message.generatedImageBase64.isNullOrEmpty()) {
                            // Standard Formatted Markdown text
                            FormattedAiContent(text = message.text)
                        }

                        // Grounded Web Sources
                        if (message.sources.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Grounded Sources:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SasukeNeonCyan
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                message.sources.forEach { source ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                                                } catch (e: Exception) {
                                                    // Browser unavailable
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = null,
                                                tint = SasukeNeonCyan,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = source.title.take(24),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action row under response
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (copied) {
                        Text(
                            text = "Copied!",
                            style = MaterialTheme.typography.labelSmall,
                            color = SasukeGreenSuccess,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Sasuke AI Response", message.text)
                            clipboard.setPrimaryClip(clip)
                            copied = true
                            scope.launch {
                                delay(2000)
                                copied = false
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantStreamingBubble(
    text: String,
    sources: List<WebSource>
) {
    Surface(
        shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .testTag("streaming_bubble")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = SasukeNeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Sasuke AI is processing...",
                    style = MaterialTheme.typography.labelSmall,
                    color = SasukeNeonCyan
                )
            }

            if (text.isNotEmpty()) {
                FormattedAiContent(text = text)
            } else {
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun saveImageToGallery(context: Context, base64: String) {
    try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val filename = "SasukeAI_${System.currentTimeMillis()}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SasukeAI")
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            }
            Toast.makeText(context, "Image saved to Pictures/SasukeAI", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
