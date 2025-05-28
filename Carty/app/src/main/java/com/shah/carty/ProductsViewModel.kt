package com.shah.carty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProductsUiState(
    val productList: List<Product> = listOf(),
    val departmentMap: Map<Long, String> = emptyMap()
)

class ProductsViewModel(
    private val repository: CartyRepository,
    private val application: CartyApplication
) : ViewModel() {

    val uiState: StateFlow<ProductsUiState> =
        combine(
            repository.getAllProductsList(),
            repository.getAllDepartmentsList()
        ) { products, departments ->
            val departmentMap = departments.associateBy({ it.departmentId }, { it.departmentName })
            ProductsUiState(productList = products, departmentMap = departmentMap)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ProductsUiState()
        )

    fun getDepartmentNameById(departmentId: Long?): String {
        return departmentId?.let { uiState.value.departmentMap[it] } ?: application.getString(R.string.no_department_selected)
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
        }
    }
}