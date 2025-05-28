package com.shah.carty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
            val nextSortIndex = if (currentDisplayedLists.isEmpty()) {
                0
            } else {
                (currentDisplayedLists.minOfOrNull { it.manualSortIndex } ?: 0) - 1
            }


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

    fun updateShoppingListsOrder(orderedListsFromAdapter: List<ShoppingList>) {
        viewModelScope.launch {
            val originalListsFromStateById = uiState.value.activeLists.associateBy { it.shoppingListId }
            var orderActuallyChanged = false

            val listsToPersist = orderedListsFromAdapter.mapIndexedNotNull { newIndex, listFromAdapter ->
                val originalList = originalListsFromStateById[listFromAdapter.shoppingListId]
                if (originalList != null) {
                    if (originalList.manualSortIndex != newIndex) {
                        orderActuallyChanged = true
                        listFromAdapter.copy(manualSortIndex = newIndex)
                    } else {
                        listFromAdapter
                    }
                } else {
                    orderActuallyChanged = true
                    listFromAdapter.copy(manualSortIndex = newIndex)
                }
            }
            if (orderedListsFromAdapter.size != uiState.value.activeLists.size) {
                orderActuallyChanged = true
            } else {
                val idsFromAdapter = orderedListsFromAdapter.map { it.shoppingListId }.toSet()
                val idsFromState = uiState.value.activeLists.map { it.shoppingListId }.toSet()
                if (idsFromAdapter != idsFromState) {
                    orderActuallyChanged = true
                }
            }

            if (orderActuallyChanged) {
                repository.updateShoppingLists(listsToPersist)
            }
        }
    }
}