package com.shah.carty

import com.shah.carty.Department
import com.shah.carty.Product
import com.shah.carty.ShoppingList
import com.shah.carty.ShoppingListItem
import kotlinx.coroutines.flow.Flow

interface CartyRepository {
    // Department
    suspend fun addDepartment(department: Department)
    suspend fun updateDepartment(department: Department)
    suspend fun deleteDepartment(department: Department)
    fun getAllDepartmentsList(): Flow<List<Department>>
    fun getDepartmentById (departmentIdForSearch: Long): Flow<Department?>
    // Product
    suspend fun addProduct(product: Product)
    suspend fun updateProduct(product: Product)
    suspend fun deleteProduct(product: Product)
    fun getAllProductsList(): Flow<List<Product>>
    fun getProductById(productIdForSearch: Long): Flow<Product?>
    fun getProductByName(productNameForSearch: String): Flow<List<Product>>
    suspend fun resetDepartmentId(departmentIdToDelete: Long)
    //Shopping List
    suspend fun addShoppingList(shoppingList: ShoppingList)
    suspend fun updateShoppingList(shoppingList: ShoppingList)
    suspend fun deleteShoppingList(shoppingList: ShoppingList)
    fun getActiveAndFavoriteLists(): Flow<List<ShoppingList>>
    fun getShoppingListById (shoppingListIdForSearch: Long): Flow<ShoppingList?>
    //Shopping List Item
    suspend fun addShoppingListItem(shoppingListItem: ShoppingListItem)
    suspend fun updateShoppingListItem(shoppingListItem: ShoppingListItem)
    suspend fun deleteShoppingListItem(shoppingListItem: ShoppingListItem)
    fun getItemsForList(shoppingListIdForSearch: Long): Flow<List<ShoppingListItem>>
    fun getShoppingListItemById (shoppingListItemIdForSearch: Long): Flow<ShoppingListItem?>
    suspend fun deleteShoppingListItemsById(shoppingListIdForDel: Long)
    suspend fun deleteShoppingListItemsByProductId(productIdForDel: Long)
}