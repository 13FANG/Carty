package com.shah.carty

import kotlinx.coroutines.flow.Flow

class OfflineCartyRepository(private val departmentDao: DepartmentDao,
                             private val productDao: ProductDao,
                             private val shoppingListDao: ShoppingListDao,
                             private val shoppingListItemDao: ShoppingListItemDao): CartyRepository {
    // Department
    override suspend fun addDepartment(department: Department) {
        departmentDao.addDepartment(department)
    }
    override suspend fun updateDepartment(department: Department) {
        departmentDao.updateDepartment(department)
    }
    override suspend fun deleteDepartment(department: Department) {
        departmentDao.deleteDepartment(department)
    }
    override fun getAllDepartmentsList(): Flow<List<Department>> {
        return departmentDao.getAllDepartmentsList()
    }
    override fun getDepartmentById(departmentIdForSearch: Long): Flow<Department?> {
        return departmentDao.getDepartmentById(departmentIdForSearch)
    }
    // Product
    override suspend fun addProduct(product: Product) {
        productDao.addProduct(product)
    }
    override suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product)
    }
    override suspend fun deleteProduct(product: Product) {
        productDao.deleteProduct(product)
    }
    override fun getAllProductsList(): Flow<List<Product>> {
        return productDao.getAllProductsList()
    }
    override fun getProductById(productIdForSearch: Long): Flow<Product?> {
        return productDao.getProductById(productIdForSearch)
    }
    override fun getProductByName(productNameForSearch: String): Flow<List<Product>> {
        return productDao.getProductByName(productNameForSearch)
    }
    override suspend fun resetDepartmentId(departmentIdToDelete: Long) {
        productDao.resetDepartmentId(departmentIdToDelete)
    }
    // Shopping List
    override suspend fun addShoppingList(shoppingList: ShoppingList) {
        shoppingListDao.addShoppingList(shoppingList)
    }
    override suspend fun updateShoppingList(shoppingList: ShoppingList) {
        shoppingListDao.updateShoppingList(shoppingList)
    }
    override suspend fun deleteShoppingList(shoppingList: ShoppingList) {
        shoppingListDao.deleteShoppingList(shoppingList)
    }
    override fun getActiveAndFavoriteLists(): Flow<List<ShoppingList>> {
        return shoppingListDao.getActiveAndFavoriteLists()
    }
    override fun getShoppingListById(shoppingListIdForSearch: Long): Flow<ShoppingList?> {
        return shoppingListDao.getShoppingListById(shoppingListIdForSearch)
    }
    // Shopping List Item
    override suspend fun addShoppingListItem(shoppingListItem: ShoppingListItem) {
        shoppingListItemDao.addShoppingListItem(shoppingListItem)
    }
    override suspend fun updateShoppingListItem(shoppingListItem: ShoppingListItem) {
        shoppingListItemDao.updateShoppingListItem(shoppingListItem)
    }
    override suspend fun deleteShoppingListItem(shoppingListItem: ShoppingListItem) {
        shoppingListItemDao.deleteShoppingListItem(shoppingListItem)
    }
    override fun getItemsForList(shoppingListIdForSearch: Long): Flow<List<ShoppingListItem>> {
        return shoppingListItemDao.getItemsForList(shoppingListIdForSearch)
    }
    override fun getShoppingListItemById(shoppingListItemIdForSearch: Long): Flow<ShoppingListItem?> {
        return shoppingListItemDao.getShoppingListItemById(shoppingListItemIdForSearch)
    }
    override suspend fun deleteShoppingListItemsById(shoppingListIdForDel: Long) {
        shoppingListItemDao.deleteShoppingListItemsById(shoppingListIdForDel)
    }
    override suspend fun deleteShoppingListItemsByProductId(productIdForDel: Long) {
        shoppingListItemDao.deleteShoppingListItemsByProductId(productIdForDel)
    }

}