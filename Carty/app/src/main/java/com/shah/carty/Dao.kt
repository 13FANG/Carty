package com.shah.carty

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao interface DepartmentDao{
    @Insert (onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDepartment(department: Department)

    @Update
    suspend fun updateDepartment(department: Department)

    @Delete
    suspend fun deleteDepartment(department: Department)

    @Query ("SELECT * FROM Department ORDER BY departmentName ASC")
    fun getAllDepartmentsList(): Flow<List<Department>>

    @Query ("SELECT * FROM Department WHERE departmentId = :departmentIdForSearch")
    fun getDepartmentById (departmentIdForSearch: Long): Flow<Department?>
}

@Dao interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addProduct(product: Product)

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("SELECT * FROM Product ORDER BY productName ASC")
    fun getAllProductsList(): Flow<List<Product>>

    @Query("SELECT * FROM Product WHERE productId = :productIdForSearch")
    fun getProductById(productIdForSearch: Long): Flow<Product?>

    @Query("SELECT * FROM Product WHERE productName = :productNameForSearch")
    fun getProductByName(productNameForSearch: String): Flow<List<Product>>

    @Query("UPDATE Product SET departmentId = NULL WHERE departmentId = :departmentIdToDelete")
    suspend fun resetDepartmentId(departmentIdToDelete: Long)
}

@Dao interface ShoppingListDao{
    @Insert (onConflict = OnConflictStrategy.REPLACE)
    suspend fun addShoppingList(shoppingList: ShoppingList)

    @Update
    suspend fun updateShoppingList(shoppingList: ShoppingList)

    @Delete
    suspend fun deleteShoppingList(shoppingList: ShoppingList)

    @Query ("SELECT * FROM ShoppingList WHERE isFavorite = 1 OR isCompleted = 0 ORDER BY manualSortIndex ASC")
    fun getActiveAndFavoriteLists(): Flow<List<ShoppingList>>

    @Query ("SELECT * FROM ShoppingList WHERE shoppingListId = :shoppingListIdForSearch")
    fun getShoppingListById (shoppingListIdForSearch: Long): Flow<ShoppingList?>
}

@Dao interface ShoppingListItemDao{
    @Insert (onConflict = OnConflictStrategy.REPLACE)
    suspend fun addShoppingListItem(shoppingListItem: ShoppingListItem)

    @Update
    suspend fun updateShoppingListItem(shoppingListItem: ShoppingListItem)

    @Delete
    suspend fun deleteShoppingListItem(shoppingListItem: ShoppingListItem)

    @Query ("SELECT * FROM ShoppingListItem WHERE shoppingListId = :shoppingListIdForSearch ORDER BY manualSortOrder ASC")
    fun getItemsForList(shoppingListIdForSearch: Long): Flow<List<ShoppingListItem>>

    @Query ("SELECT * FROM ShoppingListItem WHERE shoppingListItemId = :shoppingListItemIdForSearch")
    fun getShoppingListItemById (shoppingListItemIdForSearch: Long): Flow<ShoppingListItem?>

    @Query ("DELETE FROM ShoppingListItem WHERE shoppingListId = :shoppingListIdForDel")
    suspend fun deleteShoppingListItemsById(shoppingListIdForDel: Long)

    @Query ("DELETE FROM ShoppingListItem WHERE productId = :productIdForDel")
    suspend fun deleteShoppingListItemsByProductId(productIdForDel: Long)
}