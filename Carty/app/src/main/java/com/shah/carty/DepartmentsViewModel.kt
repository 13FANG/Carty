package com.shah.carty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DepartmentsUiState(
    val departmentList: List<Department> = listOf(),
    val departmentNameToAdd: String = ""
)

class DepartmentsViewModel(
    private val repository: CartyRepository,
    private val application: CartyApplication
) : ViewModel() {

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
                val ownerId = application.getCurrentUserId()
                val currentDepartments = uiState.value.departmentList
                val nextSortIndex = (currentDepartments.maxOfOrNull { it.manualSortIndex } ?: -1) + 1

                val newDepartment = Department(
                    departmentName = departmentName.trim(),
                    ownerId = ownerId,
                    manualSortIndex = nextSortIndex
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

    fun updateDepartmentsOrder(orderedDepartmentsFromAdapter: List<Department>) {
        viewModelScope.launch {
            val originalDepartmentsFromStateById = uiState.value.departmentList.associateBy { it.departmentId }
            var orderActuallyChanged = false

            val departmentsToPersist = orderedDepartmentsFromAdapter.mapIndexedNotNull { newIndex, deptFromAdapter ->
                val originalDept = originalDepartmentsFromStateById[deptFromAdapter.departmentId]
                if (originalDept != null) {
                    if (originalDept.manualSortIndex != newIndex) {
                        orderActuallyChanged = true
                        deptFromAdapter.copy(manualSortIndex = newIndex)
                    } else {
                        deptFromAdapter
                    }
                } else {
                    orderActuallyChanged = true
                    deptFromAdapter.copy(manualSortIndex = newIndex)
                }
            }
            if (orderedDepartmentsFromAdapter.size != uiState.value.departmentList.size) {
                orderActuallyChanged = true
            } else {
                val idsFromAdapter = orderedDepartmentsFromAdapter.map { it.departmentId }.toSet()
                val idsFromState = uiState.value.departmentList.map { it.departmentId }.toSet()
                if (idsFromAdapter != idsFromState) {
                    orderActuallyChanged = true
                }
            }

            if (orderActuallyChanged) {
                repository.updateDepartments(departmentsToPersist)
            }
        }
    }
}