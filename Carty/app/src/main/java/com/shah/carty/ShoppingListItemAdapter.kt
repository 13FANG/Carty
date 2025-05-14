package com.shah.carty

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.CompoundButtonCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.shah.carty.databinding.ItemShoppingListItemBinding

class ShoppingListItemAdapter(
    private val onItemCheckedChanged: (ShoppingListItem, Boolean) -> Unit,
    private val onDeleteItemClicked: (ShoppingListItem) -> Unit,
    private val onItemClicked: (ShoppingListItem) -> Unit
) : ListAdapter<ShoppingListItem, ShoppingListItemAdapter.ItemViewHolder>(ShoppingListItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemShoppingListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onItemCheckedChanged, onDeleteItemClicked, onItemClicked)
    }

    class ItemViewHolder(private val binding: ItemShoppingListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val context = itemView.context
        private val activeTextColor = ContextCompat.getColor(context, R.color.primary_text_color)
        private val boughtTextColor = ContextCompat.getColor(context, R.color.secondary_text_color)
        private val activeStrokeColor = ContextCompat.getColor(context, R.color.card_stroke_active_color)
        private val boughtStrokeColor = ContextCompat.getColor(context, R.color.card_stroke_bought_color)

        fun bind(
            item: ShoppingListItem,
            onItemCheckedChanged: (ShoppingListItem, Boolean) -> Unit,
            onDeleteItemClicked: (ShoppingListItem) -> Unit,
            onItemClicked: (ShoppingListItem) -> Unit
        ) {
            binding.itemNameTV.text = item.productName
            val unitName = item.unit.let { unit ->
                try {
                    context.getString(ProductUnitHelper.getDisplayNameResId(unit))
                } catch (e: Exception) {
                    unit.name.lowercase()
                }
            }
            val quantityText = "${item.quantity} $unitName"
            binding.itemQuantityTV.text = quantityText

            val pricePerUnitText = item.price?.let { "%.2f руб. за %s".format(it, unitName) } ?: "Цена не указана"
            binding.itemQuantityUnitTV2.text = pricePerUnitText

            binding.itemBoughtCB.setOnCheckedChangeListener(null)
            binding.itemBoughtCB.isChecked = item.isBought

            updateVisualState(item.isBought)

            binding.itemBoughtCB.setOnCheckedChangeListener { _, isChecked ->
                onItemCheckedChanged(item, isChecked)
                updateVisualState(isChecked)
            }

            binding.imageButton.setOnClickListener {
                onDeleteItemClicked(item)
            }
        }

        private fun updateVisualState(isBought: Boolean) {
            val currentTextColor = if (isBought) boughtTextColor else activeTextColor
            val currentStrokeColor = if (isBought) boughtStrokeColor else activeStrokeColor
            val checkboxTintColor = if (isBought) boughtTextColor else activeTextColor

            binding.itemNameTV.setTextColor(currentTextColor)
            binding.itemQuantityTV.setTextColor(currentTextColor)
            binding.itemQuantityUnitTV2.setTextColor(currentTextColor)

            binding.itemNameTV.paintFlags = binding.itemNameTV.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            binding.itemQuantityTV.paintFlags = binding.itemQuantityTV.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            binding.itemQuantityUnitTV2.paintFlags = binding.itemQuantityUnitTV2.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

            CompoundButtonCompat.setButtonTintList(binding.itemBoughtCB, android.content.res.ColorStateList.valueOf(checkboxTintColor))

            if (itemView is MaterialCardView) {
                (itemView as MaterialCardView).strokeColor = currentStrokeColor
            }
        }
    }
}

object ProductUnitHelper {
    fun getDisplayNameResId(unit: ProductUnit): Int {
        return when (unit) {
            ProductUnit.PIECE -> R.string.unit_piece
            ProductUnit.KILOGRAM -> R.string.unit_kilogram
            ProductUnit.GRAM -> R.string.unit_gram
            ProductUnit.LITER -> R.string.unit_liter
            ProductUnit.MILLILITER -> R.string.unit_milliliter
            ProductUnit.PACKAGE -> R.string.unit_package
        }
    }
}

class ShoppingListItemDiffCallback : DiffUtil.ItemCallback<ShoppingListItem>() {
    override fun areItemsTheSame(oldItem: ShoppingListItem, newItem: ShoppingListItem): Boolean {
        return oldItem.shoppingListItemId == newItem.shoppingListItemId
    }

    override fun areContentsTheSame(oldItem: ShoppingListItem, newItem: ShoppingListItem): Boolean {
        return oldItem == newItem
    }
}