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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


sealed interface DisplayableItem {
    val viewType: Int
    val id: Long

    data class DepartmentHeader(
        val departmentId: Long,
        val departmentName: String,
        override val id: Long = departmentId
    ) : DisplayableItem {
        override val viewType: Int = ShoppingListItemAdapter.VIEW_TYPE_HEADER
    }

    data class ShoppingListItemRow(
        val item: ShoppingListItem,
        override val id: Long = item.shoppingListItemId
    ) : DisplayableItem {
        override val viewType: Int = ShoppingListItemAdapter.VIEW_TYPE_ITEM
    }
}


data class ViewShoppingListUiState(
    val currentList: ShoppingList? = null,
    val itemsInList: List<ShoppingListItem> = emptyList(),
    val displayableItems: List<DisplayableItem> = emptyList(),
    val departmentMap: Map<Long, String> = emptyMap(),
    val isGroupingEnabled: Boolean = true,
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
    private val _isGroupingEnabled = MutableStateFlow(true)


    val uiState: StateFlow<ViewShoppingListUiState> = combine(
        repository.getShoppingListById(shoppingListId),
        repository.getItemsForList(shoppingListId),
        repository.getAllDepartmentsList(),
        _productToAddStateFlow.asStateFlow(),
        _isGroupingEnabled.asStateFlow()
    ) { list, items, allDepartments, productToAdd, isGroupingEnabled ->
        val departmentMap = allDepartments.associateBy({ it.departmentId }, { it.departmentName })
        val displayableItems = mutableListOf<DisplayableItem>()

        if (list != null) {
            if (isGroupingEnabled) {
                list.departmentOrder.forEach { deptId ->
                    val itemsInThisDept = items.filter { it.departmentIdAtPurchase == deptId }
                        .sortedBy { it.manualSortOrder }
                    if (itemsInThisDept.isNotEmpty()) {
                        displayableItems.add(
                            DisplayableItem.DepartmentHeader(
                                deptId,
                                departmentMap[deptId] ?: "Отдел не найден"
                            )
                        )
                        itemsInThisDept.forEach { item ->
                            displayableItems.add(DisplayableItem.ShoppingListItemRow(item))
                        }
                    }
                }
                val departmentsInOrder = list.departmentOrder.toSet()
                val remainingItemsByDept = items
                    .filter { it.departmentIdAtPurchase != null && it.departmentIdAtPurchase !in departmentsInOrder }
                    .groupBy { it.departmentIdAtPurchase }

                remainingItemsByDept.keys.sortedBy { departmentMap[it] ?: "" }.forEach { deptId ->
                    val itemsInThisDept = remainingItemsByDept[deptId]?.sortedBy { it.manualSortOrder }
                    if (itemsInThisDept?.isNotEmpty() == true && deptId != null) {
                        displayableItems.add(
                            DisplayableItem.DepartmentHeader(
                                deptId,
                                departmentMap[deptId] ?: "Отдел не найден"
                            )
                        )
                        itemsInThisDept.forEach { item ->
                            displayableItems.add(DisplayableItem.ShoppingListItemRow(item))
                        }
                    }
                }

                val itemsWithoutDepartment = items.filter { it.departmentIdAtPurchase == null }
                    .sortedBy { it.manualSortOrder }
                if (itemsWithoutDepartment.isNotEmpty()) {
                    displayableItems.add(
                        DisplayableItem.DepartmentHeader(
                            DEPARTMENT_ID_NO_DEPARTMENT,
                            application.getString(R.string.no_department_selected_group)
                        )
                    )
                    itemsWithoutDepartment.forEach { item ->
                        displayableItems.add(DisplayableItem.ShoppingListItemRow(item))
                    }
                }

            } else {
                items.sortedBy { it.manualSortOrder }.forEach { item ->
                    displayableItems.add(DisplayableItem.ShoppingListItemRow(item))
                }
            }
        }

        ViewShoppingListUiState(
            currentList = list,
            itemsInList = items,
            displayableItems = displayableItems,
            departmentMap = departmentMap,
            isGroupingEnabled = isGroupingEnabled,
            isLoading = false,
            listNotFound = (list == null && !isLoadingInitial && items.any { it.shoppingListId == shoppingListId }),
            productToAdd = productToAdd
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ViewShoppingListUiState(isLoading = true)
    )
    private var isLoadingInitial = true

    init {
        viewModelScope.launch {
            repository.getShoppingListById(shoppingListId).first()
            isLoadingInitial = false
        }
    }

    fun toggleGrouping() {
        _isGroupingEnabled.value = !_isGroupingEnabled.value
    }

    fun updateDepartmentOrder(orderedDepartmentIds: List<Long>) {
        uiState.value.currentList?.let { list ->
            if (list.departmentOrder != orderedDepartmentIds) {
                viewModelScope.launch {
                    val updatedList = list.copy(
                        departmentOrder = orderedDepartmentIds,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateShoppingList(updatedList)
                }
            }
        }
    }

    fun updateShoppingListItemsOrder(processedItemsFromFragment: List<ShoppingListItem>) {
        android.util.Log.d("CartyDND", "ViewModel updateShoppingListItemsOrder - received ${processedItemsFromFragment.size} items")
        processedItemsFromFragment.forEach { item ->
            android.util.Log.d("CartyDND", "ViewModel updateShoppingListItemsOrder - item: $item")
        }

        viewModelScope.launch {
            val originalItemsFromUiState = uiState.value.itemsInList
            val itemsToPersistInDb = mutableListOf<ShoppingListItem>()
            var hasAnythingChanged = false

            if (processedItemsFromFragment.size != originalItemsFromUiState.size) {
                hasAnythingChanged = true
                // Если размеры разные, это уже изменение, просто используем новый список
                itemsToPersistInDb.addAll(processedItemsFromFragment)
            } else {
                // Размеры одинаковы, сравниваем элементы
                for (i in processedItemsFromFragment.indices) {
                    val processedItem = processedItemsFromFragment[i]
                    // Ищем соответствующий оригинальный элемент по ID, так как порядок мог измениться
                    val originalItem = originalItemsFromUiState.find { it.shoppingListItemId == processedItem.shoppingListItemId }

                    if (originalItem == null) { // Элемент появился? Не должно быть при drag-drop
                        hasAnythingChanged = true
                        itemsToPersistInDb.add(processedItem) // Добавляем как есть
                    } else {
                        if (originalItem.manualSortOrder != processedItem.manualSortOrder ||
                            originalItem.departmentIdAtPurchase != processedItem.departmentIdAtPurchase) {
                            hasAnythingChanged = true
                        }
                        itemsToPersistInDb.add(processedItem) // Добавляем обработанный элемент (с новым sortOrder, и тем departmentId, что пришел)
                    }
                }
                // Если порядок элементов изменился, но сами элементы (содержимое) нет, hasAnythingChanged может быть false.
                // Нам нужно проверить, изменился ли сам порядок ID.
                if (!hasAnythingChanged) {
                    val originalIdsOrder = originalItemsFromUiState.map { it.shoppingListItemId }
                    val processedIdsOrder = processedItemsFromFragment.map { it.shoppingListItemId }
                    if (originalIdsOrder != processedIdsOrder) {
                        hasAnythingChanged = true
                    }
                }
            }

            android.util.Log.d("CartyDND", "ViewModel updateShoppingListItemsOrder - hasAnythingChanged: $hasAnythingChanged")
            if (hasAnythingChanged) {
                android.util.Log.d("CartyDND", "ViewModel updateShoppingListItemsOrder - Persisting ${itemsToPersistInDb.size} items: $itemsToPersistInDb")
                repository.updateShoppingListItems(itemsToPersistInDb) // Передаем полный список в новом порядке
                uiState.value.currentList?.let { list ->
                    if (!list.isCompleted) {
                        val updatedList = list.copy(updatedAt = System.currentTimeMillis())
                        repository.updateShoppingList(updatedList)
                    }
                }
            } else {
                android.util.Log.d("CartyDND", "ViewModel updateShoppingListItemsOrder - No changes detected to persist.")
            }
        }
    }

    fun updateShoppingListItemDetails(itemToUpdate: ShoppingListItem, newQuantity: Double, newPrice: Double?) {
        viewModelScope.launch {
            val updatedItem = itemToUpdate.copy(
                quantity = if (newQuantity > 0) newQuantity else itemToUpdate.quantity,
                price = newPrice
            )
            repository.updateShoppingListItem(updatedItem)
            uiState.value.currentList?.let { list ->
                if (!list.isCompleted) {
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
            uiState.value.currentList?.let { list ->
                if (!list.isCompleted) {
                    val updatedList = list.copy(updatedAt = System.currentTimeMillis())
                    repository.updateShoppingList(updatedList)
                }
            }
        }
    }

    fun deleteShoppingListItem(item: ShoppingListItem) {
        viewModelScope.launch {
            repository.deleteShoppingListItem(item)
            uiState.value.currentList?.let { list ->
                if (!list.isCompleted) {
                    val updatedList = list.copy(updatedAt = System.currentTimeMillis())
                    repository.updateShoppingList(updatedList)
                }
            }
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
                val currentDisplayableItems = uiState.value.displayableItems
                val newSortOrder: Int

                if (_isGroupingEnabled.value) {
                    val departmentIdForNewItem = product.departmentId
                    val itemsInSameDepartment = currentDisplayableItems
                        .filterIsInstance<DisplayableItem.ShoppingListItemRow>()
                        .filter { it.item.departmentIdAtPurchase == departmentIdForNewItem }
                    newSortOrder = (itemsInSameDepartment.maxOfOrNull { it.item.manualSortOrder } ?: -1) + 1
                } else {
                    newSortOrder = (currentDisplayableItems
                        .filterIsInstance<DisplayableItem.ShoppingListItemRow>()
                        .maxOfOrNull { it.item.manualSortOrder } ?: -1) + 1
                }

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
                uiState.value.currentList?.let { list ->
                    if (!list.isCompleted) {
                        val updatedList = list.copy(updatedAt = System.currentTimeMillis())
                        repository.updateShoppingList(updatedList)
                    }
                }
            }
        }
    }
    companion object {
        const val DEPARTMENT_ID_NO_DEPARTMENT = -1L
    }
}