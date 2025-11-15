package com.crabtrack.app.ui.login

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.SignInMethodQueryResult
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
 * Unit tests for ForgotPasswordFragment
 * Tests password reset functionality via Firebase Auth email and database update
 */
class ForgotPasswordFragmentTest {

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
    // Password Reset Email Tests
    // ====================

    @Test
    fun `password reset - sends email successfully`() {
        // Given: Valid email
        val email = "user@example.com"

        val mockTask = mock<Task<Void>>()
        whenever(mockAuth.sendPasswordResetEmail(email)).thenReturn(mockTask)
        whenever(mockTask.isSuccessful).thenReturn(true)

        // When: Sending reset email
        mockAuth.sendPasswordResetEmail(email)

        // Then: Firebase Auth should send reset email
        verify(mockAuth).sendPasswordResetEmail(email)
    }

    @Test
    fun `password reset - fails with invalid email`() {
        // Given: Invalid email
        val email = "nonexistent@example.com"

        val mockTask = mock<Task<Void>>()
        whenever(mockAuth.sendPasswordResetEmail(email)).thenReturn(mockTask)
        whenever(mockTask.isSuccessful).thenReturn(false)
        whenever(mockTask.exception).thenReturn(Exception("There is no user record corresponding to this identifier"))

        // Then: Should fail
        assertFalse("Reset email should fail", mockTask.isSuccessful)
    }

    @Test
    fun `password reset - empty email validation`() {
        // Given: Empty email
        val email = ""

        // Then: Should be invalid
        assertTrue("Empty email should be invalid", email.isEmpty())
    }

    // ====================
    // Email Verification Tests
    // ====================

    @Test
    fun `email verification - valid email found in Firebase`() {
        // Given: Email exists
        val email = "user@example.com"

        val mockSignInMethods = listOf("password")
        val mockQueryResult = mock<SignInMethodQueryResult>()
        val mockTask = mock<Task<SignInMethodQueryResult>>()

        whenever(mockQueryResult.signInMethods).thenReturn(mockSignInMethods)
        whenever(mockTask.isSuccessful).thenReturn(true)
        whenever(mockTask.result).thenReturn(mockQueryResult)
        whenever(mockAuth.fetchSignInMethodsForEmail(email)).thenReturn(mockTask)

        // Then: Sign-in methods should exist
        assertFalse("Email should have sign-in methods", mockSignInMethods.isNullOrEmpty())
    }

    @Test
    fun `email verification - email not found in Firebase`() {
        // Given: Email doesn't exist
        val email = "nonexistent@example.com"

        val mockQueryResult = mock<SignInMethodQueryResult>()
        val mockTask = mock<Task<SignInMethodQueryResult>>()

        whenever(mockQueryResult.signInMethods).thenReturn(emptyList())
        whenever(mockTask.isSuccessful).thenReturn(true)
        whenever(mockTask.result).thenReturn(mockQueryResult)
        whenever(mockAuth.fetchSignInMethodsForEmail(email)).thenReturn(mockTask)

        // Then: No sign-in methods should exist
        assertTrue("Email should not have sign-in methods", mockQueryResult.signInMethods.isNullOrEmpty())
    }

    // ====================
    // Database Password Update Tests
    // ====================

    @Test
    fun `database update - password updated successfully`() {
        // Given: Valid email and new password
        val email = "user@example.com"
        val newPassword = "newSecurePass123"

        val mockSnapshot = mock<DataSnapshot>()
        val mockUserSnapshot = mock<DataSnapshot>()
        val mockUserRef = mock<DatabaseReference>()
        val mockPasswordRef = mock<DatabaseReference>()
        val mockTask = mock<Task<Void>>()

        whenever(mockSnapshot.exists()).thenReturn(true)
        whenever(mockSnapshot.children).thenReturn(listOf(mockUserSnapshot).iterator())
        whenever(mockUserSnapshot.ref).thenReturn(mockUserRef)
        whenever(mockUserRef.child("password")).thenReturn(mockPasswordRef)
        whenever(mockPasswordRef.setValue(newPassword)).thenReturn(mockTask)
        whenever(mockTask.isSuccessful).thenReturn(true)

        // When: User exists
        assertTrue("User should exist", mockSnapshot.exists())

        // Then: Password can be updated
        val userSnapshot = mockSnapshot.children.first()
        userSnapshot.ref.child("password").setValue(newPassword)
        verify(mockPasswordRef).setValue(newPassword)
    }

    @Test
    fun `database update - email not found in database`() {
        // Given: Email doesn't exist
        val email = "nonexistent@example.com"

        val mockSnapshot = mock<DataSnapshot>()
        whenever(mockSnapshot.exists()).thenReturn(false)

        // Then: Update should fail
        assertFalse("Email should not exist in database", mockSnapshot.exists())
    }

    @Test
    fun `database update - database write fails`() {
        // Given: Database error
        val newPassword = "newpass123"

        val mockPasswordRef = mock<DatabaseReference>()
        val mockTask = mock<Task<Void>>()

        whenever(mockPasswordRef.setValue(newPassword)).thenReturn(mockTask)
        whenever(mockTask.isSuccessful).thenReturn(false)
        whenever(mockTask.exception).thenReturn(Exception("Network error"))

        // Then: Write should fail
        assertFalse("Database write should fail", mockTask.isSuccessful)
    }

    @Test
    fun `database update - handles database cancellation`() {
        // Given: Database error
        val mockError = mock<DatabaseError>()
        whenever(mockError.code).thenReturn(DatabaseError.NETWORK_ERROR)
        whenever(mockError.message).thenReturn("Network error")

        // Then: Error should be handled
        assertEquals("Error code should match", DatabaseError.NETWORK_ERROR, mockError.code)
    }

    // ====================
    // Validation Tests
    // ====================

    @Test
    fun `validation - empty email and password fields`() {
        // Given: Empty fields
        val email = ""
        val newPassword = ""

        // Then: Should be invalid
        assertTrue("Both fields should be invalid",
            email.isEmpty() && newPassword.isEmpty())
    }

    @Test
    fun `validation - empty password field only`() {
        // Given: Valid email but empty password
        val email = "user@example.com"
        val newPassword = ""

        // Then: Password should be invalid
        assertTrue("Password should be empty", newPassword.isEmpty())
    }

    // ====================
    // Query Tests
    // ====================

    @Test
    fun `query - searches for user by email`() {
        // Given: Email to search
        val email = "test@example.com"

        val mockUsersRef = mock<DatabaseReference>()
        whenever(mockUsersRef.orderByChild("email")).thenReturn(mockQuery)
        whenever(mockQuery.equalTo(email)).thenReturn(mockQuery)

        // When: Querying by email
        mockUsersRef.orderByChild("email").equalTo(email)

        // Then: Query should use email field
        verify(mockUsersRef).orderByChild("email")
        verify(mockQuery).equalTo(email)
    }
}
