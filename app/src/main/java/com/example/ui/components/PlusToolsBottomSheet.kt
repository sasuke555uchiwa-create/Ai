package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveToolType
import com.example.data.model.AppLanguage
import com.example.data.model.AppStrings
import com.example.ui.theme.SasukeCobaltBlue
import com.example.ui.theme.SasukeDarkCardBorder
import com.example.ui.theme.SasukeElectricViolet
import com.example.ui.theme.SasukeGreenSuccess
import com.example.ui.theme.SasukeLightViolet
import com.example.ui.theme.SasukeNeonCyan

data class ToolMenuItem(
    val type: ActiveToolType,
    val icon: ImageVector,
    val titleKey: String,
    val descKey: String,
    val badge: String,
    val badgeColor: Color,
    val gradientColors: List<Color>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusToolsBottomSheet(
    currentLanguage: AppLanguage,
    onPickFiles: () -> Unit,
    onLaunchCamera: () -> Unit,
    onPickPhotos: () -> Unit,
    onSelectTool: (ActiveToolType) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val tools = listOf(
        ToolMenuItem(
            type = ActiveToolType.IMAGE_GEN,
            icon = Icons.Default.AutoAwesome,
            titleKey = "tool_generate_image",
            descKey = "tool_generate_image_desc",
            badge = "Flash 2.5",
            badgeColor = SasukeNeonCyan,
            gradientColors = listOf(SasukeLightViolet, SasukeElectricViolet)
        ),
        ToolMenuItem(
            type = ActiveToolType.VIDEO_GEN,
            icon = Icons.Default.Videocam,
            titleKey = "tool_generate_video",
            descKey = "tool_generate_video_desc",
            badge = "Veo 3.1",
            badgeColor = Color(0xFFF59E0B),
            gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6))
        ),
        ToolMenuItem(
            type = ActiveToolType.WEB_SEARCH,
            icon = Icons.Default.Public,
            titleKey = "tool_search_web",
            descKey = "tool_search_web_desc",
            badge = "Tavily Live",
            badgeColor = SasukeGreenSuccess,
            gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF2563EB))
        ),
        ToolMenuItem(
            type = ActiveToolType.MODEL_ARENA,
            icon = Icons.AutoMirrored.Filled.CompareArrows,
            titleKey = "tool_compare_models",
            descKey = "tool_compare_models_desc",
            badge = "Flash vs Pro",
            badgeColor = Color(0xFF8B5CF6),
            gradientColors = listOf(Color(0xFF7C3AED), Color(0xFF1D4ED8))
        ),
        ToolMenuItem(
            type = ActiveToolType.STUDY,
            icon = Icons.Default.Science,
            titleKey = "tool_study",
            descKey = "tool_study_desc",
            badge = "STEM & Science",
            badgeColor = SasukeGreenSuccess,
            gradientColors = listOf(Color(0xFF10B981), Color(0xFF047857))
        ),
        ToolMenuItem(
            type = ActiveToolType.CODING,
            icon = Icons.Default.Code,
            titleKey = "tool_coding",
            descKey = "tool_coding_desc",
            badge = "Senior Dev",
            badgeColor = SasukeNeonCyan,
            gradientColors = listOf(Color(0xFF3B82F6), Color(0xFF1E40AF))
        ),
        ToolMenuItem(
            type = ActiveToolType.ADVANCED_RESEARCH,
            icon = Icons.Default.Psychology,
            titleKey = "tool_advanced_research",
            descKey = "tool_advanced_research_desc",
            badge = "Gemini 3.1 Pro",
            badgeColor = SasukeNeonCyan,
            gradientColors = listOf(Color(0xFF6D28D9), Color(0xFF4338CA))
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = Modifier.testTag("plus_tools_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = AppStrings.get("plus_tools_title", currentLanguage),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = AppStrings.get("plus_tools_subtitle", currentLanguage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // TOP THREE LARGE ROUNDED BUTTONS: Files, Camera, Photos (Google Gemini inspired)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Files Button
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            onDismiss()
                            onPickFiles()
                        }
                        .border(1.dp, SasukeDarkCardBorder.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .testTag("plus_top_files_button")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(SasukeLightViolet, SasukeElectricViolet))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = AppStrings.get("top_files", currentLanguage),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = AppStrings.get("top_files", currentLanguage),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 2. Camera Button
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            onDismiss()
                            onLaunchCamera()
                        }
                        .border(1.dp, SasukeDarkCardBorder.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .testTag("plus_top_camera_button")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(SasukeNeonCyan, SasukeCobaltBlue))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = AppStrings.get("top_camera", currentLanguage),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = AppStrings.get("top_camera", currentLanguage),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 3. Photos Button
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            onDismiss()
                            onPickPhotos()
                        }
                        .border(1.dp, SasukeDarkCardBorder.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .testTag("plus_top_photos_button")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFFEC4899)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = AppStrings.get("top_photos", currentLanguage),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = AppStrings.get("top_photos", currentLanguage),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Divider and AI Tools Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    modifier = Modifier.weight(0.1f)
                )
                Text(
                    text = AppStrings.get("ai_tools_header", currentLanguage),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    modifier = Modifier.weight(0.9f)
                )
            }

            // Scrollable AI Tools list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tools) { item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onSelectTool(item.type)
                                onDismiss()
                            }
                            .border(1.dp, SasukeDarkCardBorder.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("tool_item_${item.type.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Icon Box with gradient
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(item.gradientColors)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Texts
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = AppStrings.get(item.titleKey, currentLanguage),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    // Badge
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = item.badgeColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = item.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = item.badgeColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = AppStrings.get(item.descKey, currentLanguage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
