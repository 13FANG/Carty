package com.shah.carty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false
)

class LoginViewModel(
    private val repository: CartyRepository,
    private val firebaseAuth: FirebaseAuth,
    private val application: CartyApplication
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun loginUser() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        if (email.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Email и пароль не должны быть пустыми.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                firebaseAuth.signInWithEmailAndPassword(email, password).await()
                _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
            } catch (e: FirebaseAuthInvalidUserException) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Пользователь с таким Email не найден.") }
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Неверный пароль.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Ошибка входа: ${e.localizedMessage}") }
            }
        }
    }

    fun resetPassword(email: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            onFailure("Введите корректный Email.")
            return
        }
        viewModelScope.launch {
            try {
                firebaseAuth.sendPasswordResetEmail(email).await()
                onSuccess()
            } catch (e: Exception) {
                onFailure("Ошибка: ${e.localizedMessage}")
            }
        }
    }

    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun consumeLoginSuccessEvent() {
        _uiState.update { it.copy(loginSuccess = false) }
    }
}