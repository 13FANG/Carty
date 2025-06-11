package com.shah.carty

import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class OfflineCartyRepository(
    private val departmentDao: DepartmentDao,
    private val productDao: ProductDao,
    private val shoppingListDao: ShoppingListDao,
    private val shoppingListItemDao: ShoppingListItemDao,
    private val firebaseAuth: FirebaseAuth,
    private val database: CartyDatabase
) : CartyRepository {

    private val TAG = "OfflineCartyRepo"
    private val firestore = FirebaseFirestore.getInstance()

    private fun getCurrentUserId(): String = firebaseAuth.currentUser?.uid ?: CartyApplication.GUEST_USER_ID
    override fun isUserLoggedIn(): Boolean = firebaseAuth.currentUser != null
    private fun isUserGuest(): Boolean = firebaseAuth.currentUser == null

    private suspend fun <T> safeFirestoreSet(collectionPath: String, docId: String, data: T) {
        if (isUserGuest() || docId.isBlank()) return
        try {
            firestore.collection(collectionPath).document(docId).set(data as Any).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting document $docId in $collectionPath", e)
        }
    }

    private suspend fun safeFirestoreDelete(collectionPath: String, docId: String) {
        if (isUserGuest() || docId.isBlank()) return
        try {
            firestore.collection(collectionPath).document(docId).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting document $docId from $collectionPath", e)
        }
    }

    // --- Department ---
    override suspend fun addDepartment(department: Department) {
        val userId = getCurrentUserId()
        val newId = System.currentTimeMillis()
        val newDepartment = department.copy(departmentId = newId, ownerId = userId, firestoreId = newId.toString())
        departmentDao.addDepartment(newDepartment)
        safeFirestoreSet("departments", newDepartment.firestoreId, newDepartment)
    }

    override suspend fun updateDepartment(department: Department) {
        departmentDao.updateDepartment(department)
        safeFirestoreSet("departments", department.firestoreId, department)
    }

    override suspend fun updateDepartments(departments: List<Department>) {
        departmentDao.updateDepartments(departments)
        batchSet("departments", departments) { it.firestoreId }
    }

    override fun getAllDepartmentsList(): Flow<List<Department>> = departmentDao.getAllDepartmentsList(getCurrentUserId())
    override fun getDepartmentById(departmentIdForSearch: Long): Flow<Department?> = departmentDao.getDepartmentById(departmentIdForSearch, getCurrentUserId())

    override suspend fun deleteDepartment(department: Department) {
        database.withTransaction {
            productDao.resetDepartmentIdForOwner(department.departmentId, department.ownerId)
            departmentDao.deleteDepartment(department)
        }
        if (isUserGuest()) return

        val productsToUpdate = firestore.collection("products").whereEqualTo("departmentId", department.departmentId).whereEqualTo("ownerId", department.ownerId).get().await()
        val batch = firestore.batch()
        productsToUpdate.documents.forEach { doc -> batch.update(doc.reference, "departmentId", null) }
        batch.delete(firestore.collection("departments").document(department.firestoreId))
        try { batch.commit().await() } catch(e: Exception) { Log.e(TAG, "Error deleting department", e) }
    }

    // --- Product ---
    override suspend fun addProduct(product: Product) {
        val userId = getCurrentUserId()
        val newId = System.currentTimeMillis()
        val newProduct = product.copy(productId = newId, ownerId = userId, firestoreId = newId.toString())
        productDao.addProduct(newProduct)
        safeFirestoreSet("products", newProduct.firestoreId, newProduct)
    }

    override suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product)
        safeFirestoreSet("products", product.firestoreId, product)
    }

    override suspend fun updateProducts(products: List<Product>) {
        productDao.updateProducts(products)
        batchSet("products", products) { it.firestoreId }
    }

    override fun getAllProductsList(): Flow<List<Product>> = productDao.getAllProductsList(getCurrentUserId())
    override fun getProductById(productIdForSearch: Long): Flow<Product?> = productDao.getProductById(productIdForSearch, getCurrentUserId())
    override fun getProductByName(productNameForSearch: String): Flow<List<Product>> = productDao.getProductsByName(productNameForSearch, getCurrentUserId())
    override suspend fun resetDepartmentId(departmentIdToDelete: Long) {} // Logic is now inside deleteDepartment

    override suspend fun deleteProduct(product: Product) {
        database.withTransaction {
            shoppingListItemDao.deleteItemsByProductIdAndOwnerId(product.productId, product.ownerId)
            productDao.deleteProduct(product)
        }
        if (isUserGuest()) return

        val itemsToDelete = firestore.collection("shoppingListItems").whereEqualTo("productId", product.productId).whereEqualTo("ownerId", product.ownerId).get().await()
        val batch = firestore.batch()
        itemsToDelete.documents.forEach { doc -> batch.delete(doc.reference) }
        batch.delete(firestore.collection("products").document(product.firestoreId))
        try { batch.commit().await() } catch(e: Exception) { Log.e(TAG, "Error deleting product", e) }
    }

    // --- ShoppingList ---
    override suspend fun addShoppingList(shoppingList: ShoppingList): Long {
        val userId = getCurrentUserId()
        val newId = System.currentTimeMillis()
        val newList = shoppingList.copy(shoppingListId = newId, ownerId = userId, firestoreId = newId.toString())
        shoppingListDao.addShoppingList(newList)
        safeFirestoreSet("shoppingLists", newList.firestoreId, newList)
        return newId
    }

    override suspend fun updateShoppingList(shoppingList: ShoppingList) {
        shoppingListDao.updateShoppingList(shoppingList)
        safeFirestoreSet("shoppingLists", shoppingList.firestoreId, shoppingList)
    }

    override suspend fun updateShoppingLists(shoppingLists: List<ShoppingList>) {
        shoppingListDao.updateShoppingLists(shoppingLists)
        batchSet("shoppingLists", shoppingLists) { it.firestoreId }
    }

    override fun getActiveAndFavoriteLists(): Flow<List<ShoppingList>> = shoppingListDao.getActiveAndFavoriteLists(getCurrentUserId())
    override fun getShoppingListById(shoppingListIdForSearch: Long): Flow<ShoppingList?> = shoppingListDao.getShoppingListById(shoppingListIdForSearch, getCurrentUserId())

    override suspend fun deleteShoppingList(shoppingList: ShoppingList) {
        database.withTransaction {
            shoppingListItemDao.deleteShoppingListItemsByListIdAndOwnerId(shoppingList.shoppingListId, shoppingList.ownerId)
            shoppingListDao.deleteShoppingList(shoppingList)
        }
        if (isUserGuest()) return

        val itemsToDelete = firestore.collection("shoppingListItems").whereEqualTo("shoppingListId", shoppingList.shoppingListId).whereEqualTo("ownerId", shoppingList.ownerId).get().await()
        val batch = firestore.batch()
        itemsToDelete.documents.forEach { doc -> batch.delete(doc.reference) }
        batch.delete(firestore.collection("shoppingLists").document(shoppingList.firestoreId))
        try { batch.commit().await() } catch(e: Exception) { Log.e(TAG, "Error deleting shopping list", e) }
    }

    // --- ShoppingListItem ---
    override suspend fun addShoppingListItem(shoppingListItem: ShoppingListItem) {
        val userId = getCurrentUserId()
        val newId = System.currentTimeMillis()
        val newItem = shoppingListItem.copy(shoppingListItemId = newId, ownerId = userId, firestoreId = newId.toString())
        shoppingListItemDao.addShoppingListItem(newItem)
        safeFirestoreSet("shoppingListItems", newItem.firestoreId, newItem)
    }

    override suspend fun updateShoppingListItem(shoppingListItem: ShoppingListItem) {
        shoppingListItemDao.updateShoppingListItem(shoppingListItem)
        safeFirestoreSet("shoppingListItems", shoppingListItem.firestoreId, shoppingListItem)
    }

    override suspend fun updateShoppingListItems(items: List<ShoppingListItem>) {
        shoppingListItemDao.updateShoppingListItems(items)
        batchSet("shoppingListItems", items) { it.firestoreId }
    }

    override suspend fun deleteShoppingListItem(shoppingListItem: ShoppingListItem) {
        shoppingListItemDao.deleteShoppingListItem(shoppingListItem)
        safeFirestoreDelete("shoppingListItems", shoppingListItem.firestoreId)
    }

    override fun getItemsForList(shoppingListIdForSearch: Long): Flow<List<ShoppingListItem>> = shoppingListItemDao.getItemsForList(shoppingListIdForSearch, getCurrentUserId())
    override fun getShoppingListItemById(shoppingListItemIdForSearch: Long): Flow<ShoppingListItem?> = shoppingListItemDao.getShoppingListItemById(shoppingListItemIdForSearch, getCurrentUserId())
    override suspend fun deleteShoppingListItemsById(shoppingListIdForDel: Long) {} // Covered by deleteShoppingList
    override suspend fun deleteShoppingListItemsByProductId(productIdForDel: Long) {} // Covered by deleteProduct

    // --- Auth & Sync ---
    override suspend fun fetchAndOverwriteLocalData() {
        if (isUserGuest()) return
        val userId = getCurrentUserId()

        try {
            database.withTransaction {
                clearAllDataForOwner(userId)

                val departments = fetchCollection<Department>("departments", userId, Department::class.java) { copy(firestoreId = it) }
                if (departments.isNotEmpty()) departmentDao.updateDepartments(departments)

                val products = fetchCollection<Product>("products", userId, Product::class.java) { copy(firestoreId = it) }
                if (products.isNotEmpty()) productDao.updateProducts(products)

                val lists = fetchCollection<ShoppingList>("shoppingLists", userId, ShoppingList::class.java) { copy(firestoreId = it) }
                if (lists.isNotEmpty()) shoppingListDao.updateShoppingLists(lists)

                val items = fetchCollection<ShoppingListItem>("shoppingListItems", userId, ShoppingListItem::class.java) { copy(firestoreId = it) }
                if (items.isNotEmpty()) shoppingListItemDao.updateShoppingListItems(items)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching and overwriting local data", e)
        }
    }

    override suspend fun clearLocalGuestData() = clearAllDataForOwner(CartyApplication.GUEST_USER_ID)
    override suspend fun clearLocalUserData(userId: String) = clearAllDataForOwner(userId)
    override suspend fun clearLocalUserDataOnSignOut() { firebaseAuth.currentUser?.uid?.let { clearLocalUserData(it) } }

    override suspend fun checkIfUserHasDataOnServer(): Boolean {
        if (isUserGuest()) return false
        val userId = getCurrentUserId()
        return try {
            !firestore.collection("shoppingLists").whereEqualTo("ownerId", userId).limit(1).get().await().isEmpty
        } catch (e: Exception) { false }
    }

    // --- Generic Helpers ---
    private suspend fun <T> fetchCollection(collection: String, userId: String, clazz: Class<T>, mapper: T.(String) -> T): List<T> {
        val snapshot = firestore.collection(collection).whereEqualTo("ownerId", userId).get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(clazz)?.mapper(doc.id)
        }
    }

    private suspend fun clearAllDataForOwner(ownerId: String) {
        database.withTransaction {
            val lists = shoppingListDao.getAllListsByOwnerId(ownerId).first()
            lists.forEach { list ->
                shoppingListItemDao.deleteShoppingListItemsByListIdAndOwnerId(list.shoppingListId, ownerId)
            }
            shoppingListDao.deleteAllByOwnerId(ownerId)
            productDao.deleteAllByOwnerId(ownerId)
            departmentDao.deleteAllByOwnerId(ownerId)
        }
    }

    private suspend fun <T: Any> batchSet(collection: String, items: List<T>, idExtractor: (T) -> String) {
        if (isUserGuest()) return
        val batch = firestore.batch()
        items.forEach {
            val docId = idExtractor(it)
            if (docId.isNotBlank()) {
                batch.set(firestore.collection(collection).document(docId), it)
            }
        }
        try { batch.commit().await() } catch(e: Exception) { Log.e(TAG, "Error batch setting $collection", e) }
    }
}