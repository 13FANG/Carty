package com.shah.carty

import android.util.Log
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
    private val TAG = "OfflineCartyRepo"

    private fun getCurrentUserId(): String {
        return firebaseAuth.currentUser?.uid ?: CartyApplication.GUEST_USER_ID
    }

    override fun isUserLoggedIn(): Boolean {
        return firebaseAuth.currentUser != null
    }

    private fun isUserGuest(): Boolean {
        return firebaseAuth.currentUser == null
    }

    private suspend fun <T : Any> tryCreateInFirestoreAndUpdateLocal(
        collectionPath: String,
        localObjectWithLocalId: T, // Объект с уже сгенерированным локальным ID и ownerId
        updateLocalEntityWithFirestoreDocIdCall: suspend (T) -> Unit
    ) {
        if (isUserGuest()) { /* ... */ return }
        val firebaseUserUid = firebaseAuth.currentUser?.uid ?: return

        // Устанавливаем/проверяем ownerId. firestoreId теперь будет проигнорирован Firestore SDK благодаря @Exclude
        val objectToSend = when(localObjectWithLocalId){
            is Department -> (localObjectWithLocalId as Department).copy(ownerId = firebaseUserUid)
            is Product -> (localObjectWithLocalId as Product).copy(ownerId = firebaseUserUid)
            is ShoppingList -> (localObjectWithLocalId as ShoppingList).copy(ownerId = firebaseUserUid)
            is ShoppingListItem -> (localObjectWithLocalId as ShoppingListItem).copy(ownerId = firebaseUserUid)
            else -> localObjectWithLocalId
        }

        try {
            Log.d(TAG, "Firestore ($collectionPath): ADDING new doc for owner $firebaseUserUid: $objectToSend")
            val documentReference = firestore.collection(collectionPath).add(objectToSend).await() // objectToSend уже не будет иметь firestoreId как поле для Firestore
            val firestoreDocId = documentReference.id
            Log.d(TAG, "Firestore ($collectionPath): Successfully ADDED doc with ID: $firestoreDocId. Updating local entity.")

            val objectToUpdateLocallyWithFsId = when(localObjectWithLocalId){ // localObjectWithLocalId все еще содержит свой firestoreId (который был "" или старый)
                is Department -> (localObjectWithLocalId as Department).copy(firestoreId = firestoreDocId)
                is Product -> (localObjectWithLocalId as Product).copy(firestoreId = firestoreDocId)
                is ShoppingList -> (localObjectWithLocalId as ShoppingList).copy(firestoreId = firestoreDocId)
                is ShoppingListItem -> (localObjectWithLocalId as ShoppingListItem).copy(firestoreId = firestoreDocId)
                else -> localObjectWithLocalId
            }
            updateLocalEntityWithFirestoreDocIdCall(objectToUpdateLocallyWithFsId as T)
        } catch (e: Exception) {
            Log.e(TAG, "Firestore ($collectionPath): Error ADDING doc or UPDATING local with Firestore ID. Object: $objectToSend", e)
        }
    }

    private suspend fun <T : Any> tryUpdateOrCreateInFirestore(
        currentFirestoreIdFromLocal: String?,
        collectionPath: String,
        dataObject: T
    ) {
        if (isUserGuest()) { /* ... */ return }
        val firebaseUserUid = firebaseAuth.currentUser?.uid ?: return

        val objectToSend = when(dataObject){
            is Department -> (dataObject as Department).copy(ownerId = firebaseUserUid)
            is Product -> (dataObject as Product).copy(ownerId = firebaseUserUid)
            is ShoppingList -> (dataObject as ShoppingList).copy(ownerId = firebaseUserUid)
            is ShoppingListItem -> (dataObject as ShoppingListItem).copy(ownerId = firebaseUserUid)
            else -> dataObject
        }

        try {
            if (currentFirestoreIdFromLocal.isNullOrBlank()) {
                Log.d(TAG, "Firestore ($collectionPath): ADDING (on update with blank firestoreId) for owner $firebaseUserUid: $objectToSend")
                val documentReference = firestore.collection(collectionPath).add(objectToSend).await()
                val newFirestoreId = documentReference.id
                Log.d(TAG, "Firestore ($collectionPath): Successfully ADDED (on update) doc with ID: $newFirestoreId. Updating local with this ID.")
                val objectWithNewFsId = when(dataObject){
                    is Department -> (dataObject as Department).copy(firestoreId = newFirestoreId)
                    is Product -> (dataObject as Product).copy(firestoreId = newFirestoreId)
                    is ShoppingList -> (dataObject as ShoppingList).copy(firestoreId = newFirestoreId)
                    is ShoppingListItem -> (dataObject as ShoppingListItem).copy(firestoreId = newFirestoreId)
                    else -> dataObject
                }
                when(objectWithNewFsId) {
                    is Department -> departmentDao.updateDepartment(objectWithNewFsId)
                    is Product -> productDao.updateProduct(objectWithNewFsId)
                    is ShoppingList -> shoppingListDao.updateShoppingList(objectWithNewFsId)
                    is ShoppingListItem -> shoppingListItemDao.updateShoppingListItem(objectWithNewFsId)
                }
            } else {
                Log.d(TAG, "Firestore ($collectionPath): SETTING doc $currentFirestoreIdFromLocal for owner $firebaseUserUid: $objectToSend")
                firestore.collection(collectionPath).document(currentFirestoreIdFromLocal).set(objectToSend).await()
                Log.d(TAG, "Firestore ($collectionPath): Successfully SET doc $currentFirestoreIdFromLocal")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firestore ($collectionPath): Error writing (update/create) (localFsId: $currentFirestoreIdFromLocal). Object: $objectToSend", e)
        }
    }

    private suspend fun tryDeleteFromFirestoreInternal(firestoreId: String?, collectionPath: String) {
        if (isUserGuest() || firestoreId.isNullOrBlank()) {
            Log.d(TAG, "Firestore ($collectionPath): Delete skipped (guest or no fsId). FirestoreId: $firestoreId")
            return
        }
        try {
            Log.d(TAG, "Firestore ($collectionPath): DELETING doc $firestoreId")
            firestore.collection(collectionPath).document(firestoreId).delete().await()
            Log.d(TAG, "Firestore ($collectionPath): Successfully DELETED doc $firestoreId")
        } catch (e: Exception) {
            Log.e(TAG, "Firestore ($collectionPath): Error deleting (id: $firestoreId)", e)
        }
    }

    override suspend fun addDepartment(department: Department) {
        val userId = getCurrentUserId()
        val departmentToInsert = department.copy(ownerId = userId, firestoreId = "")
        Log.d(TAG, "addDepartment: 1. Initial object for Room: $departmentToInsert")
        val generatedLocalId = departmentDao.addDepartment(departmentToInsert)
        val departmentWithLocalId = departmentToInsert.copy(departmentId = generatedLocalId)
        Log.d(TAG, "addDepartment: 2. Saved to Room. Generated localId: $generatedLocalId. Object: $departmentWithLocalId")
        tryCreateInFirestoreAndUpdateLocal(
            "departments",
            departmentWithLocalId
        ) { entityToUpdateLocally ->
            Log.d(TAG, "addDepartment -> CALLBACK: 3. Attempting to update department in Room with Firestore ID: $entityToUpdateLocally")
            try {
                departmentDao.updateDepartment(entityToUpdateLocally as Department)
                Log.d(TAG, "addDepartment -> CALLBACK: 4. Department update in Room with Firestore ID finished successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "addDepartment -> CALLBACK: ERROR updating department in Room with Firestore ID", e)
            }
        }
    }

    override suspend fun updateDepartment(department: Department) {
        val userId = getCurrentUserId()
        val finalDepartment = if (!isUserGuest() && department.ownerId != userId && department.ownerId == CartyApplication.GUEST_USER_ID) {
            department.copy(ownerId = userId)
        } else if (department.ownerId != userId) { return } else { department }
        Log.d(TAG, "updateDepartment: To Room: ${finalDepartment.departmentName}")
        departmentDao.updateDepartment(finalDepartment)
        tryUpdateOrCreateInFirestore(finalDepartment.firestoreId, "departments", finalDepartment)
    }

    override suspend fun deleteDepartment(department: Department) {
        val userId = getCurrentUserId()
        if (department.ownerId != userId) { return }
        val fsId = department.firestoreId
        Log.d(TAG, "deleteDepartment: From Room: ${department.departmentName}")
        departmentDao.deleteDepartment(department)
        tryDeleteFromFirestoreInternal(fsId, "departments")
    }

    override fun getAllDepartmentsList(): Flow<List<Department>> {
        return departmentDao.getAllDepartmentsList(getCurrentUserId())
    }

    override fun getDepartmentById(departmentIdForSearch: Long): Flow<Department?> {
        return departmentDao.getDepartmentById(departmentIdForSearch, getCurrentUserId())
    }

    override suspend fun addProduct(product: Product) {
        val userId = getCurrentUserId()
        val productToInsert = product.copy(ownerId = userId, firestoreId = "")
        Log.d(TAG, "addProduct: 1. Initial object for Room: $productToInsert")
        val generatedLocalId = productDao.addProduct(productToInsert)
        val productWithLocalId = productToInsert.copy(productId = generatedLocalId)
        Log.d(TAG, "addProduct: 2. Saved to Room. Generated localId: $generatedLocalId. Object: $productWithLocalId")
        tryCreateInFirestoreAndUpdateLocal(
            "products",
            productWithLocalId
        ) { entityToUpdateLocally ->
            Log.d(TAG, "addProduct -> CALLBACK: 3. Attempting to update product in Room with Firestore ID: $entityToUpdateLocally")
            try {
                productDao.updateProduct(entityToUpdateLocally as Product)
                Log.d(TAG, "addProduct -> CALLBACK: 4. Product update in Room with Firestore ID finished successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "addProduct -> CALLBACK: ERROR updating product in Room with Firestore ID", e)
            }
        }
    }

    override suspend fun updateProduct(product: Product) {
        val userId = getCurrentUserId()
        val finalProduct = if (!isUserGuest() && product.ownerId != userId && product.ownerId == CartyApplication.GUEST_USER_ID) {
            product.copy(ownerId = userId)
        } else if (product.ownerId != userId) { return } else { product }
        Log.d(TAG, "updateProduct: To Room: ${finalProduct.productName}")
        productDao.updateProduct(finalProduct)
        tryUpdateOrCreateInFirestore(finalProduct.firestoreId, "products", finalProduct)
    }

    override suspend fun deleteProduct(product: Product) {
        val userId = getCurrentUserId()
        if (product.ownerId != userId) return
        val itemsToDelete = shoppingListItemDao.getAllItemsByProductIdAndOwnerId(product.productId, userId).first()
        itemsToDelete.forEach { item ->
            val parentList = shoppingListDao.getShoppingListById(item.shoppingListId, userId).first()
            if (parentList != null && !parentList.isCompleted) { deleteShoppingListItem(item) }
        }
        val fsId = product.firestoreId
        Log.d(TAG, "deleteProduct: From Room: ${product.productName}")
        productDao.deleteProduct(product)
        tryDeleteFromFirestoreInternal(fsId, "products")
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
            val updatedProduct = it.copy(departmentId = null)
            productDao.updateProduct(updatedProduct)
            tryUpdateOrCreateInFirestore(updatedProduct.firestoreId, "products", updatedProduct)
        }
    }

    override suspend fun addShoppingList(shoppingList: ShoppingList): Long {
        val userId = getCurrentUserId()
        val listToInsert = shoppingList.copy(ownerId = userId, firestoreId = "")
        Log.d(TAG, "addShoppingList: 1. Initial object for Room: $listToInsert")
        val generatedLocalId = shoppingListDao.addShoppingList(listToInsert) // Возвращает ID
        val listWithLocalId = listToInsert.copy(shoppingListId = generatedLocalId)
        Log.d(TAG, "addShoppingList: 2. Saved to Room. Generated localId: $generatedLocalId. Object: $listWithLocalId")
        tryCreateInFirestoreAndUpdateLocal(
            "shoppingLists",
            listWithLocalId
        ) { entityToUpdateLocally ->
            Log.d(TAG, "addShoppingList -> CALLBACK: 3. Attempting to update list in Room with Firestore ID: $entityToUpdateLocally")
            try {
                shoppingListDao.updateShoppingList(entityToUpdateLocally as ShoppingList)
                Log.d(TAG, "addShoppingList -> CALLBACK: 4. List update in Room with Firestore ID finished successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "addShoppingList -> CALLBACK: ERROR updating list in Room with Firestore ID", e)
            }
        }
        return generatedLocalId
    }

    override suspend fun updateShoppingList(shoppingList: ShoppingList) {
        val userId = getCurrentUserId()
        val finalList = if (!isUserGuest() && shoppingList.ownerId != userId && shoppingList.ownerId == CartyApplication.GUEST_USER_ID) {
            shoppingList.copy(ownerId = userId)
        } else if (shoppingList.ownerId != userId) { return } else { shoppingList }
        Log.d(TAG, "updateShoppingList: To Room: ${finalList.shoppingListName}")
        shoppingListDao.updateShoppingList(finalList)
        tryUpdateOrCreateInFirestore(finalList.firestoreId, "shoppingLists", finalList)
    }

    override suspend fun deleteShoppingList(shoppingList: ShoppingList) {
        val userId = getCurrentUserId()
        if (shoppingList.ownerId != userId) return
        if (!isUserGuest()) {
            try {
                val itemsQuery = firestore.collection("shoppingListItems")
                    .whereEqualTo("ownerId", userId).whereEqualTo("shoppingListId", shoppingList.shoppingListId).get().await()
                val batch = firestore.batch()
                itemsQuery.documents.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            } catch (e: Exception) { Log.e(TAG, "Error deleting items from FS for list ${shoppingList.shoppingListId}", e) }
        }
        shoppingListItemDao.deleteShoppingListItemsByListIdAndOwnerId(shoppingList.shoppingListId, userId)
        val listFirestoreId = shoppingList.firestoreId
        Log.d(TAG, "deleteShoppingList: From Room: ${shoppingList.shoppingListName}")
        shoppingListDao.deleteShoppingList(shoppingList)
        tryDeleteFromFirestoreInternal(listFirestoreId, "shoppingLists")
    }

    override fun getActiveAndFavoriteLists(): Flow<List<ShoppingList>> {
        return shoppingListDao.getActiveAndFavoriteLists(getCurrentUserId())
    }

    override fun getShoppingListById(shoppingListIdForSearch: Long): Flow<ShoppingList?> {
        return shoppingListDao.getShoppingListById(shoppingListIdForSearch, getCurrentUserId())
    }

    override suspend fun addShoppingListItem(shoppingListItem: ShoppingListItem) {
        val userId = getCurrentUserId()
        val itemToInsert = shoppingListItem.copy(ownerId = userId, firestoreId = "")
        Log.d(TAG, "addShoppingListItem: 1. Initial object for Room: $itemToInsert")
        val generatedLocalId = shoppingListItemDao.addShoppingListItem(itemToInsert) // Предполагаем, что DAO возвращает ID
        val itemWithLocalId = itemToInsert.copy(shoppingListItemId = generatedLocalId)
        Log.d(TAG, "addShoppingListItem: 2. Saved to Room. Generated localId: $generatedLocalId. Object: $itemWithLocalId")
        tryCreateInFirestoreAndUpdateLocal(
            "shoppingListItems",
            itemWithLocalId
        ) { entityToUpdateLocally ->
            Log.d(TAG, "addShoppingListItem -> CALLBACK: 3. Attempting to update item in Room with Firestore ID: $entityToUpdateLocally")
            try {
                shoppingListItemDao.updateShoppingListItem(entityToUpdateLocally as ShoppingListItem)
                Log.d(TAG, "addShoppingListItem -> CALLBACK: 4. Item update in Room with Firestore ID finished successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "addShoppingListItem -> CALLBACK: ERROR updating item in Room with Firestore ID", e)
            }
        }
    }

    override suspend fun updateShoppingListItem(shoppingListItem: ShoppingListItem) {
        val userId = getCurrentUserId()
        val finalItem = if (!isUserGuest() && shoppingListItem.ownerId != userId && shoppingListItem.ownerId == CartyApplication.GUEST_USER_ID) {
            shoppingListItem.copy(ownerId = userId)
        } else if (shoppingListItem.ownerId != userId) { return } else { shoppingListItem }
        Log.d(TAG, "updateShoppingListItem: To Room: ${finalItem.productName}")
        shoppingListItemDao.updateShoppingListItem(finalItem)
        tryUpdateOrCreateInFirestore(finalItem.firestoreId, "shoppingListItems", finalItem)
    }

    override suspend fun deleteShoppingListItem(shoppingListItem: ShoppingListItem) {
        val userId = getCurrentUserId()
        if (shoppingListItem.ownerId != userId) return
        val fsId = shoppingListItem.firestoreId
        Log.d(TAG, "deleteShoppingListItem: From Room: ${shoppingListItem.productName}")
        shoppingListItemDao.deleteShoppingListItem(shoppingListItem)
        tryDeleteFromFirestoreInternal(fsId, "shoppingListItems")
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
        val itemsToDeleteLocally = shoppingListItemDao.getAllItemsByProductIdAndOwnerId(productIdForDel, userId).first()
        itemsToDeleteLocally.forEach { item ->
            val parentList = shoppingListDao.getShoppingListById(item.shoppingListId, userId).first()
            if (parentList != null && !parentList.isCompleted) { deleteShoppingListItem(item) }
        }
    }

    override suspend fun fetchAndOverwriteLocalData() {
        if (isUserGuest()) { Log.d(TAG, "Fetch: User is guest, skipping."); return }
        val userId = getCurrentUserId()
        Log.d(TAG, "Fetch: Starting for user $userId")
        clearLocalUserData(userId)
        Log.d(TAG, "Fetch: Local data cleared for $userId")
        try {
            firestore.collection("departments").whereEqualTo("ownerId", userId).get().await().documents.forEach { doc ->
                try { doc.toObject<Department>()?.let { departmentDao.addDepartment(it.copy(firestoreId = doc.id, ownerId = userId)) }
                } catch (e: Exception) { Log.e(TAG, "Fetch: Error deserializing department doc: ${doc.id}", e) }
            }
            firestore.collection("products").whereEqualTo("ownerId", userId).get().await().documents.forEach { doc ->
                try { doc.toObject<Product>()?.let { productDao.addProduct(it.copy(firestoreId = doc.id, ownerId = userId)) }
                } catch (e: Exception) { Log.e(TAG, "Fetch: Error deserializing product doc: ${doc.id}", e) }
            }
            firestore.collection("shoppingLists").whereEqualTo("ownerId", userId).get().await().documents.forEach { doc ->
                try { doc.toObject<ShoppingList>()?.let { shoppingListDao.addShoppingList(it.copy(firestoreId = doc.id, ownerId = userId)) } // addShoppingList возвращает Long, здесь он не используется
                } catch (e: Exception) { Log.e(TAG, "Fetch: Error deserializing shoppingList doc: ${doc.id}", e) }
            }
            firestore.collection("shoppingListItems").whereEqualTo("ownerId", userId).get().await().documents.forEach { doc ->
                try { doc.toObject<ShoppingListItem>()?.let { shoppingListItemDao.addShoppingListItem(it.copy(firestoreId = doc.id, ownerId = userId)) }
                } catch (e: Exception) { Log.e(TAG, "Fetch: Error deserializing shoppingListItem doc: ${doc.id}", e) }
            }
            Log.d(TAG, "Fetch: Data fetch and overwrite complete for $userId")
        } catch (e: Exception) { Log.e(TAG, "Fetch: General error during data fetch for $userId", e) }
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
        Log.d(TAG, "Local guest data cleared.")
    }

    override suspend fun clearLocalUserData(userId: String) {
        if (userId == CartyApplication.GUEST_USER_ID) { return }
        departmentDao.deleteAllByOwnerId(userId)
        productDao.deleteAllByOwnerId(userId)
        val userLists = shoppingListDao.getAllListsByOwnerId(userId).first()
        userLists.forEach { list ->
            shoppingListItemDao.deleteShoppingListItemsByListIdAndOwnerId(list.shoppingListId, userId)
        }
        shoppingListDao.deleteAllByOwnerId(userId)
        Log.d(TAG, "Local data cleared for user $userId")
    }

    override suspend fun checkIfUserHasDataOnServer(): Boolean {
        if (isUserGuest()) return false
        val userId = getCurrentUserId()
        return try {
            !firestore.collection("shoppingLists").whereEqualTo("ownerId", userId).limit(1).get().await().isEmpty
        } catch (e: Exception) { false }
    }

    override suspend fun clearLocalUserDataOnSignOut() {
        val userId = firebaseAuth.currentUser?.uid
        if (userId != null) { clearLocalUserData(userId) }
    }
}