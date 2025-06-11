package com.shah.carty

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

@Entity
data class Department(
    @PrimaryKey val departmentId: Long = 0L,
    val departmentName: String = "",
    val ownerId: String = "",
    val manualSortIndex: Int = 0,
    @get:Exclude var firestoreId: String = ""
)

@Entity
data class Product(
    @PrimaryKey val productId: Long = 0L,
    val productName: String = "",
    val departmentId: Long? = null,
    val defaultUnit: ProductUnit = ProductUnit.PIECE,
    val defaultPrice: Double? = null,
    val ownerId: String = "",
    val manualSortIndex: Int = 0,
    @get:Exclude var firestoreId: String = ""
)

@Entity
data class ShoppingList(
    @PrimaryKey val shoppingListId: Long = 0L,
    val shoppingListName: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    @get:PropertyName("isFavorite") val isFavorite: Boolean = false,
    @get:PropertyName("isCompleted") val isCompleted: Boolean = false,
    val departmentOrder: List<Long> = emptyList(),
    val manualSortIndex: Int = 0,
    val ownerId: String = "",
    @get:Exclude var firestoreId: String = ""
)

@Entity
data class ShoppingListItem(
    @PrimaryKey val shoppingListItemId: Long = 0L,
    val shoppingListId: Long = 0L,
    val productId: Long = 0L,
    val productName: String = "",
    val quantity: Double = 0.0,
    val unit: ProductUnit = ProductUnit.PIECE,
    val price: Double? = null,
    @get:PropertyName("isBought") val isBought: Boolean = false,
    val departmentIdAtPurchase: Long? = null,
    val manualSortOrder: Int = 0,
    val ownerId: String = "",
    @get:Exclude var firestoreId: String = ""
)

enum class ProductUnit {
    PIECE, KILOGRAM, GRAM, LITER, MILLILITER, PACKAGE
}

fun ProductUnit.getDisplayName(context: Context): String {
    val resId = when (this) {
        ProductUnit.PIECE -> R.string.unit_piece
        ProductUnit.KILOGRAM -> R.string.unit_kilogram
        ProductUnit.GRAM -> R.string.unit_gram
        ProductUnit.LITER -> R.string.unit_liter
        ProductUnit.MILLILITER -> R.string.unit_milliliter
        ProductUnit.PACKAGE -> R.string.unit_package
    }
    return context.getString(resId)
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