package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.UserPreferences
import com.example.data.remote.TavilySearchWorkerClient
import com.example.ui.theme.SasukeAmberWarning
import com.example.ui.theme.SasukeCobaltBlue
import com.example.ui.theme.SasukeElectricViolet
import com.example.ui.theme.SasukeGreenSuccess
import com.example.ui.theme.SasukeNeonCyan
import com.example.ui.theme.SasukeRedError
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun ApiKeySetupDialog(
    chatViewModel: ChatViewModel,
    userPreferences: UserPreferences,
    onDismiss: () -> Unit,
    onSaved: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val tavilyClient = chatViewModel.tavilyWorkerClient

    var tavilyKeyInput by remember { mutableStateOf(tavilyClient.getTavilyApiKey()) }
    var workerUrlInput by remember { mutableStateOf(tavilyClient.getWorkerUrl()) }
    var isShowKey by remember { mutableStateOf(false) }

    var isVerifyingKey by remember { mutableStateOf(false) }
    var keyVerificationResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var isCheckingWorker by remember { mutableStateOf(false) }
    var workerCheckResult by remember { mutableStateOf<Boolean?>(null) }

    var isCloudflareExpanded by remember { mutableStateOf(workerUrlInput.isNotBlank()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth()
                .testTag("api_key_setup_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with Key Icon & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SasukeElectricViolet.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "API Keys",
                                tint = SasukeNeonCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Search API Key Setup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Add keys to activate live web research",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 1: Tavily API Key (Instant Zero-Server Mode)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = SasukeNeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "1. Tavily Search API Key",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Quick Link Button: app.tavily.com
                            OutlinedButton(
                                onClick = {
                                    try {
                                        uriHandler.openUri(TavilySearchWorkerClient.TAVILY_DASHBOARD_URL)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Visit: https://app.tavily.com/home", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("open_tavily_dashboard_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = SasukeNeonCyan
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Get Key", fontSize = 11.sp, color = SasukeNeonCyan)
                            }
                        }

                        Text(
                            text = "Free 1,000 live searches/month. Instant setup — works directly without deploying a server!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Tavily Key Input Field
                        OutlinedTextField(
                            value = tavilyKeyInput,
                            onValueChange = {
                                tavilyKeyInput = it
                                keyVerificationResult = null
                            },
                            label = { Text("Tavily API Key (tvly-...)") },
                            placeholder = { Text("tvly-xxxxxxxxxxxxxxxxxxxx") },
                            visualTransformation = if (isShowKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = SasukeNeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { isShowKey = !isShowKey }) {
                                        Icon(
                                            imageVector = if (isShowKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isShowKey) "Hide" else "Show",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                            if (clip.isNotEmpty()) {
                                                tavilyKeyInput = clip
                                                keyVerificationResult = null
                                                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.testTag("dialog_paste_tavily_key_button")
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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_tavily_key_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SasukeNeonCyan,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Test & Verify Tavily Key Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (tavilyKeyInput.isNotBlank()) {
                                        isVerifyingKey = true
                                        keyVerificationResult = null
                                        scope.launch {
                                            val result = tavilyClient.verifyTavilyKey(tavilyKeyInput)
                                            isVerifyingKey = false
                                            keyVerificationResult = result
                                            if (result.first) {
                                                Toast.makeText(context, "Tavily Key verified successfully!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Please paste your Tavily API Key first.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isVerifyingKey && tavilyKeyInput.isNotBlank(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SasukeCobaltBlue.copy(alpha = 0.8f)),
                                modifier = Modifier.testTag("dialog_verify_tavily_button")
                            ) {
                                if (isVerifyingKey) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Verifying...", fontSize = 11.sp)
                                } else {
                                    Text("Verify Key", fontSize = 11.sp)
                                }
                            }

                            // Inline Result Status
                            keyVerificationResult?.let { (valid, msg) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (valid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (valid) SasukeGreenSuccess else SasukeRedError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (valid) "Valid (1,000 credits)" else "Invalid key",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (valid) SasukeGreenSuccess else SasukeRedError,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 2: Cloudflare Worker Setup (Advanced / Optional)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCloudflareExpanded = !isCloudflareExpanded },
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
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "2. Cloudflare Worker (Optional)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Custom proxy with IP rate-limiting & caching",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            IconButton(onClick = { isCloudflareExpanded = !isCloudflareExpanded }) {
                                Icon(
                                    imageVector = if (isCloudflareExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expand"
                                )
                            }
                        }

                        AnimatedVisibility(visible = isCloudflareExpanded) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                // Cloudflare Account Token & Dashboard links
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Quick copy account ID
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.clickable {
                                            clipboardManager.setText(AnnotatedString(TavilySearchWorkerClient.CLOUDFLARE_ACCOUNT_ID))
                                            Toast.makeText(context, "Account ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Account ID",
                                                modifier = Modifier.size(12.dp),
                                                tint = SasukeNeonCyan
                                            )
                                            Text(
                                                text = "Acc: a3ed4c18...",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    // Open Cloudflare API Tokens link
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                uriHandler.openUri(TavilySearchWorkerClient.CLOUDFLARE_TOKENS_URL)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Visit: dash.cloudflare.com/api-tokens", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("open_cloudflare_tokens_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = SasukeElectricViolet
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cloudflare Tokens", fontSize = 10.sp, color = SasukeElectricViolet)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = workerUrlInput,
                                    onValueChange = {
                                        workerUrlInput = it
                                        workerCheckResult = null
                                    },
                                    label = { Text("Worker URL") },
                                    placeholder = { Text("https://sasuke-search-worker.<subdomain>.workers.dev") },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                val clip = clipboardManager.getText()?.text?.trim().orEmpty()
                                                if (clip.isNotEmpty()) {
                                                    workerUrlInput = clip
                                                    workerCheckResult = null
                                                    Toast.makeText(context, "Pasted URL", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.testTag("dialog_paste_worker_url_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = "Paste",
                                                tint = SasukeElectricViolet,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dialog_worker_url_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SasukeElectricViolet,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Health check button
                                Button(
                                    onClick = {
                                        if (workerUrlInput.isNotBlank()) {
                                            isCheckingWorker = true
                                            workerCheckResult = null
                                            tavilyClient.saveWorkerUrl(workerUrlInput)
                                            scope.launch {
                                                val ok = tavilyClient.healthCheck()
                                                isCheckingWorker = false
                                                workerCheckResult = ok
                                                if (ok) {
                                                    Toast.makeText(context, "Worker is online and reachable!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Worker health check failed.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    enabled = !isCheckingWorker && workerUrlInput.isNotBlank(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.testTag("dialog_test_worker_button")
                                ) {
                                    Text(
                                        text = if (isCheckingWorker) "Testing..." else "Test Worker Endpoint",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dialog_cancel_button")
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val cleanTavily = tavilyKeyInput.trim()
                            val cleanWorker = workerUrlInput.trim()

                            tavilyClient.saveTavilyApiKey(cleanTavily)
                            userPreferences.setTavilyApiKey(cleanTavily)

                            tavilyClient.saveWorkerUrl(cleanWorker)
                            userPreferences.setSearchWorkerUrl(cleanWorker)

                            chatViewModel.checkGroundingStatus()

                            val msg = when {
                                cleanTavily.isNotEmpty() && cleanWorker.isNotEmpty() -> "Saved! Dual-mode search activated."
                                cleanTavily.isNotEmpty() -> "Saved! Direct Tavily Search activated."
                                cleanWorker.isNotEmpty() -> "Saved! Cloudflare Worker proxy activated."
                                else -> "Settings saved."
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            onSaved?.invoke()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SasukeNeonCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("dialog_save_keys_button")
                    ) {
                        Text(
                            text = "Save & Enable Search",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.scrim
                        )
                    }
                }
            }
        }
    }
}
