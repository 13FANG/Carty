package com.shah.carty

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObjects
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
            }
        }
    }

    override suspend fun addDepartment(department: Department) {
        val userId = getCurrentUserId()
        val departmentToInsert = department.copy(ownerId = userId, departmentId = 0L)
        val generatedLocalId = departmentDao.addDepartment(departmentToInsert)
        val departmentAfterRoomInsert = departmentToInsert.copy(departmentId = generatedLocalId, firestoreId = department.firestoreId)

        safeFirestoreCall(
            departmentAfterRoomInsert.firestoreId,
            "departments",
            departmentAfterRoomInsert,
            departmentAfterRoomInsert.firestoreId.isBlank()
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

        val productsToUpdateLocally = productDao.getProductsByDepartmentId(department.departmentId, userId).first()
        productsToUpdateLocally.forEach { product ->
            val updatedProduct = product.copy(departmentId = null)
            productDao.updateProduct(updatedProduct)
            if (!isUserGuest() && updatedProduct.firestoreId.isNotBlank()) {
                try {
                    firestore.collection("products").document(updatedProduct.firestoreId)
                        .update("departmentId", null).await()
                } catch (e: Exception) {
                }
            }
        }

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
        val productToInsert = product.copy(ownerId = userId, productId = 0L)
        val generatedLocalId = productDao.addProduct(productToInsert)
        val productAfterRoomInsert = productToInsert.copy(productId = generatedLocalId, firestoreId = product.firestoreId)

        safeFirestoreCall(
            productAfterRoomInsert.firestoreId,
            "products",
            productAfterRoomInsert,
            productAfterRoomInsert.firestoreId.isBlank()
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

        val itemsToUpdate = shoppingListItemDao.getAllItemsByProductIdAndOwnerId(product.productId, userId).first()
        itemsToUpdate.forEach { item ->
            val parentList = shoppingListDao.getShoppingListById(item.shoppingListId, userId).first()
            if (parentList != null && !parentList.isCompleted) {
                val itemToDeleteFirestoreId = item.firestoreId
                shoppingListItemDao.deleteShoppingListItem(item)
                safeFirestoreDelete(itemToDeleteFirestoreId, "shoppingListItems")
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
        val listToInsert = shoppingList.copy(ownerId = userId, shoppingListId = 0L)
        val generatedLocalId = shoppingListDao.addShoppingList(listToInsert)
        val listAfterRoomInsert = listToInsert.copy(shoppingListId = generatedLocalId, firestoreId = shoppingList.firestoreId)

        safeFirestoreCall(
            listAfterRoomInsert.firestoreId,
            "shoppingLists",
            listAfterRoomInsert,
            listAfterRoomInsert.firestoreId.isBlank()
        ) { newFsId, savedList ->
            shoppingListDao.updateShoppingList(savedList.copy(firestoreId = newFsId))
        }
        return generatedLocalId
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

    override suspend fun updateShoppingLists(shoppingLists: List<ShoppingList>) {
        val userId = getCurrentUserId()

        database.withTransaction {
            shoppingLists.forEach { list ->
                if (list.ownerId == userId || (isUserGuest() && list.ownerId == CartyApplication.GUEST_USER_ID)) {
                    val finalListForRoom = if (isUserGuest() && list.ownerId != CartyApplication.GUEST_USER_ID) {
                        list.copy(ownerId = CartyApplication.GUEST_USER_ID)
                    } else if (!isUserGuest() && list.ownerId == CartyApplication.GUEST_USER_ID) {
                        list.copy(ownerId = userId)
                    } else {
                        list
                    }
                    shoppingListDao.updateShoppingList(finalListForRoom)
                }
            }
        }

        if (!isUserGuest()) {
            val batch = firestore.batch()
            val firestoreIdsToUpdateInRoom = mutableMapOf<Long, String>()

            shoppingLists.forEach { listFromApp ->
                val listForFirestore = if (listFromApp.ownerId == CartyApplication.GUEST_USER_ID) {
                    listFromApp.copy(ownerId = userId)
                } else {
                    listFromApp
                }

                if (listForFirestore.ownerId == userId) {
                    if (listForFirestore.firestoreId.isNotBlank()) {
                        val docRef = firestore.collection("shoppingLists").document(listForFirestore.firestoreId)
                        batch.set(docRef, listForFirestore)
                    } else {
                        val newDocRef = firestore.collection("shoppingLists").document()
                        batch.set(newDocRef, listForFirestore.copy(firestoreId = newDocRef.id))
                        firestoreIdsToUpdateInRoom[listForFirestore.shoppingListId] = newDocRef.id
                    }
                }
            }
            if (firestoreIdsToUpdateInRoom.isNotEmpty() || shoppingLists.any { it.firestoreId.isNotBlank() && it.ownerId == userId }) {
                try {
                    batch.commit().await()
                    if (firestoreIdsToUpdateInRoom.isNotEmpty()) {
                        database.withTransaction {
                            firestoreIdsToUpdateInRoom.forEach { (localId, fsId) ->
                                val listToUpdate = shoppingListDao.getShoppingListById(localId, userId).first()
                                listToUpdate?.let {
                                    shoppingListDao.updateShoppingList(it.copy(firestoreId = fsId))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }
    }


    override suspend fun deleteShoppingList(shoppingList: ShoppingList) {
        val userId = getCurrentUserId()
        if (shoppingList.ownerId != userId) return

        val itemsInList = shoppingListItemDao.getItemsForList(shoppingList.shoppingListId, userId).first()
        val batch = firestore.batch()
        var itemsDeletedFromFirestore = false

        itemsInList.forEach { item ->
            shoppingListItemDao.deleteShoppingListItem(item)
            if (!isUserGuest() && item.firestoreId.isNotBlank()) {
                val itemDocRef = firestore.collection("shoppingListItems").document(item.firestoreId)
                batch.delete(itemDocRef)
                itemsDeletedFromFirestore = true
            }
        }
        if(itemsDeletedFromFirestore && !isUserGuest()){
            try {
                batch.commit().await()
            } catch (e: Exception) {}
        }


        val listFirestoreId = shoppingList.firestoreId
        shoppingListDao.deleteShoppingList(shoppingList)
        safeFirestoreDelete(listFirestoreId, "shoppingLists")
    }

    override fun getActiveAndFavoriteLists(): Flow<List<ShoppingList>> {
        return shoppingListDao.getActiveAndFavoriteLists(getCurrentUserId())
    }

    override fun getShoppingListById(shoppingListIdForSearch: Long): Flow<ShoppingList?> {
        return shoppingListDao.getShoppingListById(shoppingListIdForSearch, getCurrentUserId())
    }

    override suspend fun addShoppingListItem(shoppingListItem: ShoppingListItem) {
        val userId = getCurrentUserId()
        val itemToInsert = shoppingListItem.copy(ownerId = userId, shoppingListItemId = 0L)

        shoppingListItemDao.addShoppingListItem(itemToInsert)
        val insertedItemWithPossibleId = shoppingListItemDao.getItemsForList(itemToInsert.shoppingListId, userId).first().find {
            it.productId == itemToInsert.productId &&
                    it.productName == itemToInsert.productName &&
                    it.quantity == itemToInsert.quantity &&
                    it.price == itemToInsert.price &&
                    it.firestoreId.isBlank()
        } ?: itemToInsert


        safeFirestoreCall(
            insertedItemWithPossibleId.firestoreId,
            "shoppingListItems",
            insertedItemWithPossibleId,
            insertedItemWithPossibleId.firestoreId.isBlank()
        ) { newFsId, savedItem ->
            if (newFsId.isNotBlank()) {
                shoppingListItemDao.updateShoppingListItem(savedItem.copy(firestoreId = newFsId))
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
        val userId = getCurrentUserId()
        val itemsInList = shoppingListItemDao.getItemsForList(shoppingListIdForDel, userId).first()
        val batch = firestore.batch()
        var itemsDeletedFromFirestore = false

        itemsInList.forEach { item ->
            if (!isUserGuest() && item.firestoreId.isNotBlank()) {
                val itemDocRef = firestore.collection("shoppingListItems").document(item.firestoreId)
                batch.delete(itemDocRef)
                itemsDeletedFromFirestore = true
            }
        }
        if(itemsDeletedFromFirestore && !isUserGuest()){
            try {
                batch.commit().await()
            } catch (e: Exception) {}
        }
        shoppingListItemDao.deleteShoppingListItemsByListIdAndOwnerId(shoppingListIdForDel, getCurrentUserId())
    }

    override suspend fun deleteShoppingListItemsByProductId(productIdForDel: Long) {
        val userId = getCurrentUserId()
        val itemsToDelete = shoppingListItemDao.getAllItemsByProductIdAndOwnerId(productIdForDel, userId).first()
        val batch = firestore.batch()
        var itemsChangedInFirestore = false

        itemsToDelete.forEach { item ->
            val parentList = shoppingListDao.getShoppingListById(item.shoppingListId, userId).first()
            if (parentList != null && !parentList.isCompleted) {
                shoppingListItemDao.deleteShoppingListItem(item)
                if (!isUserGuest() && item.firestoreId.isNotBlank()) {
                    val itemDocRef = firestore.collection("shoppingListItems").document(item.firestoreId)
                    batch.delete(itemDocRef)
                    itemsChangedInFirestore = true
                }
            }
        }
        if(itemsChangedInFirestore && !isUserGuest()){
            try {
                batch.commit().await()
            } catch (e: Exception) {}
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
                    updateLocalWithFirestoreId(currentFirestoreId, dataObject)
                }
            } catch (e: Exception) {
            }
        }
    }


    override suspend fun fetchAndOverwriteLocalData() {
        if (isUserGuest()) return
        val userId = getCurrentUserId()

        clearLocalUserData(userId)

        try {
            val departmentsFS = firestore.collection("departments").whereEqualTo("ownerId", userId).get().await().toObjects<Department>()
            departmentsFS.forEach { dept ->
                val existing = departmentDao.getDepartmentById(dept.departmentId, userId).first()
                if (existing == null) departmentDao.addDepartment(dept) else departmentDao.updateDepartment(dept)
            }

            val productsFS = firestore.collection("products").whereEqualTo("ownerId", userId).get().await().toObjects<Product>()
            productsFS.forEach { prod ->
                val existing = productDao.getProductById(prod.productId, userId).first()
                if (existing == null) productDao.addProduct(prod) else productDao.updateProduct(prod)
            }

            val listsFS = firestore.collection("shoppingLists").whereEqualTo("ownerId", userId).get().await().toObjects<ShoppingList>()
            listsFS.forEach { list ->
                val existing = shoppingListDao.getShoppingListById(list.shoppingListId, userId).first()
                if (existing == null) shoppingListDao.addShoppingList(list) else shoppingListDao.updateShoppingList(list)

            }

            val itemsFS = firestore.collection("shoppingListItems").whereEqualTo("ownerId", userId).get().await().toObjects<ShoppingListItem>()
            itemsFS.forEach { item ->
                val existing = shoppingListItemDao.getShoppingListItemById(item.shoppingListItemId, userId).first()
                if (existing == null) shoppingListItemDao.addShoppingListItem(item) else shoppingListItemDao.updateShoppingListItem(item)
            }
        } catch (e: Exception) {
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