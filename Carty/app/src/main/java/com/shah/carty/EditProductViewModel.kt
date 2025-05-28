package com.shah.carty

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductUnitDisplay(val unit: ProductUnit, val displayName: String)

data class EditProductUiState(
    val productName: String = "",
    val selectedDepartmentId: Long? = null,
    val selectedUnit: ProductUnit = ProductUnit.PIECE,
    val defaultPrice: String = "",

    val allDepartments: List<Department> = emptyList(),
    val allUnitsDisplay: List<ProductUnitDisplay> = emptyList(),

    val isEditing: Boolean = false,
    val productId: Long = 0L,
    val isLoading: Boolean = true,
    val saveButtonEnabled: Boolean = false,
    val productNotFound: Boolean = false
)

class EditProductViewModel(
    private val repository: CartyRepository,
    private val savedStateHandle: SavedStateHandle,
    private val stringProvider: (Int) -> String,
    private val application: CartyApplication
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProductUiState())
    val uiState: StateFlow<EditProductUiState> = _uiState.asStateFlow()

    private val productIdArg: Long = savedStateHandle.get<Long>("productId") ?: 0L

    private val unitDisplayMap: Map<ProductUnit, String> by lazy {
        mapOf(
            ProductUnit.PIECE to stringProvider(R.string.unit_piece),
            ProductUnit.KILOGRAM to stringProvider(R.string.unit_kilogram),
            ProductUnit.GRAM to stringProvider(R.string.unit_gram),
            ProductUnit.LITER to stringProvider(R.string.unit_liter),
            ProductUnit.MILLILITER to stringProvider(R.string.unit_milliliter),
            ProductUnit.PACKAGE to stringProvider(R.string.unit_package)
        )
    }

    init {
        val initialUnitsDisplay = ProductUnit.values().map { ProductUnitDisplay(it, unitDisplayMap[it] ?: it.name) }
        _uiState.update { it.copy(isLoading = true, allUnitsDisplay = initialUnitsDisplay) }

        viewModelScope.launch {
            val departmentsFlow = repository.getAllDepartmentsList()

            if (productIdArg != 0L) {
                val productFlow = repository.getProductById(productIdArg)

                combine(departmentsFlow, productFlow) { departments, product ->
                    product?.let { p ->
                        EditProductUiState(
                            productName = p.productName,
                            selectedDepartmentId = p.departmentId,
                            selectedUnit = p.defaultUnit,
                            defaultPrice = p.defaultPrice?.toString() ?: "",
                            allDepartments = departments,
                            allUnitsDisplay = initialUnitsDisplay,
                            isEditing = true,
                            productId = p.productId,
                            isLoading = false,
                            saveButtonEnabled = p.productName.isNotBlank(),
                            productNotFound = false
                        )
                    } ?: EditProductUiState(
                        allDepartments = departments,
                        allUnitsDisplay = initialUnitsDisplay,
                        isLoading = false,
                        productNotFound = true
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Lazily,
                    initialValue = _uiState.value.copy(isLoading = true)
                ).collect { combinedState ->
                    _uiState.value = combinedState
                }
            } else {
                departmentsFlow.first().let { departments ->
                    _uiState.update {
                        it.copy(
                            allDepartments = departments,
                            isLoading = false,
                            saveButtonEnabled = it.productName.isNotBlank()
                        )
                    }
                }
            }
        }
    }

    fun updateProductName(name: String) {
        _uiState.update {
            it.copy(productName = name, saveButtonEnabled = name.isNotBlank())
        }
    }

    fun updateSelectedDepartment(department: Department?) {
        _uiState.update {
            it.copy(selectedDepartmentId = department?.departmentId)
        }
    }

    fun updateSelectedUnit(unitDisplay: ProductUnitDisplay) {
        _uiState.update {
            it.copy(selectedUnit = unitDisplay.unit)
        }
    }

    fun updateDefaultPrice(price: String) {
        _uiState.update {
            it.copy(defaultPrice = price)
        }
    }

    fun saveProduct() {
        val current = _uiState.value
        if (!current.saveButtonEnabled || current.productName.isBlank()) return

        val ownerId = application.getCurrentUserId()

        viewModelScope.launch {
            val priceDouble = current.defaultPrice.toDoubleOrNull()
            val productToSave = Product(
                productId = if (current.isEditing) current.productId else 0L,
                productName = current.productName.trim(),
                departmentId = current.selectedDepartmentId,
                defaultUnit = current.selectedUnit,
                defaultPrice = priceDouble,
                ownerId = ownerId
            )
            if (current.isEditing) {
                repository.updateProduct(productToSave)
            } else {
                repository.addProduct(productToSave)
            }
        }
    }

    fun refreshDepartments() {
        viewModelScope.launch {
            val departments = repository.getAllDepartmentsList().first()
            _uiState.update { it.copy(allDepartments = departments) }
        }
    }
}