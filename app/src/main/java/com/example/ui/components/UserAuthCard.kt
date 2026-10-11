package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.auth.AuthState
import com.example.data.auth.UserAuthManager
import com.example.data.model.AppLanguage
import com.example.data.model.AppStrings
import com.example.ui.theme.SasukeCobaltBlue
import com.example.ui.theme.SasukeElectricViolet
import com.example.ui.theme.SasukeGreenSuccess
import com.example.ui.theme.SasukeNeonCyan
import com.example.ui.theme.SasukeRedError
import kotlinx.coroutines.launch

@Composable
fun UserAuthCard(
    authManager: UserAuthManager,
    currentLanguage: AppLanguage,
    modifier: Modifier = Modifier
) {
    val authState by authManager.authState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "User Account",
                        tint = SasukeNeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "User & Cloud Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                when (val state = authState) {
                    is AuthState.Authenticated -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (state.user.isGuest) MaterialTheme.colorScheme.surface else SasukeGreenSuccess.copy(alpha = 0.2f),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = if (state.user.isGuest) "Guest Mode" else "Synced",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.user.isGuest) MaterialTheme.colorScheme.onSurfaceVariant else SasukeGreenSuccess,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (val state = authState) {
                is AuthState.Authenticated -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.user.photoUrl != null) {
                            AsyncImage(
                                model = state.user.photoUrl,
                                contentDescription = "User Avatar",
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, SasukeNeonCyan, CircleShape)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = SasukeElectricViolet,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = state.user.displayName?.take(1)?.uppercase() ?: "S",
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.user.displayName ?: "Shinobi User",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = state.user.email ?: "Cloud Firestore active (UID: ${state.user.uid.take(8)}...)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = { authManager.signOut() },
                            modifier = Modifier.testTag("auth_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Sign Out",
                                tint = SasukeRedError
                            )
                        }
                    }

                    if (state.user.isGuest) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "You are browsing as Guest. Sign in with Google to enable permanent Cloud Firestore sync across devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    authManager.signInWithGoogle()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SasukeCobaltBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guest_upgrade_google_sign_in_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in with Google", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is AuthState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = SasukeNeonCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Authenticating with Google...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is AuthState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = SasukeRedError,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = SasukeRedError
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        authManager.signInWithGoogle()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SasukeCobaltBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("auth_retry_google_button")
                            ) {
                                Text("Retry Google Sign-In", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { authManager.continueAsGuest() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("auth_error_guest_button")
                            ) {
                                Text("Guest Mode", fontSize = 12.sp)
                            }
                        }
                    }
                }
                is AuthState.Unauthenticated, is AuthState.Initial -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Sign in to persist your SASUKE AI chats, media creations, and custom settings to your secure Cloud Firestore account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        authManager.signInWithGoogle()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SasukeCobaltBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("auth_google_sign_in_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sign in with Google", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = { authManager.continueAsGuest() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("auth_guest_button")
                            ) {
                                Text("Guest Mode", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
