package com.shah.carty

import com.shah.carty.Department
import com.shah.carty.CartyRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DepartmentsUiState(
    val departmentList: List<Department> = listOf(),
    val departmentNameToAdd: String = ""
)

class DepartmentsViewModel(private val repository: CartyRepository) : ViewModel() {

    val uiState: StateFlow<DepartmentsUiState> =
        repository.getAllDepartmentsList()
            .map { departments -> DepartmentsUiState(departmentList = departments) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = DepartmentsUiState()
            )

    fun addDepartment(departmentName: String) {
        viewModelScope.launch {
            if (departmentName.isNotBlank()) {
                val ownerIdPlaceholder = "user_guest_or_id" // ЗАГЛУШКА
                val newDepartment = Department(
                    departmentName = departmentName.trim(),
                    ownerId = ownerIdPlaceholder
                )
                repository.addDepartment(newDepartment)
            }
        }
    }

    fun deleteDepartment(department: Department) {
        viewModelScope.launch {
            repository.deleteDepartment(department)
        }
    }
}