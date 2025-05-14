package com.shah.carty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shah.carty.CartyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShoppingListsUiState(
    val activeLists: List<ShoppingList> = emptyList()
)

class ShoppingListsViewModel(private val repository: CartyRepository) : ViewModel() {

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
            val ownerIdPlaceholder = "user_guest_or_id"
            val currentTime = System.currentTimeMillis()
            val currentLists = uiState.value.activeLists
            val nextSortIndex = (currentLists.minOfOrNull { it.manualSortIndex } ?: 1) - 1

            val defaultName = "Новый список"

            val newList = ShoppingList(
                shoppingListName = defaultName,
                ownerId = ownerIdPlaceholder,
                createdAt = currentTime,
                updatedAt = currentTime,
                isFavorite = false,
                isCompleted = false,
                departmentOrder = emptyList(),
                manualSortIndex = nextSortIndex
            )
            val newId = repository.addShoppingList(newList)
            callback(newId)
        }
    }
}