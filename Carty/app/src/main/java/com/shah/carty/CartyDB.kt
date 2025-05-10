package com.shah.carty

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database (
    entities = [Department::class, Product::class, ShoppingList::class, ShoppingListItem::class],
    version = 1,
    exportSchema = false
)

@TypeConverters (Converters::class)

abstract class CartyDatabase : RoomDatabase(){
    abstract fun departmentDao(): DepartmentDao
    abstract fun productDao(): ProductDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun shoppingListItemDao(): ShoppingListItemDao
    companion object {
        private const val DATABASE_NAME = "carty_database"

        @Volatile
        private var INSTANCE : CartyDatabase? = null

        fun getInstance(context: Context): CartyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CartyDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}