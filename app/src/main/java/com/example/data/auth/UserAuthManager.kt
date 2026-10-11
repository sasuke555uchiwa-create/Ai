package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.model.FirestoreUserProfile
import com.example.data.remote.FirestoreChatRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UserProfile(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isGuest: Boolean = false
)

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: UserProfile) : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * UserAuthManager handles Firebase Authentication sign-in, sign-out, session restoration,
 * and profile & chat history synchronization with Cloud Firestore for SASUKE AI.
 */
class UserAuthManager(private val context: Context) {

    private val tag = "UserAuthManager"
    private val prefs = context.getSharedPreferences("sasuke_auth_prefs", Context.MODE_PRIVATE)
    private val credentialManager = CredentialManager.create(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isFirebaseConfigured = MutableStateFlow(checkIfFirebaseAvailable())
    val isFirebaseConfigured: StateFlow<Boolean> = _isFirebaseConfigured.asStateFlow()

    private val firestoreRepo = lazy { FirestoreChatRepository(context) }

    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    companion object {
        @Volatile
        private var instance: UserAuthManager? = null

        fun getInstance(context: Context): UserAuthManager {
            return instance ?: synchronized(this) {
                instance ?: UserAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        setupAuthStateListener()
        restoreSession()
    }

    fun findActivity(ctx: Context?): Activity? {
        var current = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun checkIfFirebaseAvailable(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun getFirebaseAuth(): FirebaseAuth? {
        return try {
            if (checkIfFirebaseAvailable()) {
                FirebaseAuth.getInstance()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    val currentUser: FirebaseUser?
        get() = getFirebaseAuth()?.currentUser

    val currentProfile: UserProfile?
        get() {
            val state = _authState.value
            return if (state is AuthState.Authenticated) state.user else null
        }

    private fun setupAuthStateListener() {
        val auth = getFirebaseAuth() ?: return
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                val profile = UserProfile(
                    uid = user.uid,
                    email = user.email,
                    displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Shinobi User",
                    photoUrl = user.photoUrl?.toString(),
                    isGuest = false
                )
                _authState.value = AuthState.Authenticated(profile)

                // Background restore of cloud history into Room without wiping local data
                scope.launch(Dispatchers.IO) {
                    try {
                        val appDb = AppDatabase.getInstance(context)
                        firestoreRepo.value.syncUserHistoryToRoom(user.uid, appDb.appDao())
                    } catch (e: Exception) {
                        Log.w(tag, "Cloud history sync notice: ${e.message}")
                    }
                }
            } else {
                val isGuest = prefs.getBoolean("is_guest", false)
                if (isGuest) {
                    val guestId = prefs.getString("guest_id", "guest_${System.currentTimeMillis()}") ?: "guest_user"
                    _authState.value = AuthState.Authenticated(
                        UserProfile(
                            uid = guestId,
                            email = null,
                            displayName = "Guest Shinobi",
                            photoUrl = null,
                            isGuest = true
                        )
                    )
                } else if (_authState.value !is AuthState.Loading && _authState.value !is AuthState.Error) {
                    _authState.value = AuthState.Unauthenticated
                }
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    fun restoreSession() {
        val auth = getFirebaseAuth()
        if (auth != null && auth.currentUser != null) {
            val user = auth.currentUser!!
            _authState.value = AuthState.Authenticated(
                UserProfile(
                    uid = user.uid,
                    email = user.email,
                    displayName = user.displayName ?: user.email?.substringBefore("@"),
                    photoUrl = user.photoUrl?.toString(),
                    isGuest = false
                )
            )
            return
        }

        val isGuest = prefs.getBoolean("is_guest", false)
        if (isGuest) {
            val guestId = prefs.getString("guest_id", "guest_${System.currentTimeMillis()}") ?: "guest_user"
            _authState.value = AuthState.Authenticated(
                UserProfile(
                    uid = guestId,
                    email = null,
                    displayName = "Guest Shinobi",
                    photoUrl = null,
                    isGuest = true
                )
            )
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun continueAsGuest() {
        val guestId = "guest_${System.currentTimeMillis()}"
        prefs.edit()
            .putBoolean("is_guest", true)
            .putString("guest_id", guestId)
            .apply()

        _authState.value = AuthState.Authenticated(
            UserProfile(
                uid = guestId,
                email = null,
                displayName = "Guest Shinobi",
                photoUrl = null,
                isGuest = true
            )
        )
    }

    /**
     * Signs in with Google using Jetpack CredentialManager with a foreground Activity context
     * and Firebase Authentication.
     */
    suspend fun signInWithGoogle(activityContext: Context? = null): Result<UserProfile> = withContext(Dispatchers.IO) {
        val auth = getFirebaseAuth()
        if (auth == null) {
            val error = "Firebase is not yet linked. Please verify google-services.json configuration."
            _authState.value = AuthState.Error(error)
            return@withContext Result.failure(IllegalStateException(error))
        }

        _authState.value = AuthState.Loading
        try {
            val serverClientId = try {
                context.getString(R.string.default_web_client_id)
            } catch (e: Exception) {
                ""
            }

            if (serverClientId.isEmpty()) {
                val error = "default_web_client_id is not present in build configuration."
                _authState.value = AuthState.Error(error)
                return@withContext Result.failure(IllegalStateException(error))
            }

            // Exactly one option per GetCredentialRequest
            val googleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()

            // Resolve foreground Activity context for Credential Manager UI prompt
            val foregroundActivity = findActivity(activityContext) ?: findActivity(context)
            val launchContext: Context = foregroundActivity ?: activityContext ?: context

            val response: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = launchContext
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user ?: throw Exception("User is null after Google Sign-In")

                val profile = UserProfile(
                    uid = user.uid,
                    email = user.email,
                    displayName = user.displayName ?: user.email?.substringBefore("@"),
                    photoUrl = user.photoUrl?.toString(),
                    isGuest = false
                )

                // Sync profile to Cloud Firestore
                try {
                    firestoreRepo.value.saveUserProfile(
                        FirestoreUserProfile(
                            uid = profile.uid,
                            email = profile.email,
                            displayName = profile.displayName,
                            photoUrl = profile.photoUrl
                        )
                    )
                } catch (e: Exception) {
                    Log.w(tag, "Could not persist profile to Firestore: ${e.message}")
                }

                // Sync user cloud history into Room
                try {
                    val appDb = AppDatabase.getInstance(context)
                    firestoreRepo.value.syncUserHistoryToRoom(profile.uid, appDb.appDao())
                } catch (e: Exception) {
                    Log.w(tag, "Could not restore cloud history to Room: ${e.message}")
                }

                prefs.edit().putBoolean("is_guest", false).apply()
                _authState.value = AuthState.Authenticated(profile)
                Result.success(profile)
            } else {
                throw Exception("Unexpected credential type returned: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(tag, "Google Sign-In was cancelled by user")
            restoreSession()
            Result.failure(e)
        } catch (e: GetCredentialInterruptedException) {
            Log.d(tag, "Google Sign-In was interrupted")
            restoreSession()
            Result.failure(e)
        } catch (e: NoCredentialException) {
            val msg = "No Google account found on device. Please sign in to a Google account in Android Settings."
            Log.w(tag, msg, e)
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        } catch (e: GetCredentialProviderConfigurationException) {
            val msg = "Google Sign-In configuration error. Please verify Google Play Services and project settings."
            Log.e(tag, msg, e)
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        } catch (e: GetCredentialUnsupportedException) {
            val msg = "Credential Manager is not supported on this device. Please update Google Play Services."
            Log.e(tag, msg, e)
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        } catch (e: FirebaseAuthInvalidUserException) {
            val msg = "Firebase user account is disabled or not found."
            Log.e(tag, msg, e)
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            val msg = "Invalid Google credentials or ID token."
            Log.e(tag, msg, e)
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        } catch (e: FirebaseNetworkException) {
            val msg = "Network error during authentication. Please check your internet connection."
            Log.e(tag, msg, e)
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        } catch (e: GetCredentialCustomException) {
            val msg = "Google Sign-In error: ${e.errorMessage ?: e.message}"
            Log.e(tag, msg, e)
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        } catch (e: GetCredentialException) {
            val msg = "Google Sign-In failed: ${e.localizedMessage ?: e.message}"
            Log.e(tag, msg, e)
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Authentication failed: ${e.javaClass.simpleName}"
            Log.e(tag, "Google Sign-In error: $msg", e)
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        }
    }

    /**
     * Signs out of Firebase and clears local credentials.
     */
    fun signOut(): Result<Unit> {
        return try {
            getFirebaseAuth()?.signOut()
            try {
                scope.launch(Dispatchers.IO) {
                    try {
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    } catch (e: Exception) {
                        Log.w(tag, "Error clearing credential manager state: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                // Ignore background clear failure
            }
            prefs.edit().clear().apply()
            _authState.value = AuthState.Unauthenticated
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Sign-out error: ${e.message}", e)
            _authState.value = AuthState.Error(e.localizedMessage ?: "Sign-out failed")
            Result.failure(e)
        }
    }
}
