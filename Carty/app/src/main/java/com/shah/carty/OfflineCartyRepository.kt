package com.shah.carty

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class OfflineCartyRepository(
    private val departmentDao: DepartmentDao,
    private val productDao: ProductDao,
    private val shoppingListDao: ShoppingListDao,
    private val shoppingListItemDao: ShoppingListItemDao,
    private val firebaseAuth: FirebaseAuth
) : CartyRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private fun getCurrentUserId(): String {
        return firebaseAuth.currentUser?.uid ?: CartyApplication.GUEST_USER_ID
    }

    override fun isUserLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }

    private fun isUserGuest(): Boolean {
        return firebaseAuth.currentUser == null
    }

    private suspend fun safeFirestoreDelete(firestoreId: String, collectionPath: String) {
        if (!isUserGuest() && firestoreId.isNotBlank()) {
            try {
                firestore.collection(collectionPath).document(firestoreId).delete().await()
            } catch (e: Exception) {
                // Ошибка удаления из Firestore
            }
        }
    }

    override suspend fun addDepartment(department: Department) {
        val userId = getCurrentUserId()
        val departmentWithOwnerId = department.copy(ownerId = userId)
        departmentDao.addDepartment(departmentWithOwnerId)
        safeFirestoreCall(
            departmentWithOwnerId.firestoreId,
            "departments",
            departmentWithOwnerId,
            departmentWithOwnerId.firestoreId.isBlank()
        ) { newFsId, savedDept ->
            departmentDao.updateDepartment(savedDept.copy(firestoreId = newFsId))
        }
    }

    override suspend fun updateDepartment(department: Department) {
        val userId = getCurrentUserId()
        if (department.ownerId != userId && !(isUserGuest() && department.ownerId == CartyApplication.GUEST_USER_ID)) return
        val finalDepartment = if (isUserGuest() && department.ownerId != CartyApplication.GUEST_USER_ID) {
            department.copy(ownerId = CartyApplication.GUEST_USER_ID)
        } else if (!isUserGuest() && department.ownerId == CartyApplication.GUEST_USER_ID) {
            department.copy(ownerId = userId)
        }
        else {
            department
        }
        departmentDao.updateDepartment(finalDepartment)
        safeFirestoreCall(finalDepartment.firestoreId, "departments", finalDepartment, finalDepartment.firestoreId.isBlank() && !isUserGuest()) {newFsId, savedObject ->
            if (!isUserGuest()) departmentDao.updateDepartment(savedObject.copy(firestoreId = newFsId))
        }
    }

    override suspend fun deleteDepartment(department: Department) {
        val userId = getCurrentUserId()
        if (department.ownerId != userId) return
        val departmentFirestoreId = department.firestoreId
        departmentDao.deleteDepartment(department)
        safeFirestoreDelete(departmentFirestoreId, "departments")
    }

    override fun getAllDepartmentsList(): Flow<List<Department>> {
        return departmentDao.getAllDepartmentsList(getCurrentUserId())
    }

    override fun getDepartmentById(departmentIdForSearch: Long): Flow<Department?> {
        return departmentDao.getDepartmentById(departmentIdForSearch, getCurrentUserId())
    }

    override suspend fun addProduct(product: Product) {
        val userId = getCurrentUserId()
        val productWithOwnerId = product.copy(ownerId = userId)
        productDao.addProduct(productWithOwnerId)
        safeFirestoreCall(
            productWithOwnerId.firestoreId,
            "products",
            productWithOwnerId,
            productWithOwnerId.firestoreId.isBlank()
        ) { newFsId, savedProd ->
            productDao.updateProduct(savedProd.copy(firestoreId = newFsId))
        }
    }

    override suspend fun updateProduct(product: Product) {
        val userId = getCurrentUserId()
        if (product.ownerId != userId && !(isUserGuest() && product.ownerId == CartyApplication.GUEST_USER_ID)) return
        val finalProduct = if (isUserGuest() && product.ownerId != CartyApplication.GUEST_USER_ID) {
            product.copy(ownerId = CartyApplication.GUEST_USER_ID)
        } else if (!isUserGuest() && product.ownerId == CartyApplication.GUEST_USER_ID) {
            product.copy(ownerId = userId)
        }
        else {
            product
        }
        productDao.updateProduct(finalProduct)
        safeFirestoreCall(finalProduct.firestoreId, "products", finalProduct, finalProduct.firestoreId.isBlank() && !isUserGuest()) {newFsId, savedObject ->
            if(!isUserGuest()) productDao.updateProduct(savedObject.copy(firestoreId = newFsId))
        }
    }

    override suspend fun deleteProduct(product: Product) {
        val userId = getCurrentUserId()
        if (product.ownerId != userId) return

        val itemsToDelete = shoppingListItemDao.getAllItemsByProductIdAndOwnerId(product.productId, userId).first()
        itemsToDelete.forEach { item ->
            val parentList = shoppingListDao.getShoppingListById(item.shoppingListId, userId).first()
            if (parentList != null && !parentList.isCompleted) {
                deleteShoppingListItem(item)
            }
        }

        val productFirestoreId = product.firestoreId
        productDao.deleteProduct(product)
        safeFirestoreDelete(productFirestoreId, "products")
    }

    override fun getAllProductsList(): Flow<List<Product>> {
        return productDao.getAllProductsList(getCurrentUserId())
    }

    override fun getProductById(productIdForSearch: Long): Flow<Product?> {
        return productDao.getProductById(productIdForSearch, getCurrentUserId())
    }

    override fun getProductByName(productNameForSearch: String): Flow<List<Product>> {
        return productDao.getProductsByName(productNameForSearch, getCurrentUserId())
    }

    override suspend fun resetDepartmentId(departmentIdToDelete: Long) {
        val userId = getCurrentUserId()
        val productsToUpdateLocally = productDao.getProductsByDepartmentId(departmentIdToDelete, userId).first()
        productsToUpdateLocally.forEach {
            productDao.updateProduct(it.copy(departmentId = null))
        }

        if (!isUserGuest()) {
            val productsToUpdateFS = firestore.collection("products")
                .whereEqualTo("ownerId", userId)
                .whereEqualTo("departmentId", departmentIdToDelete)
                .get().await()
            val batch = firestore.batch()
            productsToUpdateFS.documents.forEach { doc ->
                batch.update(doc.reference, "departmentId", null)
            }
            try {
                batch.commit().await()
            } catch (e: Exception) {}
        }
    }

    override suspend fun addShoppingList(shoppingList: ShoppingList): Long {
        val userId = getCurrentUserId()
        val listWithOwnerId = shoppingList.copy(ownerId = userId)
        val localId = shoppingListDao.addShoppingList(listWithOwnerId)
        val listWithLocalIdAndOwner = listWithOwnerId.copy(shoppingListId = localId)

        safeFirestoreCall(
            listWithLocalIdAndOwner.firestoreId,
            "shoppingLists",
            listWithLocalIdAndOwner,
            listWithLocalIdAndOwner.firestoreId.isBlank()
        ) { newFsId, savedList ->
            shoppingListDao.updateShoppingList(savedList.copy(firestoreId = newFsId))
        }
        return localId
    }

    override suspend fun updateShoppingList(shoppingList: ShoppingList) {
        val userId = getCurrentUserId()
        if (shoppingList.ownerId != userId && !(isUserGuest() && shoppingList.ownerId == CartyApplication.GUEST_USER_ID)) return
        val finalList = if (isUserGuest() && shoppingList.ownerId != CartyApplication.GUEST_USER_ID) {
            shoppingList.copy(ownerId = CartyApplication.GUEST_USER_ID)
        } else if (!isUserGuest() && shoppingList.ownerId == CartyApplication.GUEST_USER_ID) {
            shoppingList.copy(ownerId = userId)
        }
        else {
            shoppingList
        }
        shoppingListDao.updateShoppingList(finalList)
        safeFirestoreCall(finalList.firestoreId, "shoppingLists", finalList, finalList.firestoreId.isBlank() && !isUserGuest()) {newFsId, savedObject ->
            if (!isUserGuest()) shoppingListDao.updateShoppingList(savedObject.copy(firestoreId = newFsId))
        }
    }

    override suspend fun deleteShoppingList(shoppingList: ShoppingList) {
        val userId = getCurrentUserId()
        if (shoppingList.ownerId != userId) return

        shoppingListItemDao.deleteShoppingListItemsByListIdAndOwnerId(shoppingList.shoppingListId, userId)

        val listFirestoreId = shoppingList.firestoreId
        shoppingListDao.deleteShoppingList(shoppingList)

        if (!isUserGuest() && listFirestoreId.isNotBlank()) {
            try {
                val itemsQuery = firestore.collection("shoppingListItems")
                    .whereEqualTo("listId", shoppingList.shoppingListId)
                    .whereEqualTo("ownerId", userId)
                    .get().await()
                val batch = firestore.batch()
                itemsQuery.documents.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()

                firestore.collection("shoppingLists").document(listFirestoreId).delete().await()
            } catch (e: Exception) {}
        }
    }

    override fun getActiveAndFavoriteLists(): Flow<List<ShoppingList>> {
        return shoppingListDao.getActiveAndFavoriteLists(getCurrentUserId())
    }

    override fun getShoppingListById(shoppingListIdForSearch: Long): Flow<ShoppingList?> {
        return shoppingListDao.getShoppingListById(shoppingListIdForSearch, getCurrentUserId())
    }

    override suspend fun addShoppingListItem(shoppingListItem: ShoppingListItem) {
        val userId = getCurrentUserId()
        val itemWithOwnerId = shoppingListItem.copy(ownerId = userId)
        val finalItem = itemWithOwnerId.copy(firestoreId = itemWithOwnerId.firestoreId.ifEmpty { "" })

        shoppingListItemDao.addShoppingListItem(finalItem)

        safeFirestoreCall(
            finalItem.firestoreId,
            "shoppingListItems",
            finalItem,
            finalItem.firestoreId.isBlank()
        ) { newFsId, savedItem ->
            if (newFsId.isNotBlank()) {
                shoppingListItemDao.updateShoppingListItem(finalItem.copy(firestoreId = newFsId))
            }
        }
    }

    override suspend fun updateShoppingListItem(shoppingListItem: ShoppingListItem) {
        val userId = getCurrentUserId()
        if (shoppingListItem.ownerId != userId && !(isUserGuest() && shoppingListItem.ownerId == CartyApplication.GUEST_USER_ID)) return
        val finalItem = if (isUserGuest() && shoppingListItem.ownerId != CartyApplication.GUEST_USER_ID) {
            shoppingListItem.copy(ownerId = CartyApplication.GUEST_USER_ID)
        } else if (!isUserGuest() && shoppingListItem.ownerId == CartyApplication.GUEST_USER_ID) {
            shoppingListItem.copy(ownerId = userId)
        }
        else {
            shoppingListItem
        }
        shoppingListItemDao.updateShoppingListItem(finalItem)
        safeFirestoreCall(finalItem.firestoreId, "shoppingListItems", finalItem, finalItem.firestoreId.isBlank() && !isUserGuest()) {newFsId, savedObject ->
            if(!isUserGuest()) shoppingListItemDao.updateShoppingListItem(savedObject.copy(firestoreId = newFsId))
        }
    }

    override suspend fun deleteShoppingListItem(shoppingListItem: ShoppingListItem) {
        val userId = getCurrentUserId()
        if (shoppingListItem.ownerId != userId) return
        val itemFirestoreId = shoppingListItem.firestoreId
        shoppingListItemDao.deleteShoppingListItem(shoppingListItem)
        safeFirestoreDelete(itemFirestoreId, "shoppingListItems")
    }

    override fun getItemsForList(shoppingListIdForSearch: Long): Flow<List<ShoppingListItem>> {
        return shoppingListItemDao.getItemsForList(shoppingListIdForSearch, getCurrentUserId())
    }

    override fun getShoppingListItemById(shoppingListItemIdForSearch: Long): Flow<ShoppingListItem?> {
        return shoppingListItemDao.getShoppingListItemById(shoppingListItemIdForSearch, getCurrentUserId())
    }

    override suspend fun deleteShoppingListItemsById(shoppingListIdForDel: Long) {
        shoppingListItemDao.deleteShoppingListItemsByListIdAndOwnerId(shoppingListIdForDel, getCurrentUserId())
    }

    override suspend fun deleteShoppingListItemsByProductId(productIdForDel: Long) {
        val userId = getCurrentUserId()
        val itemsToDelete = shoppingListItemDao.getAllItemsByProductIdAndOwnerId(productIdForDel, userId).first()
        itemsToDelete.forEach { item ->
            val parentList = shoppingListDao.getShoppingListById(item.shoppingListId, userId).first()
            if (parentList != null && !parentList.isCompleted) {
                deleteShoppingListItem(item)
            }
        }
    }

    private suspend fun <T> safeFirestoreCall(
        currentFirestoreId: String,
        collectionPath: String,
        dataObject: T,
        forceCreateNew: Boolean,
        updateLocalWithFirestoreId: suspend (String, T) -> Unit
    ) {
        if (!isUserGuest()) {
            try {
                if (forceCreateNew || currentFirestoreId.isBlank()) {
                    val docRef = firestore.collection(collectionPath).add(dataObject!!).await()
                    updateLocalWithFirestoreId(docRef.id, dataObject)
                } else {
                    firestore.collection(collectionPath).document(currentFirestoreId).set(dataObject!!).await()
                }
            } catch (e: Exception) {
                // Ошибка записи в Firestore
            }
        }
    }


    override suspend fun fetchAndOverwriteLocalData() {
        if (isUserGuest()) return
        val userId = getCurrentUserId()

        clearLocalUserData(userId)

        try {
            val departmentsFS = firestore.collection("departments").whereEqualTo("ownerId", userId).get().await().toObjects<Department>()
            departmentsFS.forEach { departmentDao.addDepartment(it) }

            val productsFS = firestore.collection("products").whereEqualTo("ownerId", userId).get().await().toObjects<Product>()
            productsFS.forEach { productDao.addProduct(it) }

            val listsFS = firestore.collection("shoppingLists").whereEqualTo("ownerId", userId).get().await().toObjects<ShoppingList>()
            listsFS.forEach { shoppingListDao.addShoppingList(it) }

            val itemsFS = firestore.collection("shoppingListItems").whereEqualTo("ownerId", userId).get().await().toObjects<ShoppingListItem>()
            itemsFS.forEach { shoppingListItemDao.addShoppingListItem(it) }
        } catch (e: Exception) {
            // Ошибка загрузки
        }
    }

    override suspend fun clearLocalGuestData() {
        val guestId = CartyApplication.GUEST_USER_ID
        departmentDao.deleteAllByOwnerId(guestId)
        productDao.deleteAllByOwnerId(guestId)
        val guestLists = shoppingListDao.getAllListsByOwnerId(guestId).first()
        guestLists.forEach { list ->
            shoppingListItemDao.deleteShoppingListItemsByListIdAndOwnerId(list.shoppingListId, guestId)
        }
        shoppingListDao.deleteAllByOwnerId(guestId)
    }

    override suspend fun clearLocalUserData(userId: String) {
        departmentDao.deleteAllByOwnerId(userId)
        productDao.deleteAllByOwnerId(userId)
        val userLists = shoppingListDao.getAllListsByOwnerId(userId).first()
        userLists.forEach { list ->
            shoppingListItemDao.deleteShoppingListItemsByListIdAndOwnerId(list.shoppingListId, userId)
        }
        shoppingListDao.deleteAllByOwnerId(userId)
    }

    override suspend fun checkIfUserHasDataOnServer(): Boolean {
        if (isUserGuest()) return false
        val userId = getCurrentUserId()
        return try {
            val snapshot = firestore.collection("shoppingLists")
                .whereEqualTo("ownerId", userId)
                .limit(1)
                .get()
                .await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun clearLocalUserDataOnSignOut() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId != null) {
            clearLocalUserData(userId)
        }
    }

}