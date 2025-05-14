package com.shah.carty

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shah.carty.CartyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth


data class ViewShoppingListUiState(
    val currentList: ShoppingList? = null,
    val itemsInList: List<ShoppingListItem> = emptyList(),
    val isLoading: Boolean = true,
    val listNotFound: Boolean = false,
    val productToAdd: Product? = null
)

class ViewShoppingListViewModel(
    private val repository: CartyRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewShoppingListUiState())
    val uiState: StateFlow<ViewShoppingListUiState> = _uiState.asStateFlow()

    private val shoppingListId: Long = savedStateHandle.get<Long>("shoppingListId")!!

    init {
        loadListDetails()
    }

    private fun loadListDetails() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            repository.getShoppingListById(shoppingListId).collectLatest { list ->
                if (list == null) {
                    _uiState.update { it.copy(isLoading = false, listNotFound = true) }
                } else {
                    _uiState.update { it.copy(currentList = list) }
                    repository.getItemsForList(shoppingListId).collectLatest { items ->
                        _uiState.update { currentState ->
                            currentState.copy(
                                itemsInList = items,
                                isLoading = false,
                                listNotFound = false
                            )
                        }
                    }
                }
            }
        }
    }

    fun updateListName(newName: String) {
        _uiState.value.currentList?.let { list ->
            val trimmedName = newName.trim()
            if (list.shoppingListName != trimmedName && trimmedName.isNotBlank()) {
                viewModelScope.launch {
                    val updatedList = list.copy(
                        shoppingListName = trimmedName,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateShoppingList(updatedList)
                }
            }
        }
    }

    fun toggleFavoriteStatus() {
        _uiState.value.currentList?.let { list ->
            viewModelScope.launch {
                val updatedList = list.copy(
                    isFavorite = !list.isFavorite,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateShoppingList(updatedList)
            }
        }
    }

    fun completeShoppingList() {
        _uiState.value.currentList?.let { list ->
            if (!list.isCompleted) {
                viewModelScope.launch {
                    val updatedList = list.copy(
                        isCompleted = true,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateShoppingList(updatedList)
                }
            }
        }
    }

    fun deleteFullShoppingList(callback: () -> Unit) {
        _uiState.value.currentList?.let { list ->
            viewModelScope.launch {
                repository.deleteShoppingListItemsById(list.shoppingListId)
                repository.deleteShoppingList(list)
                callback()
            }
        }
    }

    fun updateShoppingListItemBoughtStatus(item: ShoppingListItem, isBought: Boolean) {
        viewModelScope.launch {
            val updatedItem = item.copy(isBought = isBought)
            repository.updateShoppingListItem(updatedItem)
        }
    }

    fun deleteShoppingListItem(item: ShoppingListItem) {
        viewModelScope.launch {
            repository.deleteShoppingListItem(item)
        }
    }

    fun loadProductToAdd(productId: Long) {
        viewModelScope.launch {
            val product = repository.getProductById(productId).first()
            _uiState.update { it.copy(productToAdd = product) }
        }
    }

    fun clearProductToAdd() {
        _uiState.update { it.copy(productToAdd = null) }
    }

    fun confirmAddProductToList(quantity: Double, price: Double?, unit: ProductUnit) {
        val product = _uiState.value.productToAdd
        val listId = _uiState.value.currentList?.shoppingListId

        if (product != null && listId != null) {
            viewModelScope.launch {
                val newItem = ShoppingListItem(
                    shoppingListId = listId,
                    productId = product.productId,
                    productName = product.productName,
                    quantity = if (quantity > 0) quantity else 1.0,
                    unit = unit,
                    price = price ?: product.defaultPrice,
                    isBought = false,
                    departmentIdAtPurchase = product.departmentId,
                    manualSortOrder = (_uiState.value.itemsInList.maxOfOrNull { it.manualSortOrder } ?: -1) + 1,
                    ownerId = "",
                    firestoreId = ""
                )
                repository.addShoppingListItem(newItem)
                clearProductToAdd()
            }
        }
    }
}