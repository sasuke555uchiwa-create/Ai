package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppDatabase
import com.example.data.local.ChatSessionEntity
import com.example.data.model.AppLanguage
import com.example.data.model.AppStrings
import com.example.data.auth.UserAuthManager
import com.example.ui.components.UserAuthCard
import com.example.ui.components.AppScreen
import com.example.ui.theme.SasukeDarkCardBorder
import com.example.ui.theme.SasukeNeonCyan
import com.example.ui.theme.SasukeRedError
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.platform.testTag

@Composable
fun HistoryScreen(
    chatViewModel: ChatViewModel,
    currentLanguage: AppLanguage,
    onOpenSession: (String) -> Unit
) {
    val db = AppDatabase.getInstance(chatViewModel.getApplication())
    val dao = db.appDao()
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<ChatSessionEntity>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var filteredSessions by remember { mutableStateOf<List<ChatSessionEntity>>(emptyList()) }
    var matchingSnippets by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Load sessions and trigger cloud sync
    androidx.compose.runtime.LaunchedEffect(Unit) {
        chatViewModel.syncCloudHistory()
        dao.getAllSessions().collect { list ->
            sessions = list
        }
    }

    // Dynamic search filtering over sessions and messages
    androidx.compose.runtime.LaunchedEffect(searchQuery, sessions) {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            filteredSessions = sessions
            matchingSnippets = emptyMap()
        } else {
            val matchingMessages = dao.searchMessagesSnapshot(q)
            val snippetMap = mutableMapOf<String, String>()
            val matchingSessionIdsFromMessages = mutableSetOf<String>()

            matchingMessages.forEach { msg ->
                matchingSessionIdsFromMessages.add(msg.sessionId)
                if (!snippetMap.containsKey(msg.sessionId)) {
                    val idx = msg.text.indexOf(q, ignoreCase = true)
                    val start = maxOf(0, idx - 25)
                    val end = minOf(msg.text.length, idx + q.length + 35)
                    val prefix = if (start > 0) "..." else ""
                    val suffix = if (end < msg.text.length) "..." else ""
                    snippetMap[msg.sessionId] = prefix + msg.text.substring(start, end).replace("\n", " ") + suffix
                }
            }

            val matched = sessions.filter { session ->
                session.title.contains(q, ignoreCase = true) || matchingSessionIdsFromMessages.contains(session.id)
            }
            filteredSessions = matched
            matchingSnippets = snippetMap
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(14.dp)
    ) {
        // Title card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, tint = SasukeNeonCyan)
                    Column {
                        Text(
                            text = AppStrings.get("history", currentLanguage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${sessions.size} saved conversations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (sessions.isNotEmpty()) {
                    TextButton(onClick = { showDeleteConfirmDialog = true }) {
                        Text("Clear All", color = SasukeRedError, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Input Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search conversations & messages...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = SasukeNeonCyan
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SasukeNeonCyan,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Cloud Sync Account Status
        val authManager = remember { UserAuthManager.getInstance(chatViewModel.getApplication()) }
        UserAuthCard(
            authManager = authManager,
            currentLanguage = currentLanguage
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No conversation history yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (filteredSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No conversations matching \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredSessions, key = { it.id }) { session ->
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                    val dateString = dateFormat.format(Date(session.createdAt))
                    val snippet = matchingSnippets[session.id]

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SasukeDarkCardBorder, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                chatViewModel.switchToSession(session.id)
                                onOpenSession(session.id)
                            }
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = SasukeNeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = session.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$dateString • ${session.selectedModelId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            dao.deleteMessagesForSession(session.id)
                                            dao.deleteSession(session.id)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (!snippet.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = SasukeNeonCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = snippet,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(AppStrings.get("clear_history", currentLanguage)) },
            text = { Text(AppStrings.get("clear_history_confirm", currentLanguage)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            dao.clearAllMessages()
                            dao.clearAllSessions()
                            chatViewModel.startNewChat()
                            showDeleteConfirmDialog = false
                        }
                    }
                ) {
                    Text(AppStrings.get("delete", currentLanguage), color = SasukeRedError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(AppStrings.get("cancel", currentLanguage))
                }
            }
        )
    }
}
