package com.shah.carty

import android.app.Application
import com.google.firebase.auth.FirebaseAuth
import com.shah.carty.CartyRepository
import com.shah.carty.OfflineCartyRepository

class CartyApplication : Application() {

    val database: CartyDatabase by lazy { CartyDatabase.getInstance(this) }

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val repository: CartyRepository by lazy {
        OfflineCartyRepository(
            database.departmentDao(),
            database.productDao(),
            database.shoppingListDao(),
            database.shoppingListItemDao(),
            firebaseAuth
        )
    }

    fun getCurrentUserId(): String {
        return firebaseAuth.currentUser?.uid ?: GUEST_USER_ID
    }

    companion object {
        const val GUEST_USER_ID = "guest_user_carty_app"
    }
}