package com.shah.carty

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity
data class Department(
    @PrimaryKey(autoGenerate = true) val departmentId: Long = 0L,
    val departmentName: String,
    val ownerId: String,
    val firestoreId: String = ""
)

@Entity
data class Product(
    @PrimaryKey(autoGenerate = true) val productId: Long = 0L,
    val productName: String,
    val departmentId: Long?,
    val defaultUnit: ProductUnit,
    val defaultPrice: Double?,
    val ownerId: String,
    val firestoreId: String = ""
)

@Entity
data class ShoppingList(
    @PrimaryKey(autoGenerate = true) val shoppingListId: Long = 0L,
    val shoppingListName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean = false,
    val isCompleted: Boolean = false,
    val departmentOrder: List<Long>,
    val manualSortIndex: Int,
    val ownerId: String,
    val firestoreId: String = ""
)

@Entity
data class ShoppingListItem(
    @PrimaryKey(autoGenerate = true) val shoppingListItemId: Long = 0L,
    val shoppingListId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val unit: ProductUnit,
    val price: Double?,
    val isBought: Boolean = false,
    val departmentIdAtPurchase: Long?,
    val manualSortOrder: Int,
    val ownerId: String,
    val firestoreId: String = ""
)

enum class ProductUnit {
    PIECE, KILOGRAM, GRAM, LITER, MILLILITER, PACKAGE
}
class Converters{
    @TypeConverter  fun fromUnit(unit: ProductUnit): String{
        return unit.name
    }
    @TypeConverter  fun toUnit(unitName: String): ProductUnit{
        return ProductUnit.valueOf(unitName)
    }
    @TypeConverter fun fromListLong(ListToConvert: List<Long>): String{
        return ListToConvert.joinToString(separator = ",")
    }
    @TypeConverter fun toListLong(stringToConvert: String): List<Long>{
        var listToReturn: List<Long> = emptyList()
        if (stringToConvert.isNotEmpty()) {
            listToReturn = stringToConvert.split(',').map { it.toLong() }
        }
        return listToReturn
    }
}