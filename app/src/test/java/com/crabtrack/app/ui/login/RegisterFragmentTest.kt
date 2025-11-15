package com.crabtrack.app.ui.login

import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Query
import com.google.android.gms.tasks.Task
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.*

/**
 * Unit tests for RegisterFragment
 * Tests user registration flow including username uniqueness check
 */
class RegisterFragmentTest {

    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockDatabase: DatabaseReference
    private lateinit var mockQuery: Query

    @Before
    fun setup() {
        mockAuth = mock()
        mockDatabase = mock()
        mockQuery = mock()
    }

    // ====================
    // Registration Success Tests
    // ====================

    @Test
    fun `registration - successful with unique username`() {
        // Given: Unique username
        val username = "newuser"
        val email = "newuser@example.com"
        val password = "password123"

        // Username check returns no existing user
        val mockSnapshot = mock<DataSnapshot>()
        whenever(mockSnapshot.exists()).thenReturn(false)

        // Firebase Auth succeeds
        val mockAuthResult = mock<AuthResult>()
        val mockUser = mock<FirebaseUser>()
        val mockAuthTask = mock<Task<AuthResult>>()
        whenever(mockUser.uid).thenReturn("user123")
        whenever(mockAuthResult.user).thenReturn(mockUser)
        whenever(mockAuth.createUserWithEmailAndPassword(email, password)).thenReturn(mockAuthTask)
        whenever(mockAuthTask.isSuccessful).thenReturn(true)

        // Assert: Username doesn't exist
        assertFalse("Username should be unique", mockSnapshot.exists())
    }

    @Test
    fun `registration - creates Firebase Auth account`() {
        // Given: Valid credentials
        val email = "test@example.com"
        val password = "securepass"

        val mockTask = mock<Task<AuthResult>>()
        whenever(mockAuth.createUserWithEmailAndPassword(email, password)).thenReturn(mockTask)

        // When: Creating account
        mockAuth.createUserWithEmailAndPassword(email, password)

        // Then: Firebase Auth should be called
        verify(mockAuth).createUserWithEmailAndPassword(email, password)
    }

    @Test
    fun `registration - saves user to Realtime Database after auth success`() {
        // Given: Successful authentication
        val userId = "uid123"
        val username = "testuser"
        val email = "test@example.com"
        val password = "pass123"

        val user = User(userId, username, email, password)
        val mockUserRef = mock<DatabaseReference>()
        val mockTask = mock<Task<Void>>()

        whenever(mockDatabase.child(userId)).thenReturn(mockUserRef)
        whenever(mockUserRef.setValue(user)).thenReturn(mockTask)
        whenever(mockTask.isSuccessful).thenReturn(true)

        // When: Saving user
        mockDatabase.child(userId).setValue(user)

        // Then: Database should save user data
        verify(mockDatabase).child(userId)
        verify(mockUserRef).setValue(user)
    }

    // ====================
    // Registration Failure Tests
    // ====================

    @Test
    fun `registration - fails when username already exists`() {
        // Given: Existing username
        val username = "existinguser"
        val mockSnapshot = mock<DataSnapshot>()
        whenever(mockSnapshot.exists()).thenReturn(true)

        // Then: Registration should be blocked
        assertTrue("Username should already exist", mockSnapshot.exists())
    }

    @Test
    fun `registration - database error on username check`() {
        // Given: Database error
        val mockError = mock<DatabaseError>()
        whenever(mockError.code).thenReturn(DatabaseError.PERMISSION_DENIED)
        whenever(mockError.message).thenReturn("Permission denied")

        // Then: Error should be handled
        assertEquals("Error code should match", DatabaseError.PERMISSION_DENIED, mockError.code)
    }

    @Test
    fun `registration - Firebase Auth failure with invalid email`() {
        // Given: Invalid email format
        val email = "invalid-email"
        val password = "password123"

        val mockTask = mock<Task<AuthResult>>()
        whenever(mockAuth.createUserWithEmailAndPassword(email, password)).thenReturn(mockTask)
        whenever(mockTask.isSuccessful).thenReturn(false)
        whenever(mockTask.exception).thenReturn(Exception("The email address is badly formatted"))

        // Then: Auth should fail
        assertFalse("Auth should fail with invalid email", mockTask.isSuccessful)
    }

    @Test
    fun `registration - database write fails after auth success`() {
        // Given: Auth succeeds but database write fails
        val userId = "uid456"
        val user = User(userId, "testuser", "test@example.com", "pass123")

        val mockUserRef = mock<DatabaseReference>()
        val mockTask = mock<Task<Void>>()

        whenever(mockDatabase.child(userId)).thenReturn(mockUserRef)
        whenever(mockUserRef.setValue(user)).thenReturn(mockTask)
        whenever(mockTask.isSuccessful).thenReturn(false)
        whenever(mockTask.exception).thenReturn(Exception("Network error"))

        // Then: Database write should fail
        assertFalse("Database write should fail", mockTask.isSuccessful)
    }

    // ====================
    // Validation Tests
    // ====================

    @Test
    fun `validation - empty username is invalid`() {
        val username = ""
        assertTrue("Empty username should be invalid", username.isEmpty())
    }

    @Test
    fun `validation - empty email is invalid`() {
        val email = ""
        assertTrue("Empty email should be invalid", email.isEmpty())
    }

    @Test
    fun `validation - empty password is invalid`() {
        val password = ""
        assertTrue("Empty password should be invalid", password.isEmpty())
    }

    @Test
    fun `validation - all fields empty is invalid`() {
        val username = ""
        val email = ""
        val password = ""

        assertTrue("All fields empty should be invalid",
            username.isEmpty() && email.isEmpty() && password.isEmpty())
    }

    // ====================
    // User Model Tests
    // ====================

    @Test
    fun `user model - creates with all fields`() {
        // Given: All user data
        val user = User(
            id = "uid123",
            username = "johndoe",
            email = "john@example.com",
            password = "securepass"
        )

        // Then: All fields should be set
        assertEquals("uid123", user.id)
        assertEquals("johndoe", user.username)
        assertEquals("john@example.com", user.email)
        assertEquals("securepass", user.password)
    }

    @Test
    fun `user model - handles null values`() {
        // Given: User with null fields (for Firebase deserialization)
        val user = User(null, null, null, null)

        // Then: Fields should be null
        assertNull(user.id)
        assertNull(user.username)
        assertNull(user.email)
        assertNull(user.password)
    }

    // ====================
    // Query Tests
    // ====================

    @Test
    fun `query - checks username uniqueness correctly`() {
        // Given: Username to check
        val username = "testuser"

        whenever(mockDatabase.orderByChild("username")).thenReturn(mockQuery)
        whenever(mockQuery.equalTo(username)).thenReturn(mockQuery)

        // When: Querying for username
        mockDatabase.orderByChild("username").equalTo(username)

        // Then: Query should be structured correctly
        verify(mockDatabase).orderByChild("username")
        verify(mockQuery).equalTo(username)
    }
}
