package com.example.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class AuthManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("sasuke_auth_prefs", Context.MODE_PRIVATE)
    private val credentialManager = CredentialManager.create(context)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isFirebaseConfigured = MutableStateFlow(checkIfFirebaseAvailable())
    val isFirebaseConfigured: StateFlow<Boolean> = _isFirebaseConfigured.asStateFlow()

    init {
        restoreSession()
    }

    private fun checkIfFirebaseAvailable(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun getFirebaseAuth(): FirebaseAuth? {
        return try {
            if (checkIfFirebaseAvailable()) {
                FirebaseAuth.getInstance()
            } else null
        } catch (e: Exception) {
            null
        }
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

    suspend fun signInWithEmail(email: String, pass: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        val auth = getFirebaseAuth()
        if (auth == null) {
            val error = "Firebase is not yet linked via google-services.json. Please configure your Firebase project in the Firebase Console or continue as Guest."
            _authState.value = AuthState.Error(error)
            return@withContext Result.failure(Exception(error))
        }

        _authState.value = AuthState.Loading
        try {
            val result = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw Exception("User is null after sign in")
            val profile = UserProfile(
                uid = user.uid,
                email = user.email,
                displayName = user.displayName ?: user.email?.substringBefore("@"),
                photoUrl = user.photoUrl?.toString(),
                isGuest = false
            )
            prefs.edit().putBoolean("is_guest", false).apply()
            _authState.value = AuthState.Authenticated(profile)
            Result.success(profile)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Authentication failed"
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        }
    }

    suspend fun registerWithEmail(email: String, pass: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        val auth = getFirebaseAuth()
        if (auth == null) {
            val error = "Firebase is not yet linked via google-services.json. Please configure your Firebase project in the Firebase Console or continue as Guest."
            _authState.value = AuthState.Error(error)
            return@withContext Result.failure(Exception(error))
        }

        _authState.value = AuthState.Loading
        try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw Exception("User is null after registration")
            val profile = UserProfile(
                uid = user.uid,
                email = user.email,
                displayName = user.email?.substringBefore("@"),
                photoUrl = null,
                isGuest = false
            )
            prefs.edit().putBoolean("is_guest", false).apply()
            _authState.value = AuthState.Authenticated(profile)
            Result.success(profile)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Registration failed"
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(): Result<UserProfile> = withContext(Dispatchers.IO) {
        val auth = getFirebaseAuth()
        if (auth == null) {
            val error = "Firebase is not yet linked via google-services.json. Please connect your Firebase project in the Firebase Console."
            _authState.value = AuthState.Error(error)
            return@withContext Result.failure(Exception(error))
        }

        _authState.value = AuthState.Loading
        try {
            // Retrieve default_web_client_id if configured
            val clientId = try {
                context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName))
            } catch (e: Exception) {
                ""
            }

            if (clientId.isEmpty()) {
                val error = "default_web_client_id is not present in google-services.json. Please configure the OAuth 2.0 Web Client ID in the Firebase Console."
                _authState.value = AuthState.Error(error)
                return@withContext Result.failure(Exception(error))
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response: GetCredentialResponse = credentialManager.getCredential(
                request = request,
                context = context
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
                prefs.edit().putBoolean("is_guest", false).apply()
                _authState.value = AuthState.Authenticated(profile)
                Result.success(profile)
            } else {
                throw Exception("Unexpected credential type returned")
            }
        } catch (e: GetCredentialCancellationException) {
            _authState.value = AuthState.Unauthenticated
            Result.failure(e)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Google Sign-In failed"
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            getFirebaseAuth()?.signOut()
        } catch (e: Exception) {
            // ignore
        }
        prefs.edit().clear().apply()
        _authState.value = AuthState.Unauthenticated
    }
}
