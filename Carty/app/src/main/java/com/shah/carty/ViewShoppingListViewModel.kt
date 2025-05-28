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


data class ViewShoppingListUiState(
    val currentList: ShoppingList? = null,
    val itemsInList: List<ShoppingListItem> = emptyList(),
    val isLoading: Boolean = true,
    val listNotFound: Boolean = false,
    val productToAdd: Product? = null
)

class ViewShoppingListViewModel(
    private val repository: CartyRepository,
    savedStateHandle: SavedStateHandle,
    private val application: CartyApplication
) : ViewModel() {

    private val shoppingListId: Long = savedStateHandle.get<Long>("shoppingListId")!!
    private val _productToAddStateFlow = MutableStateFlow<Product?>(null)

    val uiState: StateFlow<ViewShoppingListUiState> = combine(
        repository.getShoppingListById(shoppingListId),
        repository.getItemsForList(shoppingListId),
        _productToAddStateFlow.asStateFlow()
    ) { list, items, productToAdd ->
        ViewShoppingListUiState(
            currentList = list,
            itemsInList = items,
            isLoading = false,
            listNotFound = (list == null && items.any { it.shoppingListId == shoppingListId }),
            productToAdd = productToAdd
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ViewShoppingListUiState(isLoading = true)
    )


    fun updateShoppingListItemsOrder(orderedItems: List<ShoppingListItem>) {
        viewModelScope.launch {
            val itemsToUpdate = mutableListOf<ShoppingListItem>()
            val currentSnapshot = uiState.value.itemsInList

            orderedItems.forEachIndexed { index, newItemOrderVersion ->
                val oldVersionInState = currentSnapshot.find { it.shoppingListItemId == newItemOrderVersion.shoppingListItemId }

                if (oldVersionInState != null) {
                    if (oldVersionInState.manualSortOrder != index ||
                        currentSnapshot.getOrNull(index)?.shoppingListItemId != newItemOrderVersion.shoppingListItemId) {
                        itemsToUpdate.add(newItemOrderVersion.copy(manualSortOrder = index))
                    }
                } else {
                    itemsToUpdate.add(newItemOrderVersion.copy(manualSortOrder = index))
                }
            }

            if (itemsToUpdate.isEmpty() && currentSnapshot.size == orderedItems.size) {
                var orderChanged = false
                for(i in orderedItems.indices) {
                    if(currentSnapshot.getOrNull(i)?.shoppingListItemId != orderedItems.getOrNull(i)?.shoppingListItemId) {
                        orderChanged = true
                        break
                    }
                }
                if(orderChanged) {
                    orderedItems.forEachIndexed{ index, item ->
                        itemsToUpdate.add(item.copy(manualSortOrder = index))
                    }
                }
            }

            if (itemsToUpdate.isNotEmpty()) {
                itemsToUpdate.forEach { repository.updateShoppingListItem(it) }
                uiState.value.currentList?.let { list ->
                    if (list.isCompleted) return@let
                    val updatedList = list.copy(updatedAt = System.currentTimeMillis())
                    repository.updateShoppingList(updatedList)
                }
            }
        }
    }

    fun updateListName(newName: String) {
        uiState.value.currentList?.let { list ->
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
        uiState.value.currentList?.let { list ->
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
        uiState.value.currentList?.let { list ->
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
        uiState.value.currentList?.let { list ->
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
            _productToAddStateFlow.value = repository.getProductById(productId).first()
        }
    }

    fun clearProductToAdd() {
        _productToAddStateFlow.value = null
    }

    fun confirmAddProductToList(quantity: Double, price: Double?, unit: ProductUnit) {
        val product = _productToAddStateFlow.value
        val listId = uiState.value.currentList?.shoppingListId
        val ownerId = application.getCurrentUserId()

        if (product != null && listId != null) {
            viewModelScope.launch {
                val currentItems = repository.getItemsForList(listId).first()
                val newSortOrder = (currentItems.minOfOrNull { it.manualSortOrder } ?: 1) - 1

                val newItem = ShoppingListItem(
                    shoppingListId = listId,
                    productId = product.productId,
                    productName = product.productName,
                    quantity = if (quantity > 0) quantity else 1.0,
                    unit = unit,
                    price = price ?: product.defaultPrice,
                    isBought = false,
                    departmentIdAtPurchase = product.departmentId,
                    manualSortOrder = newSortOrder,
                    ownerId = ownerId,
                    firestoreId = ""
                )
                repository.addShoppingListItem(newItem)
                clearProductToAdd()
            }
        }
    }
}