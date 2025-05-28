package com.shah.carty

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemShoppingListItemBinding
import java.util.ArrayList

class ShoppingListItemAdapter(
    private val onItemCheckedChanged: (ShoppingListItem, Boolean) -> Unit,
    private val onDeleteItemClicked: (ShoppingListItem) -> Unit,
    private val onItemClicked: (ShoppingListItem) -> Unit,
    private val onOrderChanged: (List<ShoppingListItem>) -> Unit
) : ListAdapter<ShoppingListItem, ShoppingListItemAdapter.ItemViewHolder>(ShoppingListItemDiffCallback()),
    ItemTouchHelperAdapter {

    private var internalList: MutableList<ShoppingListItem> = mutableListOf()
    private var dragInProgress = false

    override fun submitList(list: List<ShoppingListItem>?) {
        val listToSubmit = list ?: emptyList()
        super.submitList(listToSubmit)
        if (!dragInProgress) {
            internalList = ArrayList(listToSubmit)
        }
    }

    override fun submitList(list: List<ShoppingListItem>?, commitCallback: Runnable?) {
        val listToSubmit = list ?: emptyList()
        super.submitList(listToSubmit, commitCallback)
        if (!dragInProgress) {
            internalList = ArrayList(listToSubmit)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemShoppingListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item: ShoppingListItem = if (dragInProgress && position < internalList.size) {
            internalList[position]
        } else if (position < super.getItemCount()){
            getItem(position)
        } else {
            if (internalList.isNotEmpty()) internalList[0] else ShoppingListItem(productName = "Error")
        }
        holder.bind(item, onItemCheckedChanged, onDeleteItemClicked, onItemClicked)
    }

    override fun getItemCount(): Int {
        return if (dragInProgress) internalList.size else super.getItemCount()
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (!dragInProgress) {
            internalList.clear()
            internalList.addAll(currentList)
            dragInProgress = true
        }

        if (fromPosition < 0 || fromPosition >= internalList.size || toPosition < 0 || toPosition >= internalList.size) {
            return false
        }

        if (fromPosition == toPosition) {
            return true
        }

        val item = internalList.removeAt(fromPosition)
        internalList.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onDragFinished() {
        if (dragInProgress) {
            onOrderChanged(ArrayList(internalList))
        }
        dragInProgress = false
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

            itemView.setOnClickListener{
                onItemClicked(item)
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
            itemView.elevation = 8f
        }

        override fun onItemClear() {
            itemView.alpha = 1.0f
            itemView.elevation = 0f
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