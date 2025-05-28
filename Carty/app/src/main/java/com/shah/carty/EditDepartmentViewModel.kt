package com.shah.carty

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditDepartmentUiState(
    val departmentName: String = "",
    val isEditing: Boolean = false,
    val departmentId: Long = 0L,
    val isLoading: Boolean = false,
    val saveButtonEnabled: Boolean = false,
    val departmentNotFound: Boolean = false
)

class EditDepartmentViewModel(
    private val repository: CartyRepository,
    private val savedStateHandle: SavedStateHandle,
    private val application: CartyApplication
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditDepartmentUiState())
    val uiState: StateFlow<EditDepartmentUiState> = _uiState.asStateFlow()

    private val departmentIdArg: Long = savedStateHandle.get<Long>("departmentId") ?: 0L

    init {
        if (departmentIdArg != 0L) {
            _uiState.update { it.copy(isLoading = true, isEditing = true, departmentId = departmentIdArg) }
            viewModelScope.launch {
                val department = repository.getDepartmentById(departmentIdArg).firstOrNull()
                department?.let {
                    _uiState.update { currentState ->
                        currentState.copy(
                            departmentName = it.departmentName,
                            isLoading = false,
                            saveButtonEnabled = it.departmentName.isNotBlank(),
                            departmentNotFound = false
                        )
                    }
                } ?: run {
                    _uiState.update { it.copy(isLoading = false, departmentNotFound = true) }
                }
            }
        } else {
            _uiState.update { it.copy(isLoading = false, isEditing = false, saveButtonEnabled = false) }
        }
    }

    fun updateDepartmentName(name: String) {
        _uiState.update {
            it.copy(
                departmentName = name,
                saveButtonEnabled = name.isNotBlank()
            )
        }
    }

    fun saveDepartment(callback: (Boolean) -> Unit) {
        val currentUiState = _uiState.value
        if (!currentUiState.saveButtonEnabled || currentUiState.departmentName.isBlank()) {
            callback(false)
            return
        }
        val ownerId = application.getCurrentUserId()

        viewModelScope.launch {
            val departmentToSave = Department(
                departmentId = if (currentUiState.isEditing) currentUiState.departmentId else 0L,
                departmentName = currentUiState.departmentName.trim(),
                ownerId = ownerId
            )

            try {
                if (currentUiState.isEditing) {
                    repository.updateDepartment(departmentToSave)
                } else {
                    repository.addDepartment(departmentToSave)
                }
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }
    }
}