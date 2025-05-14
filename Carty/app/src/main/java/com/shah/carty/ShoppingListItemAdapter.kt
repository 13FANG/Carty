package com.shah.carty

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemShoppingListItemBinding
import java.util.Collections

class ShoppingListItemAdapter(
    private val onItemCheckedChanged: (ShoppingListItem, Boolean) -> Unit,
    private val onDeleteItemClicked: (ShoppingListItem) -> Unit,
    private val onItemClicked: (ShoppingListItem) -> Unit,
    private val onOrderChanged: (List<ShoppingListItem>) -> Unit
) : ListAdapter<ShoppingListItem, ShoppingListItemAdapter.ItemViewHolder>(ShoppingListItemDiffCallback()),
    ItemTouchHelperAdapter {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemShoppingListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onItemCheckedChanged, onDeleteItemClicked, onItemClicked)
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(currentList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(currentList, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onOrderChanged(ArrayList(currentList))
    }

    override fun onItemDismiss(position: Int) {}

    class ItemViewHolder(private val binding: ItemShoppingListItemBinding) :
        RecyclerView.ViewHolder(binding.root), ItemTouchHelperViewHolder {

        fun bind(
            item: ShoppingListItem,
            onItemCheckedChanged: (ShoppingListItem, Boolean) -> Unit,
            onDeleteItemClicked: (ShoppingListItem) -> Unit,
            onItemClicked: (ShoppingListItem) -> Unit
        ) {
            binding.itemNameTV.text = item.productName
            val quantityText = "${item.quantity} ${item.unit.name.lowercase()}"
            binding.itemQuantityTV.text = quantityText

            val pricePerUnitText = item.price?.let { "%.2f руб. за %s".format(it, item.unit.name.lowercase()) } ?: "Цена не указана"
            binding.itemQuantityUnitTV2.text = pricePerUnitText

            binding.itemBoughtCB.setOnCheckedChangeListener(null)
            binding.itemBoughtCB.isChecked = item.isBought

            updateTextStyle(item.isBought)

            binding.itemBoughtCB.setOnCheckedChangeListener { _, isChecked ->
                onItemCheckedChanged(item, isChecked)
                updateTextStyle(isChecked)
            }

            binding.imageButton.setOnClickListener {
                onDeleteItemClicked(item)
            }
        }

        private fun updateTextStyle(isBought: Boolean) {
            if (isBought) {
                binding.itemNameTV.paintFlags = binding.itemNameTV.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.itemQuantityTV.paintFlags = binding.itemQuantityTV.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.itemNameTV.paintFlags = binding.itemNameTV.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.itemQuantityTV.paintFlags = binding.itemQuantityTV.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
        }

        override fun onItemSelected() {
            itemView.alpha = 0.7f
        }

        override fun onItemClear() {
            itemView.alpha = 1.0f
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