package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.AppStrings
import com.example.data.model.AvailableModels
import com.example.ui.components.FormattedAiContent
import com.example.ui.theme.SasukeAmberWarning
import com.example.ui.theme.SasukeCobaltBlue
import com.example.ui.theme.SasukeDarkCardBorder
import com.example.ui.theme.SasukeElectricViolet
import com.example.ui.theme.SasukeGreenSuccess
import com.example.ui.theme.SasukeLightViolet
import com.example.ui.theme.SasukeNeonCyan
import com.example.ui.theme.SasukeRedError
import com.example.ui.viewmodel.ArenaViewModel
import com.example.ui.viewmodel.BenchmarkPreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArenaScreen(
    viewModel: ArenaViewModel,
    currentLanguage: AppLanguage
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Side-by-Side/Comparison, 1: Benchmarks, 2: Saved History
    var modelAMenuExpanded by remember { mutableStateOf(false) }
    var modelBMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = SasukeNeonCyan
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Model Battle", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Benchmark Tests", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Saved Results (${uiState.savedRecords.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // ARENA BATTLE VIEW
                    item {
                        // Title banner
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
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                        contentDescription = null,
                                        tint = SasukeNeonCyan
                                    )
                                    Text(
                                        text = AppStrings.get("arena_title", currentLanguage),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = AppStrings.get("arena_subtitle", currentLanguage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Model Selectors Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Model A Box
                            Box(modifier = Modifier.weight(1f)) {
                                val modelA = AvailableModels.textChatModels.firstOrNull { it.id == uiState.modelAId }
                                    ?: AvailableModels.GEMINI_3_5_FLASH
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, SasukeCobaltBlue, RoundedCornerShape(12.dp))
                                        .clickable { modelAMenuExpanded = true }
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "MODEL A",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SasukeCobaltBlue,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = modelA.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = modelAMenuExpanded,
                                    onDismissRequest = { modelAMenuExpanded = false }
                                ) {
                                    AvailableModels.textChatModels.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m.displayName) },
                                            onClick = {
                                                viewModel.setModelA(m.id)
                                                modelAMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Model B Box
                            Box(modifier = Modifier.weight(1f)) {
                                val modelB = AvailableModels.textChatModels.firstOrNull { it.id == uiState.modelBId }
                                    ?: AvailableModels.GEMINI_3_1_PRO
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, SasukeElectricViolet, RoundedCornerShape(12.dp))
                                        .clickable { modelBMenuExpanded = true }
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "MODEL B",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SasukeLightViolet,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = modelB.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = modelBMenuExpanded,
                                    onDismissRequest = { modelBMenuExpanded = false }
                                ) {
                                    AvailableModels.textChatModels.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m.displayName) },
                                            onClick = {
                                                viewModel.setModelB(m.id)
                                                modelBMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Prompt Input
                    item {
                        OutlinedTextField(
                            value = uiState.prompt,
                            onValueChange = { viewModel.setPrompt(it) },
                            placeholder = {
                                Text(AppStrings.get("arena_prompt_placeholder", currentLanguage))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("arena_prompt_input"),
                            shape = RoundedCornerShape(12.dp),
                            minLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SasukeNeonCyan,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    // Run Battle Button
                    item {
                        Button(
                            onClick = { viewModel.runBattle() },
                            enabled = !uiState.isRunning && uiState.prompt.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("run_arena_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SasukeElectricViolet
                            )
                        ) {
                            if (uiState.isRunning) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(AppStrings.get("running", currentLanguage))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(AppStrings.get("run_comparison", currentLanguage), fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Objective Metrics Dashboard if responses exist
                    if (uiState.responseA.isNotEmpty() || uiState.responseB.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Objective Metrics (Measured)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SasukeNeonCyan
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "Model A Latency: ${uiState.latencyMsA} ms",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Est. Tokens A: ~${uiState.responseA.length / 4}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "Model B Latency: ${uiState.latencyMsB} ms",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Est. Tokens B: ~${uiState.responseB.length / 4}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Response A Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SasukeCobaltBlue, RoundedCornerShape(14.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Model A Response (${uiState.latencyMsA} ms)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SasukeCobaltBlue
                                        )
                                        // Star rating for Model A
                                        Row {
                                            (1..5).forEach { star ->
                                                IconButton(
                                                    onClick = { viewModel.setRatingA(star) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (star <= uiState.ratingA) Icons.Default.Star else Icons.Outlined.StarBorder,
                                                        contentDescription = "Rate $star",
                                                        tint = if (star <= uiState.ratingA) SasukeAmberWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (uiState.errorA != null) {
                                        Text(
                                            text = "Error: ${uiState.errorA}",
                                            color = SasukeRedError,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else {
                                        FormattedAiContent(text = uiState.responseA)
                                    }
                                }
                            }
                        }

                        // Response B Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SasukeElectricViolet, RoundedCornerShape(14.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Model B Response (${uiState.latencyMsB} ms)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SasukeLightViolet
                                        )
                                        // Star rating for Model B
                                        Row {
                                            (1..5).forEach { star ->
                                                IconButton(
                                                    onClick = { viewModel.setRatingB(star) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (star <= uiState.ratingB) Icons.Default.Star else Icons.Outlined.StarBorder,
                                                        contentDescription = "Rate $star",
                                                        tint = if (star <= uiState.ratingB) SasukeAmberWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (uiState.errorB != null) {
                                        Text(
                                            text = "Error: ${uiState.errorB}",
                                            color = SasukeRedError,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else {
                                        FormattedAiContent(text = uiState.responseB)
                                    }
                                }
                            }
                        }

                        // Decision & Save Result
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = AppStrings.get("choose_winner", currentLanguage),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.setWinner("A") },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (uiState.winner == "A") SasukeCobaltBlue else MaterialTheme.colorScheme.surface
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                AppStrings.get("winner_a", currentLanguage),
                                                color = if (uiState.winner == "A") Color.White else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Button(
                                            onClick = { viewModel.setWinner("B") },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (uiState.winner == "B") SasukeLightViolet else MaterialTheme.colorScheme.surface
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                AppStrings.get("winner_b", currentLanguage),
                                                color = if (uiState.winner == "B") Color.White else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Button(
                                            onClick = { viewModel.setWinner("TIE") },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (uiState.winner == "TIE") SasukeNeonCyan else MaterialTheme.colorScheme.surface
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                AppStrings.get("winner_tie", currentLanguage),
                                                color = if (uiState.winner == "TIE") Color.Black else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { viewModel.saveComparison() },
                                        enabled = !uiState.isSaved,
                                        colors = ButtonDefaults.buttonColors(containerColor = SasukeGreenSuccess),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.isSaved) Icons.Default.CheckCircle else Icons.Default.BookmarkBorder,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (uiState.isSaved) "Saved to Database" else AppStrings.get("save_comparison", currentLanguage))
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // PRESET BENCHMARK TESTS
                    item {
                        Text(
                            text = "Standardized Repeatable Benchmark Tests",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tap any preset test to populate the Arena prompt and compare models on specific competencies.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(viewModel.benchmarkPresets.size) { index ->
                        val preset = viewModel.benchmarkPresets[index]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.applyBenchmark(preset)
                                    selectedTab = 0
                                }
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SasukeNeonCyan
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = preset.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = preset.prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // SAVED ARENA RECORDS
                    if (uiState.savedRecords.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No saved comparison results yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(uiState.savedRecords.size) { index ->
                            val record = uiState.savedRecords[index]
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, SasukeDarkCardBorder, RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Category: ${record.category}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SasukeNeonCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Winner: ${record.winner ?: "Unrated"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SasukeGreenSuccess,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = record.prompt,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Model A: ${record.latencyMsA}ms (${record.ratingA}★)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Model B: ${record.latencyMsB}ms (${record.ratingB}★)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
