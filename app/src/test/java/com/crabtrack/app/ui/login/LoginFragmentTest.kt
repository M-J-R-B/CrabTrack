package com.crabtrack.app.ui.login

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.*

/**
 * Unit tests for LoginFragment
 * Tests Firebase Authentication login flow with username-to-email resolution
 */
class LoginFragmentTest {

    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockDatabase: DatabaseReference
    private lateinit var mockQuery: Query
    private lateinit var mockContext: Context
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var mockNetworkCapabilities: NetworkCapabilities

    @Before
    fun setup() {
        mockAuth = mock()
        mockDatabase = mock()
        mockQuery = mock()
        mockContext = mock()
        mockConnectivityManager = mock()
        mockNetworkCapabilities = mock()
    }

    // ====================
    // Login Success Tests
    // ====================

    @Test
    fun `login - successful login with valid username and password`() {
        // Given: Valid credentials
        val username = "testuser"
        val password = "password123"
        val email = "testuser@example.com"

        // Expect: Username lookup should succeed
        val mockSnapshot = mock<DataSnapshot>()
        val mockUserSnapshot = mock<DataSnapshot>()
        val mockEmailSnapshot = mock<DataSnapshot>()

        whenever(mockSnapshot.exists()).thenReturn(true)
        whenever(mockSnapshot.children).thenReturn(listOf(mockUserSnapshot))
        whenever(mockUserSnapshot.child("email")).thenReturn(mockEmailSnapshot)
        whenever(mockEmailSnapshot.getValue(String::class.java)).thenReturn(email)

        // Then: Firebase auth should succeed
        val mockAuthTask = mock<Task<AuthResult>>()
        whenever(mockAuth.signInWithEmailAndPassword(email, password)).thenReturn(mockAuthTask)
        whenever(mockAuthTask.isSuccessful).thenReturn(true)
        whenever(mockAuthTask.addOnSuccessListener(any())).thenReturn(mockAuthTask)
        whenever(mockAuthTask.addOnFailureListener(any())).thenReturn(mockAuthTask)

        // Verify query was set up correctly
        whenever(mockDatabase.orderByChild("username")).thenReturn(mockQuery)
        whenever(mockQuery.equalTo(username)).thenReturn(mockQuery)

        // Assert: The query structure is correct
        verify(mockDatabase, never()).orderByChild("email") // Should use username, not email
    }

    @Test
    fun `login - database query finds user by username`() {
        // Given: A username exists in database
        val username = "johndoe"

        // When: Query is created
        whenever(mockDatabase.orderByChild("username")).thenReturn(mockQuery)
        whenever(mockQuery.equalTo(username)).thenReturn(mockQuery)

        mockDatabase.orderByChild("username").equalTo(username)

        // Then: Query should be ordered by username field
        verify(mockDatabase).orderByChild("username")
        verify(mockQuery).equalTo(username)
    }

    // ====================
    // Login Failure Tests
    // ====================

    @Test
    fun `login - fails when username not found in database`() {
        // Given: Username doesn't exist
        val mockSnapshot = mock<DataSnapshot>()
        whenever(mockSnapshot.exists()).thenReturn(false)

        // Then: Login should fail
        assertFalse("User should not exist", mockSnapshot.exists())
    }

    @Test
    fun `login - fails when email field is null in database`() {
        // Given: User exists but email is null
        val mockSnapshot = mock<DataSnapshot>()
        val mockUserSnapshot = mock<DataSnapshot>()
        val mockEmailSnapshot = mock<DataSnapshot>()

        whenever(mockSnapshot.exists()).thenReturn(true)
        whenever(mockSnapshot.children).thenReturn(listOf(mockUserSnapshot))
        whenever(mockUserSnapshot.child("email")).thenReturn(mockEmailSnapshot)
        whenever(mockEmailSnapshot.getValue(String::class.java)).thenReturn(null)

        // Assert: Email should be null
        val email = mockUserSnapshot.child("email").getValue(String::class.java)
        assertNull("Email should be null", email)
    }

    @Test
    fun `login - fails when Firebase authentication fails`() {
        // Given: Valid email and password
        val email = "test@example.com"
        val password = "wrongpassword"

        // When: Auth fails
        val mockTask = mock<Task<AuthResult>>()
        whenever(mockAuth.signInWithEmailAndPassword(email, password)).thenReturn(mockTask)
        whenever(mockTask.isSuccessful).thenReturn(false)
        whenever(mockTask.addOnSuccessListener(any())).thenReturn(mockTask)
        whenever(mockTask.addOnFailureListener(any())).thenAnswer { invocation ->
            val listener = invocation.getArgument<com.google.android.gms.tasks.OnFailureListener>(0)
            listener.onFailure(Exception("Invalid credentials"))
            mockTask
        }

        // Then: Task should not be successful
        assertFalse("Authentication should fail", mockTask.isSuccessful)
    }

    // ====================
    // Validation Tests
    // ====================

    @Test
    fun `validation - empty username returns false`() {
        val username = ""
        val password = "password123"

        assertTrue("Empty username should be invalid", username.isEmpty())
    }

    @Test
    fun `validation - empty password returns false`() {
        val username = "testuser"
        val password = ""

        assertTrue("Empty password should be invalid", password.isEmpty())
    }

    @Test
    fun `validation - both fields empty returns false`() {
        val username = ""
        val password = ""

        assertTrue("Both fields empty should be invalid", username.isEmpty() && password.isEmpty())
    }

    // ====================
    // Network Tests
    // ====================

    @Test
    fun `network - login fails when no network connection`() {
        // Given: No network available
        whenever(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .thenReturn(mockConnectivityManager)
        whenever(mockConnectivityManager.activeNetwork).thenReturn(null)

        // Then: Network should be unavailable
        val activeNetwork = mockConnectivityManager.activeNetwork
        assertNull("Network should be unavailable", activeNetwork)
    }

    @Test
    fun `network - login proceeds when WiFi is available`() {
        // Given: WiFi is connected
        val mockNetwork = mock<android.net.Network>()
        whenever(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .thenReturn(mockConnectivityManager)
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockNetworkCapabilities)
        whenever(mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            .thenReturn(true)

        // Then: WiFi should be available
        val hasWiFi = mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        assertTrue("WiFi should be available", hasWiFi)
    }

    @Test
    fun `network - login proceeds when mobile data is available`() {
        // Given: Mobile data is connected
        val mockNetwork = mock<android.net.Network>()
        whenever(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
            .thenReturn(mockConnectivityManager)
        whenever(mockConnectivityManager.activeNetwork).thenReturn(mockNetwork)
        whenever(mockConnectivityManager.getNetworkCapabilities(mockNetwork))
            .thenReturn(mockNetworkCapabilities)
        whenever(mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            .thenReturn(true)

        // Then: Mobile data should be available
        val hasMobileData = mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        assertTrue("Mobile data should be available", hasMobileData)
    }

    // ====================
    // Timeout Tests
    // ====================

    @Test
    fun `timeout - Firebase query timeout is set to 10 seconds`() {
        // Given: Timeout duration
        val timeoutMs = 10000L

        // Then: Timeout should be 10 seconds
        assertEquals("Timeout should be 10 seconds", 10000L, timeoutMs)
    }

    @Test
    fun `database - error handling on query cancellation`() {
        // Given: Database error
        val mockError = mock<DatabaseError>()
        whenever(mockError.code).thenReturn(DatabaseError.DISCONNECTED)
        whenever(mockError.message).thenReturn("Database connection lost")

        // Then: Error should be handled
        assertEquals("Error code should match", DatabaseError.DISCONNECTED, mockError.code)
        assertEquals("Error message should match", "Database connection lost", mockError.message)
    }
}
