package com.shah.carty

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DepartmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDepartment(department: Department): Long

    @Update
    suspend fun updateDepartment(department: Department)

    @Update
    suspend fun updateDepartments(departments: List<Department>)

    @Delete
    suspend fun deleteDepartment(department: Department)

    @Query("SELECT * FROM Department WHERE ownerId = :ownerId ORDER BY manualSortIndex ASC, departmentName ASC")
    fun getAllDepartmentsList(ownerId: String): Flow<List<Department>>

    @Query("SELECT * FROM Department WHERE departmentId = :departmentId AND ownerId = :ownerId")
    fun getDepartmentById(departmentId: Long, ownerId: String): Flow<Department?>

    @Query("DELETE FROM Department WHERE ownerId = :ownerId")
    suspend fun deleteAllByOwnerId(ownerId: String)
}

@Dao
interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Update
    suspend fun updateProducts(products: List<Product>)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("SELECT * FROM Product WHERE ownerId = :ownerId ORDER BY manualSortIndex ASC, productName ASC")
    fun getAllProductsList(ownerId: String): Flow<List<Product>>

    @Query("SELECT * FROM Product WHERE productId = :productId AND ownerId = :ownerId")
    fun getProductById(productId: Long, ownerId: String): Flow<Product?>

    @Query("SELECT * FROM Product WHERE productName LIKE '%' || :productName || '%' AND ownerId = :ownerId ORDER BY manualSortIndex ASC, productName ASC")
    fun getProductsByName(productName: String, ownerId: String): Flow<List<Product>>

    @Query("UPDATE Product SET departmentId = NULL WHERE departmentId = :departmentIdToDelete AND ownerId = :ownerId")
    suspend fun resetDepartmentIdForOwner(departmentIdToDelete: Long, ownerId: String)

    @Query("DELETE FROM Product WHERE ownerId = :ownerId")
    suspend fun deleteAllByOwnerId(ownerId: String)

    @Query("SELECT * FROM Product WHERE departmentId = :departmentId AND ownerId = :ownerId ORDER BY manualSortIndex ASC, productName ASC")
    fun getProductsByDepartmentId(departmentId: Long, ownerId: String): Flow<List<Product>>
}

@Dao
interface ShoppingListDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addShoppingList(shoppingList: ShoppingList): Long

    @Update
    suspend fun updateShoppingList(shoppingList: ShoppingList)

    @Update
    suspend fun updateShoppingLists(shoppingLists: List<ShoppingList>)

    @Delete
    suspend fun deleteShoppingList(shoppingList: ShoppingList)

    @Query("SELECT * FROM ShoppingList WHERE ownerId = :ownerId AND (isFavorite = 1 OR isCompleted = 0) ORDER BY isFavorite DESC, manualSortIndex ASC, createdAt DESC")
    fun getActiveAndFavoriteLists(ownerId: String): Flow<List<ShoppingList>>

    @Query("SELECT * FROM ShoppingList WHERE shoppingListId = :shoppingListId AND ownerId = :ownerId")
    fun getShoppingListById(shoppingListId: Long, ownerId: String): Flow<ShoppingList?>

    @Query("SELECT * FROM ShoppingList WHERE ownerId = :ownerId")
    fun getAllListsByOwnerId(ownerId: String): Flow<List<ShoppingList>>

    @Query("DELETE FROM ShoppingList WHERE ownerId = :ownerId")
    suspend fun deleteAllByOwnerId(ownerId: String)
}

@Dao
interface ShoppingListItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addShoppingListItem(shoppingListItem: ShoppingListItem): Long

    @Update
    suspend fun updateShoppingListItem(shoppingListItem: ShoppingListItem)

    @Update
    suspend fun updateShoppingListItems(shoppingListItems: List<ShoppingListItem>)

    @Delete
    suspend fun deleteShoppingListItem(shoppingListItem: ShoppingListItem)

    @Query("SELECT * FROM ShoppingListItem WHERE shoppingListId = :shoppingListId AND ownerId = :ownerId ORDER BY manualSortOrder ASC")
    fun getItemsForList(shoppingListId: Long, ownerId: String): Flow<List<ShoppingListItem>>

    @Query("SELECT * FROM ShoppingListItem WHERE shoppingListItemId = :itemId AND ownerId = :ownerId")
    fun getShoppingListItemById(itemId: Long, ownerId: String): Flow<ShoppingListItem?>

    @Query("DELETE FROM ShoppingListItem WHERE shoppingListId = :listId AND ownerId = :ownerId")
    suspend fun deleteShoppingListItemsByListIdAndOwnerId(listId: Long, ownerId: String)

    @Query("DELETE FROM ShoppingListItem WHERE productId = :productId AND ownerId = :ownerId")
    suspend fun deleteItemsByProductIdAndOwnerId(productId: Long, ownerId: String)

    @Query("SELECT * FROM ShoppingListItem WHERE productId = :productId AND ownerId = :ownerId")
    fun getAllItemsByProductIdAndOwnerId(productId: Long, ownerId: String): Flow<List<ShoppingListItem>>
}