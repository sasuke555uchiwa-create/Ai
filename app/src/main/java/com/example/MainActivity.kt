package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.data.local.UserPreferences
import com.example.data.model.AppLanguage
import com.example.ui.components.AppScreen
import com.example.ui.components.SasukeBottomNavBar
import com.example.ui.components.SasukeNavigationDrawerSheet
import com.example.ui.components.SasukeTopAppBar
import com.example.ui.screens.ArenaScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CodingScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudyScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.theme.SasukeAiTheme
import com.example.ui.viewmodel.ArenaViewModel
import com.example.ui.viewmodel.AssistantViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.MediaStudioViewModel

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()
    private val arenaViewModel: ArenaViewModel by viewModels()
    private val mediaStudioViewModel: MediaStudioViewModel by viewModels()
    private val assistantViewModel: AssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val userPreferences = UserPreferences(this)

        setContent {
            val isDarkTheme by userPreferences.isDarkTheme.collectAsState()
            val currentLanguage by userPreferences.language.collectAsState()

            SasukeAiApp(
                userPreferences = userPreferences,
                chatViewModel = chatViewModel,
                arenaViewModel = arenaViewModel,
                mediaStudioViewModel = mediaStudioViewModel,
                assistantViewModel = assistantViewModel,
                isDarkTheme = isDarkTheme,
                currentLanguage = currentLanguage
            )
        }
    }
}

@Composable
fun SasukeAiApp(
    userPreferences: UserPreferences,
    chatViewModel: ChatViewModel,
    arenaViewModel: ArenaViewModel,
    mediaStudioViewModel: MediaStudioViewModel,
    assistantViewModel: AssistantViewModel,
    isDarkTheme: Boolean,
    currentLanguage: AppLanguage
) {
    var currentScreen by remember { mutableStateOf(AppScreen.CHAT) }
    var showNavSheet by remember { mutableStateOf(false) }

    // Hardware back button returns to Chat if on sub-screen
    BackHandler(enabled = currentScreen != AppScreen.CHAT) {
        currentScreen = AppScreen.CHAT
    }

    CompositionLocalProvider(LocalLayoutDirection provides currentLanguage.direction) {
        SasukeAiTheme(darkTheme = isDarkTheme) {
            Scaffold(
                topBar = {
                    SasukeTopAppBar(
                        currentScreen = currentScreen,
                        currentLanguage = currentLanguage,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = {
                            userPreferences.setDarkTheme(!isDarkTheme)
                        },
                        onChangeLanguage = { newLang ->
                            userPreferences.setLanguage(newLang)
                        },
                        onOpenNavSheet = { showNavSheet = true }
                    )
                },
                bottomBar = {
                    SasukeBottomNavBar(
                        currentScreen = currentScreen,
                        currentLanguage = currentLanguage,
                        onNavigate = { screen -> currentScreen = screen }
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentScreen) {
                        AppScreen.CHAT -> {
                            ChatScreen(
                                viewModel = chatViewModel,
                                currentLanguage = currentLanguage,
                                onNavigateToSettings = { currentScreen = AppScreen.SETTINGS }
                            )
                        }
                        AppScreen.ARENA -> {
                            ArenaScreen(
                                viewModel = arenaViewModel,
                                currentLanguage = currentLanguage
                            )
                        }
                        AppScreen.IMAGE_STUDIO -> {
                            ImageStudioScreen(
                                viewModel = mediaStudioViewModel,
                                currentLanguage = currentLanguage
                            )
                        }
                        AppScreen.VIDEO_STUDIO -> {
                            VideoStudioScreen(
                                viewModel = mediaStudioViewModel,
                                currentLanguage = currentLanguage
                            )
                        }
                        AppScreen.AI_SEARCH -> {
                            SearchScreen(
                                apiClient = chatViewModel.apiClient,
                                currentLanguage = currentLanguage
                            )
                        }
                        AppScreen.CODING -> {
                            CodingScreen(
                                viewModel = assistantViewModel,
                                currentLanguage = currentLanguage
                            )
                        }
                        AppScreen.STUDY -> {
                            StudyScreen(
                                viewModel = assistantViewModel,
                                currentLanguage = currentLanguage
                            )
                        }
                        AppScreen.HISTORY -> {
                            HistoryScreen(
                                chatViewModel = chatViewModel,
                                currentLanguage = currentLanguage,
                                onOpenSession = {
                                    currentScreen = AppScreen.CHAT
                                }
                            )
                        }
                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                userPreferences = userPreferences,
                                chatViewModel = chatViewModel,
                                isDarkTheme = isDarkTheme,
                                currentLanguage = currentLanguage,
                                onToggleTheme = { userPreferences.setDarkTheme(it) },
                                onChangeLanguage = { userPreferences.setLanguage(it) }
                            )
                        }
                    }
                }

                if (showNavSheet) {
                    SasukeNavigationDrawerSheet(
                        currentScreen = currentScreen,
                        currentLanguage = currentLanguage,
                        onNavigate = { selectedScreen ->
                            currentScreen = selectedScreen
                        },
                        onDismiss = { showNavSheet = false }
                    )
                }
            }
        }
    }
}
