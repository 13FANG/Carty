package com.shah.carty

import android.util.Log
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
    val productToAdd: Product? = null,
    val totalSum: Double = 0.0
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
        val displayableItemsResult = mutableListOf<DisplayableItem>()

        val totalSum = items.sumOf { item ->
            (item.price ?: 0.0) * item.quantity
        }

        if (list != null) {
            if (isGroupingEnabled) {
                val departmentOrderToUse = list.departmentOrder.ifEmpty {
                    items.mapNotNull { it.departmentIdAtPurchase }.distinct()
                        .sortedWith(compareBy<Long>(
                            { departmentMap[it]?.lowercase() ?: "~~~" },
                            { it }
                        ))
                }

                departmentOrderToUse.forEach { deptId ->
                    val itemsInThisDept = items.filter { it.departmentIdAtPurchase == deptId }
                        .sortedBy { it.manualSortOrder }
                    if (itemsInThisDept.isNotEmpty() || list.departmentOrder.contains(deptId)) {
                        displayableItemsResult.add(
                            DisplayableItem.DepartmentHeader(
                                deptId,
                                departmentMap[deptId] ?: application.getString(R.string.unknown_department)
                            )
                        )
                        itemsInThisDept.forEach { item ->
                            displayableItemsResult.add(DisplayableItem.ShoppingListItemRow(item))
                        }
                    }
                }

                val departmentsInProcessedOrderSet = departmentOrderToUse.toSet()
                val remainingItemsByDept = items
                    .filter { it.departmentIdAtPurchase != null && it.departmentIdAtPurchase !in departmentsInProcessedOrderSet }
                    .groupBy { it.departmentIdAtPurchase }

                remainingItemsByDept.keys.filterNotNull().sortedWith(
                    compareBy<Long>(
                        { departmentMap[it]?.lowercase() ?: "~~~" },
                        { it }
                    )
                ).forEach { deptId ->
                    val itemsInThisDept = remainingItemsByDept[deptId]?.sortedBy { it.manualSortOrder }
                    if (itemsInThisDept?.isNotEmpty() == true) {
                        displayableItemsResult.add(
                            DisplayableItem.DepartmentHeader(
                                deptId,
                                departmentMap[deptId] ?: application.getString(R.string.unknown_department)
                            )
                        )
                        itemsInThisDept.forEach { item ->
                            displayableItemsResult.add(DisplayableItem.ShoppingListItemRow(item))
                        }
                    }
                }

                val itemsWithoutDepartment = items.filter { it.departmentIdAtPurchase == null }
                    .sortedBy { it.manualSortOrder }
                if (itemsWithoutDepartment.isNotEmpty()) {
                    if (displayableItemsResult.none { it is DisplayableItem.DepartmentHeader && it.departmentId == DEPARTMENT_ID_NO_DEPARTMENT}) {
                        displayableItemsResult.add(
                            DisplayableItem.DepartmentHeader(
                                DEPARTMENT_ID_NO_DEPARTMENT,
                                application.getString(R.string.no_department_selected_group)
                            )
                        )
                    }
                    itemsWithoutDepartment.forEach { item ->
                        displayableItemsResult.add(DisplayableItem.ShoppingListItemRow(item))
                    }
                }

            } else {
                items.sortedBy { it.manualSortOrder }.forEach { item ->
                    displayableItemsResult.add(DisplayableItem.ShoppingListItemRow(item))
                }
            }
        }

        ViewShoppingListUiState(
            currentList = list,
            itemsInList = items,
            displayableItems = displayableItemsResult,
            departmentMap = departmentMap,
            isGroupingEnabled = isGroupingEnabled,
            isLoading = false,
            listNotFound = (list == null && !isLoadingInitial && items.any { it.shoppingListId == shoppingListId }),
            productToAdd = productToAdd,
            totalSum = totalSum
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ViewShoppingListUiState(isLoading = true)
    )
    private var isLoadingInitial = true
    private val _departmentMapState = MutableStateFlow<Map<Long, String>>(emptyMap())


    init {
        viewModelScope.launch {
            repository.getShoppingListById(shoppingListId).first()
            isLoadingInitial = false
        }
        viewModelScope.launch {
            repository.getAllDepartmentsList().collect { departments ->
                _departmentMapState.value = departments.associateBy({ dep -> dep.departmentId }, { dep -> dep.departmentName })
            }
        }
    }

    fun toggleGrouping() {
        _isGroupingEnabled.value = !_isGroupingEnabled.value
    }

    fun processAndUpdateOrder(orderedDisplayableItems: List<DisplayableItem>) {
        val newDepartmentOrder = orderedDisplayableItems
            .filterIsInstance<DisplayableItem.DepartmentHeader>()
            .map { it.departmentId }
            .filter { it != DEPARTMENT_ID_NO_DEPARTMENT }
        updateDepartmentOrderIfNeeded(newDepartmentOrder)

        val finalShoppingListItemsToSave = mutableListOf<ShoppingListItem>()
        var orderInDept = 0

        orderedDisplayableItems.forEach { displayable ->
            when (displayable) {
                is DisplayableItem.DepartmentHeader -> {
                    orderInDept = 0
                }
                is DisplayableItem.ShoppingListItemRow -> {
                    finalShoppingListItemsToSave.add(
                        displayable.item.copy(
                            manualSortOrder = orderInDept++
                        )
                    )
                }
            }
        }
        finalShoppingListItemsToSave.forEach {}
        updateShoppingListItemsOrderIfNeeded(finalShoppingListItemsToSave)
    }

    private fun updateDepartmentOrderIfNeeded(newDepartmentOrder: List<Long>) {
        val currentShoppingList = uiState.value.currentList ?: return

        if (currentShoppingList.departmentOrder != newDepartmentOrder) {
            viewModelScope.launch {
                val updatedList = currentShoppingList.copy(
                    departmentOrder = newDepartmentOrder,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateShoppingList(updatedList)
            }
        } else {}
    }

    private fun updateShoppingListItemsOrderIfNeeded(processedItemsWithNewSortOrder: List<ShoppingListItem>) {
        viewModelScope.launch {
            val originalItemsFromState = uiState.value.itemsInList
            var significantChangeFound = false

            if (processedItemsWithNewSortOrder.size != originalItemsFromState.size) {
                significantChangeFound = true
            } else {
                val originalItemsMap = originalItemsFromState.associateBy { it.shoppingListItemId }
                for (processedItem in processedItemsWithNewSortOrder) {
                    val originalItem = originalItemsMap[processedItem.shoppingListItemId]
                    if (originalItem == null ||
                        originalItem.manualSortOrder != processedItem.manualSortOrder ||
                        originalItem.departmentIdAtPurchase != processedItem.departmentIdAtPurchase) {
                        significantChangeFound = true
                        break
                    }
                }
                if (!significantChangeFound) {
                    val originalIdsOrder = originalItemsFromState.map { it.shoppingListItemId }
                    val processedIdsOrder = processedItemsWithNewSortOrder.map { it.shoppingListItemId }
                    if(originalIdsOrder != processedIdsOrder && processedIdsOrder.isNotEmpty()) {
                        significantChangeFound = true
                    }
                }
            }

            if (significantChangeFound) {
                processedItemsWithNewSortOrder.forEach { item -> }
                repository.updateShoppingListItems(processedItemsWithNewSortOrder)
                uiState.value.currentList?.let { list ->
                    if (!list.isCompleted) {
                        val updatedList = list.copy(updatedAt = System.currentTimeMillis())
                        repository.updateShoppingList(updatedList)
                    }
                }
            } else {}
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
        val currentShoppingList = uiState.value.currentList
        val ownerId = application.getCurrentUserId()

        if (product != null && currentShoppingList != null) {
            viewModelScope.launch {
                val currentDisplayableItemsValue = uiState.value.displayableItems
                val isGroupingCurrentlyEnabled = _isGroupingEnabled.value
                var newSortOrder = 0

                if (isGroupingCurrentlyEnabled) {
                    val departmentIdForNewItem = product.departmentId
                    val itemsInSameDepartment = currentDisplayableItemsValue
                        .filterIsInstance<DisplayableItem.ShoppingListItemRow>()
                        .filter { it.item.departmentIdAtPurchase == departmentIdForNewItem }
                    newSortOrder = (itemsInSameDepartment.maxOfOrNull { it.item.manualSortOrder } ?: -1) + 1
                } else {
                    newSortOrder = (currentDisplayableItemsValue
                        .filterIsInstance<DisplayableItem.ShoppingListItemRow>()
                        .maxOfOrNull { it.item.manualSortOrder } ?: -1) + 1
                }

                val newItem = ShoppingListItem(
                    shoppingListId = currentShoppingList.shoppingListId,
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
                if (!currentShoppingList.isCompleted) {
                    val updatedList = currentShoppingList.copy(updatedAt = System.currentTimeMillis())
                    repository.updateShoppingList(updatedList)
                }
            }
        }
    }
    companion object {
        const val DEPARTMENT_ID_NO_DEPARTMENT = -1L
    }
}