package com.shah.carty

import app.cash.turbine.test
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class LoginViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: LoginViewModel
    private lateinit var mockRepository: CartyRepository
    private lateinit var mockFirebaseAuth: FirebaseAuth

    @Test
    fun `loginUser with valid credentials should set loginSuccess to true`() = runTest {
        mockRepository = mockk(relaxed = true)
        mockFirebaseAuth = mockk()

        val mockAuthResultTask = mockk<Task<AuthResult>>(relaxed = true)
        every { mockAuthResultTask.isSuccessful } returns true

        coEvery { mockFirebaseAuth.signInWithEmailAndPassword(any(), any()) } coAnswers {
            val taskCompletionSource = com.google.android.gms.tasks.TaskCompletionSource<AuthResult>()
            taskCompletionSource.setResult(mockk(relaxed = true))
            taskCompletionSource.task
        }


        viewModel = LoginViewModel(mockRepository, mockFirebaseAuth)
        viewModel.updateEmail("test@example.com")
        viewModel.updatePassword("password")

        viewModel.loginUser()

        viewModel.uiState.test {
            var finalState: LoginUiState? = null
            for (i in 1..3) {
                finalState = awaitItem()
                if (finalState.loginSuccess) break
            }
            assertNotNull(finalState)
            assertTrue(finalState!!.loginSuccess)
            assertNull(finalState.errorMessage)
            assertFalse(finalState.isLoading)
            cancelAndConsumeRemainingEvents()
        }
    }
}