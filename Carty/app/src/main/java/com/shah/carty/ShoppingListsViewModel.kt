package com.shah.carty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingListsUiState(
    val activeLists: List<ShoppingList> = emptyList()
)

class ShoppingListsViewModel(
    private val repository: CartyRepository,
    private val application: CartyApplication
) : ViewModel() {

    val uiState: StateFlow<ShoppingListsUiState> =
        repository.getActiveAndFavoriteLists()
            .map { lists -> ShoppingListsUiState(activeLists = lists) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = ShoppingListsUiState()
            )

    fun createNewShoppingListAndNavigate(callback: (Long) -> Unit) {
        viewModelScope.launch {
            val ownerId = application.getCurrentUserId()
            val currentTime = System.currentTimeMillis()

            val currentDisplayedLists = uiState.value.activeLists
            val nextSortIndex = (currentDisplayedLists.minOfOrNull { list -> list.manualSortIndex } ?: 1) - 1

            val defaultName = "Новый список"
            val newList = ShoppingList(
                shoppingListName = defaultName, ownerId = ownerId,
                createdAt = currentTime, updatedAt = currentTime,
                isFavorite = false, isCompleted = false,
                departmentOrder = emptyList(), manualSortIndex = nextSortIndex
            )
            val newId = repository.addShoppingList(newList)
            callback(newId)
        }
    }

    fun updateShoppingListsOrder(orderedLists: List<ShoppingList>) {
        viewModelScope.launch {
            val listsToUpdate = mutableListOf<ShoppingList>()
            val currentSnapshot = uiState.value.activeLists

            orderedLists.forEachIndexed { index, newListOrderVersion ->
                val oldVersionInState = currentSnapshot.find { it.shoppingListId == newListOrderVersion.shoppingListId }

                if (oldVersionInState != null) {
                    if (oldVersionInState.manualSortIndex != index ||
                        currentSnapshot.getOrNull(index)?.shoppingListId != newListOrderVersion.shoppingListId) {
                        listsToUpdate.add(newListOrderVersion.copy(manualSortIndex = index))
                    }
                } else {
                    listsToUpdate.add(newListOrderVersion.copy(manualSortIndex = index))
                }
            }

            if (listsToUpdate.isEmpty() && currentSnapshot.size == orderedLists.size) {
                var orderChanged = false
                for(i in orderedLists.indices) {
                    if(currentSnapshot.getOrNull(i)?.shoppingListId != orderedLists.getOrNull(i)?.shoppingListId) {
                        orderChanged = true
                        break
                    }
                }
                if(orderChanged) {
                    orderedLists.forEachIndexed{ index, list ->
                        listsToUpdate.add(list.copy(manualSortIndex = index))
                    }
                }
            }

            if (listsToUpdate.isNotEmpty()) {
                listsToUpdate.forEach { repository.updateShoppingList(it) }
            }
        }
    }
}