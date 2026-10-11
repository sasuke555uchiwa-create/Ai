package com.example

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthState
import com.example.data.auth.UserAuthManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserAuthManagerTest {

    private lateinit var context: Context
    private lateinit var authManager: UserAuthManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear prefs before test
        context.getSharedPreferences("sasuke_auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        authManager = UserAuthManager(context)
    }

    @Test
    fun testContinueAsGuest_createsValidGuestProfile() {
        authManager.continueAsGuest()

        val state = authManager.authState.value
        assertTrue("State should be Authenticated", state is AuthState.Authenticated)
        val profile = (state as AuthState.Authenticated).user

        assertTrue("Profile should have isGuest=true", profile.isGuest)
        assertTrue("Guest UID should start with guest_", profile.uid.startsWith("guest_"))
        assertEquals("Guest Shinobi", profile.displayName)
        assertNull("Guest email should be null", profile.email)
    }

    @Test
    fun testFindActivity_resolvesActivityThroughContextWrapper() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val wrapper = ContextWrapper(activity)
        val doubleWrapper = ContextWrapper(wrapper)

        val resolved = authManager.findActivity(doubleWrapper)
        assertNotNull("Should resolve underlying Activity", resolved)
        assertEquals(activity, resolved)
    }

    @Test
    fun testFindActivity_returnsNullForPlainApplicationContext() {
        val resolved = authManager.findActivity(context)
        assertNull("Plain context should not resolve to Activity", resolved)
    }

    @Test
    fun testSignOut_transitionsToUnauthenticatedAndClearsGuest() {
        authManager.continueAsGuest()
        assertTrue(authManager.authState.value is AuthState.Authenticated)

        authManager.signOut()
        assertTrue(
            "State should be Unauthenticated after signOut",
            authManager.authState.value is AuthState.Unauthenticated
        )
    }
}
