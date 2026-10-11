package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.UserPreferences
import com.example.data.model.AppLanguage
import com.example.data.model.AppStrings
import com.example.data.remote.GeminiApiClient
import com.example.data.auth.UserAuthManager
import com.example.ui.components.UserAuthCard
import com.example.ui.theme.SasukeCobaltBlue
import com.example.ui.theme.SasukeElectricViolet
import com.example.ui.theme.SasukeGreenSuccess
import com.example.ui.theme.SasukeNeonCyan
import com.example.ui.theme.SasukeRedError
import com.example.ui.viewmodel.ChatViewModel

@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    chatViewModel: ChatViewModel,
    isDarkTheme: Boolean,
    currentLanguage: AppLanguage,
    onToggleTheme: (Boolean) -> Unit,
    onChangeLanguage: (AppLanguage) -> Unit
) {
    val context = LocalContext.current
    val apiClient = chatViewModel.apiClient
    var customKeyInput by remember { mutableStateOf(apiClient.getEffectiveApiKey()) }
    val isKeyConfigured = apiClient.isApiKeyConfigured()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Identity Header
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.sasuke_ai_icon_1791648464068),
                        contentDescription = "Sasuke AI Logo",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )
                    Column {
                        Text(
                            text = "SASUKE AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Version 1.0 (MVP) • Google Gemini Engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Firebase Auth & Cloud Firestore Account Card
        item {
            val authManager = remember { UserAuthManager(context) }
            UserAuthCard(
                authManager = authManager,
                currentLanguage = currentLanguage
            )
        }

        // Appearance & Theme Card
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Appearance & Interface",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dark Theme Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.DarkMode, contentDescription = null, tint = SasukeNeonCyan)
                            Text(
                                text = AppStrings.get("dark_theme", currentLanguage),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { onToggleTheme(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SasukeNeonCyan,
                                checkedTrackColor = SasukeElectricViolet
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Language Selector
                    Text(
                        text = AppStrings.get("interface_language", currentLanguage),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.values().forEach { lang ->
                            FilterChip(
                                selected = currentLanguage == lang,
                                onClick = { onChangeLanguage(lang) },
                                label = { Text(lang.displayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SasukeElectricViolet,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }

        // Provider Credentials & Secret Status
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Model Provider Configuration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isKeyConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isKeyConfigured) SasukeGreenSuccess else SasukeRedError
                        )
                        Column {
                            Text(
                                text = if (isKeyConfigured) AppStrings.get("api_key_active", currentLanguage) else AppStrings.get("api_key_missing", currentLanguage),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isKeyConfigured) SasukeGreenSuccess else SasukeRedError
                            )
                            Text(
                                text = "Provider: Google AI Studio (Gemini 3.5, 3.1 Pro, Veo, Flash Image)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customKeyInput,
                        onValueChange = { customKeyInput = it },
                        label = { Text("API Key Override / Direct Key") },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_api_key_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SasukeNeonCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            apiClient.saveCustomApiKey(customKeyInput)
                            chatViewModel.checkApiKey()
                            Toast.makeText(context, "API Key updated successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SasukeCobaltBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(AppStrings.get("save_key", currentLanguage), fontSize = 12.sp)
                    }
                }
            }
        }

        // Cost & Quota Controls Card
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = SasukeNeonCyan)
                        Text(
                            text = "Cost & Rate Limit Policies",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = AppStrings.get("quota_notice", currentLanguage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Text models (gemini-3.5-flash, gemini-3.1-pro): Included with standard rate limits.\n• Multimodal synthesis: Real-time image generation via gemini-2.5-flash-image.\n• Video rendering: Cloud asynchronous operations via Veo 3.1.\n• Security: Credentials never leaked into frontend logs or exports.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // About Sasuke AI Card
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = AppStrings.get("about_sasuke", currentLanguage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = AppStrings.get("about_desc", currentLanguage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
