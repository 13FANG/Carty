package com.shah.carty

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database (
    entities = [Department::class, Product::class, ShoppingList::class, ShoppingListItem::class],
    version = 2,
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

        // Миграция с версии 1 на 2
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE Department ADD COLUMN manualSortIndex INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE Product ADD COLUMN manualSortIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): CartyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CartyDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}