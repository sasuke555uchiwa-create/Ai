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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.components.ApiKeySetupDialog
import com.example.data.remote.TavilySearchWorkerClient
import com.example.ui.theme.SasukeCobaltBlue
import com.example.ui.theme.SasukeElectricViolet
import com.example.ui.theme.SasukeGreenSuccess
import com.example.ui.theme.SasukeNeonCyan
import com.example.ui.theme.SasukeRedError
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.theme.SasukeAmberWarning
import kotlinx.coroutines.launch
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
    var showKeySetupDialog by remember { mutableStateOf(false) }

    if (showKeySetupDialog) {
        ApiKeySetupDialog(
            chatViewModel = chatViewModel,
            userPreferences = userPreferences,
            onDismiss = { showKeySetupDialog = false },
            onSaved = { showKeySetupDialog = false }
        )
    }

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
            val authManager = remember { UserAuthManager.getInstance(context) }
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

        // Web Search Provider & API Keys (Tavily & Cloudflare)
        item {
            val tavilyClient = chatViewModel.tavilyWorkerClient
            val uriHandler = LocalUriHandler.current
            val clipboardManager = LocalClipboardManager.current
            val scope = rememberCoroutineScope()

            var tavilyKeyInput by remember { mutableStateOf(tavilyClient.getTavilyApiKey()) }
            var searchWorkerUrlInput by remember { mutableStateOf(tavilyClient.getWorkerUrl()) }
            var isShowTavilyKey by remember { mutableStateOf(false) }

            var isVerifyingTavilyKey by remember { mutableStateOf(false) }
            var tavilyKeyTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

            var isCheckingHealth by remember { mutableStateOf(false) }

            val isConfigured = tavilyClient.isConfigured()
            val isWorkerConfigured = tavilyClient.isWorkerConfigured()
            val isTavilyKeyConfigured = tavilyClient.isTavilyKeyConfigured()

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().testTag("settings_search_provider_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = SasukeNeonCyan
                            )
                            Text(
                                text = "Web Search & API Keys",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Quick Wizard Button
                        OutlinedButton(
                            onClick = { showKeySetupDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("open_key_setup_wizard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = SasukeNeonCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("1-Tap Wizard", fontSize = 11.sp, color = SasukeNeonCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status Badge Card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isConfigured) SasukeGreenSuccess.copy(alpha = 0.12f) else SasukeAmberWarning.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isConfigured) SasukeGreenSuccess.copy(alpha = 0.4f) else SasukeAmberWarning.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isConfigured) SasukeGreenSuccess else SasukeAmberWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = if (isConfigured) "Web Search Ready: " + tavilyClient.getActiveConfigurationLabel() else "Web Search Not Configured",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConfigured) SasukeGreenSuccess else SasukeAmberWarning
                                )
                                Text(
                                    text = if (isConfigured) "Real-time grounded research active with live citations." else "Add a free Tavily API Key or Cloudflare Worker URL below to activate live search.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- SECTION 1: Tavily API Key (Instant Zero-Server Setup) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = SasukeNeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Tavily Search API Key (Instant)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Link button to app.tavily.com
                        OutlinedButton(
                            onClick = {
                                try {
                                    uriHandler.openUri(TavilySearchWorkerClient.TAVILY_DASHBOARD_URL)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Visit: https://app.tavily.com/home", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("settings_open_tavily_link")
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = SasukeNeonCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("app.tavily.com", fontSize = 11.sp, color = SasukeNeonCyan)
                        }
                    }

                    Text(
                        text = "Get 1,000 free searches/month at app.tavily.com. Works instantly without server setup!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tavilyKeyInput,
                        onValueChange = {
                            tavilyKeyInput = it
                            tavilyKeyTestResult = null
                        },
                        label = { Text("Tavily API Key (tvly-...)") },
                        placeholder = { Text("tvly-xxxxxxxxxxxxxxxxxxxx") },
                        visualTransformation = if (isShowTavilyKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { isShowTavilyKey = !isShowTavilyKey }) {
                                    Icon(
                                        imageVector = if (isShowTavilyKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isShowTavilyKey) "Hide" else "Show",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                        if (clip.isNotEmpty()) {
                                            tavilyKeyInput = clip
                                            tavilyKeyTestResult = null
                                            Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("settings_paste_tavily_key")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = SasukeNeonCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("settings_tavily_key_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SasukeNeonCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (tavilyKeyInput.isNotBlank()) {
                                    isVerifyingTavilyKey = true
                                    tavilyKeyTestResult = null
                                    scope.launch {
                                        val res = tavilyClient.verifyTavilyKey(tavilyKeyInput)
                                        isVerifyingTavilyKey = false
                                        tavilyKeyTestResult = res
                                        if (res.first) {
                                            Toast.makeText(context, "Key verified! 1,000 monthly credits active.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, res.second, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter your Tavily Key first.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isVerifyingTavilyKey && tavilyKeyInput.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.testTag("settings_verify_tavily_button")
                        ) {
                            if (isVerifyingTavilyKey) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verifying...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("Verify Key", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Button(
                            onClick = {
                                tavilyClient.saveTavilyApiKey(tavilyKeyInput.trim())
                                userPreferences.setTavilyApiKey(tavilyKeyInput.trim())
                                chatViewModel.checkGroundingStatus()
                                Toast.makeText(context, "Tavily API Key saved successfully!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SasukeCobaltBlue),
                            modifier = Modifier.testTag("settings_save_tavily_key_button")
                        ) {
                            Text("Save Tavily Key", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(14.dp))

                    // --- SECTION 2: Cloudflare Worker Setup (Advanced) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = SasukeElectricViolet,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Cloudflare Worker Proxy (Optional)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Link button to dash.cloudflare.com/api-tokens
                        OutlinedButton(
                            onClick = {
                                try {
                                    uriHandler.openUri(TavilySearchWorkerClient.CLOUDFLARE_TOKENS_URL)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Visit: dash.cloudflare.com", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("settings_open_cloudflare_link")
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = SasukeElectricViolet
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cloudflare Tokens", fontSize = 11.sp, color = SasukeElectricViolet)
                        }
                    }

                    // Cloudflare Account ID chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(TavilySearchWorkerClient.CLOUDFLARE_ACCOUNT_ID))
                                Toast.makeText(context, "Cloudflare Account ID copied!", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(12.dp),
                                tint = SasukeElectricViolet
                            )
                            Text(
                                text = "Cloudflare Account ID: " + TavilySearchWorkerClient.CLOUDFLARE_ACCOUNT_ID,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = searchWorkerUrlInput,
                        onValueChange = { searchWorkerUrlInput = it },
                        label = { Text("Cloudflare Worker URL") },
                        placeholder = { Text("https://sasuke-search-worker.<subdomain>.workers.dev") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_worker_url_input"),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                    if (clip.isNotEmpty()) {
                                        searchWorkerUrlInput = clip
                                        Toast.makeText(context, "Pasted URL", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = SasukeElectricViolet,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SasukeElectricViolet,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (searchWorkerUrlInput.isNotBlank()) {
                                    isCheckingHealth = true
                                    tavilyClient.saveWorkerUrl(searchWorkerUrlInput)
                                    userPreferences.setSearchWorkerUrl(searchWorkerUrlInput)
                                    scope.launch {
                                        val healthy = tavilyClient.healthCheck()
                                        isCheckingHealth = false
                                        if (healthy) {
                                            Toast.makeText(context, "Search Worker is reachable and healthy!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Health check failed. Please verify your Worker URL.", Toast.LENGTH_LONG).show()
                                        }
                                        chatViewModel.checkGroundingStatus()
                                    }
                                } else {
                                    Toast.makeText(context, "Please enter a Worker URL first.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isCheckingHealth && searchWorkerUrlInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("test_worker_connection_button")
                        ) {
                            Text(
                                text = if (isCheckingHealth) "Testing..." else "Test Connection",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = {
                                tavilyClient.saveWorkerUrl(searchWorkerUrlInput)
                                userPreferences.setSearchWorkerUrl(searchWorkerUrlInput)
                                chatViewModel.checkGroundingStatus()
                                Toast.makeText(context, "Search Worker URL saved successfully!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SasukeCobaltBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("save_worker_url_button")
                        ) {
                            Text("Save Worker URL", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Setup Instructions Card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Easy Setup Guide:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SasukeNeonCyan
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Option 1 (Zero-Server, 30s): Tap 'app.tavily.com', copy your free key (1,000 searches/mo), paste above, and tap 'Save Tavily Key'. Done!\n• Option 2 (Cloudflare Proxy): Deploy /search-worker via Wrangler or Cloudflare Dashboard, paste the worker URL, and tap 'Save Worker URL'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
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
