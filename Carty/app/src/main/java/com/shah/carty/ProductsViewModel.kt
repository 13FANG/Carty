package com.shah.carty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shah.carty.CartyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProductsUiState(
    val productList: List<Product> = listOf(),
    val departmentMap: Map<Long, String> = emptyMap()
)

class ProductsViewModel(private val repository: CartyRepository) : ViewModel() {

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
        return departmentId?.let { uiState.value.departmentMap[it] } ?: "Без отдела"
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            // Логика удаления товара из активных списков покупок (ТЗ 3.1.2) - СЛОЖНАЯ, пока отложим для скорости
            // и обнуления departmentId у товаров (ТЗ 3.1.3 - это для удаления отдела, не товара)
        }
    }
}