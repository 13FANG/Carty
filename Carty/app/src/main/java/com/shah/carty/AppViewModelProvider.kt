package com.shah.carty

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.firebase.auth.FirebaseAuth

class AppViewModelProvider(
    private val repository: CartyRepository,
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val savedStateHandle = extras.createSavedStateHandle()
        val firebaseAuth = FirebaseAuth.getInstance()
        val stringProviderLambda: (Int) -> String = { resId -> application.getString(resId) }
        val cartyApplication = application as CartyApplication


        return when {
            modelClass.isAssignableFrom(DepartmentsViewModel::class.java) ->
                DepartmentsViewModel(repository, cartyApplication) as T
            modelClass.isAssignableFrom(EditDepartmentViewModel::class.java) ->
                EditDepartmentViewModel(repository, savedStateHandle, cartyApplication) as T
            modelClass.isAssignableFrom(ProductsViewModel::class.java) ->
                ProductsViewModel(repository, cartyApplication) as T
            modelClass.isAssignableFrom(EditProductViewModel::class.java) ->
                EditProductViewModel(repository, savedStateHandle, stringProviderLambda, cartyApplication) as T
            modelClass.isAssignableFrom(ShoppingListsViewModel::class.java) ->
                ShoppingListsViewModel(repository, cartyApplication) as T
            modelClass.isAssignableFrom(ViewShoppingListViewModel::class.java) ->
                ViewShoppingListViewModel(repository, savedStateHandle, cartyApplication) as T
            modelClass.isAssignableFrom(LoginViewModel::class.java) ->
                LoginViewModel(repository, firebaseAuth, cartyApplication) as T
            modelClass.isAssignableFrom(RegisterViewModel::class.java) ->
                RegisterViewModel(repository, firebaseAuth, cartyApplication) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}