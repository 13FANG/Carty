package com.shah.carty

import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemDepartmentHeaderBinding
import com.shah.carty.databinding.ItemShoppingListItemBinding
import java.util.ArrayList
import java.util.Collections

class ShoppingListItemAdapter(
    private val onItemCheckedChanged: (ShoppingListItem, Boolean) -> Unit,
    private val onDeleteItemClicked: (ShoppingListItem) -> Unit,
    private val onItemClicked: (ShoppingListItem) -> Unit,
    private val onHeaderClicked: (DisplayableItem.DepartmentHeader) -> Unit,
    private val onOrderChanged: (List<DisplayableItem>) -> Unit,
    private val getDepartmentName: (Long?) -> String
) : ListAdapter<DisplayableItem, RecyclerView.ViewHolder>(DisplayableItemDiffCallback()),
    ItemTouchHelperAdapter {

    private val internalList: MutableList<DisplayableItem> = ArrayList()
    var isGroupingEnabled: Boolean = true

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ITEM = 1
    }

    override fun submitList(list: List<DisplayableItem>?) {
        super.submitList(list) {
            internalList.clear()
            if (list != null) {
                internalList.addAll(list)
            }
        }
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    override fun isItemDraggable(position: Int): Boolean {
        if (position < 0 || position >= itemCount) return false
        return getItemViewType(position) == VIEW_TYPE_ITEM
    }

    override fun getItemViewType(position: Int): Int {
        return getItem(position).viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemDepartmentHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                DepartmentHeaderViewHolder(binding)
            }
            VIEW_TYPE_ITEM -> {
                val binding = ItemShoppingListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ItemViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val displayableItem = getItem(position)

        when (holder) {
            is DepartmentHeaderViewHolder -> {
                val headerItem = displayableItem as DisplayableItem.DepartmentHeader
                holder.bind(headerItem)
                holder.itemView.setOnClickListener { onHeaderClicked(headerItem) }
            }
            is ItemViewHolder -> {
                val shoppingListItemRow = displayableItem as DisplayableItem.ShoppingListItemRow
                holder.bind(shoppingListItemRow.item, this)
                holder.itemView.setOnClickListener {
                    onItemClicked(shoppingListItemRow.item)
                }
            }
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        val fromItem = internalList[fromPosition]
        val toItem = internalList[toPosition]

        // Защита от перетаскивания на заголовок или в другую группу
        if (isGroupingEnabled) {
            if (fromItem is DisplayableItem.ShoppingListItemRow && toItem is DisplayableItem.ShoppingListItemRow) {
                if (fromItem.item.departmentIdAtPurchase != toItem.item.departmentIdAtPurchase) {
                    return false
                }
            } else {
                return false
            }
        }

        Collections.swap(internalList, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onDragFinished() {
        onOrderChanged(ArrayList(internalList))
    }

    override fun onItemDismiss(position: Int) {}

    class DepartmentHeaderViewHolder(private val binding: ItemDepartmentHeaderBinding) :
        RecyclerView.ViewHolder(binding.root), ItemTouchHelperViewHolder {
        fun bind(header: DisplayableItem.DepartmentHeader) {
            binding.departmentHeaderName.text = header.departmentName
        }
        override fun onItemSelected() { itemView.alpha = 0.7f }
        override fun onItemClear() { itemView.alpha = 1.0f }
    }

    class ItemViewHolder(private val binding: ItemShoppingListItemBinding) :
        RecyclerView.ViewHolder(binding.root), ItemTouchHelperViewHolder {

        fun bind(item: ShoppingListItem, adapter: ShoppingListItemAdapter) {
            binding.itemNameTV.text = item.productName

            val context = itemView.context
            val unitDisplayName = item.unit.getDisplayName(context)
            val quantityText = "${item.quantity} $unitDisplayName"
            binding.itemQuantityTV.text = quantityText

            val pricePerUnitText = item.price?.let { "%.2f руб. за %s".format(it, unitDisplayName) } ?: "Цена не указана"
            binding.itemQuantityUnitTV.text = pricePerUnitText

            binding.itemBoughtCB.setOnCheckedChangeListener(null)
            binding.itemBoughtCB.isChecked = item.isBought

            if (!adapter.isGroupingEnabled) {
                binding.itemDepartmentNameTV.isVisible = true
                binding.itemDepartmentNameTV.text = adapter.getDepartmentName(item.departmentIdAtPurchase)
            } else {
                binding.itemDepartmentNameTV.isVisible = false
            }

            updateTextStyle(item.isBought)

            binding.itemBoughtCB.setOnCheckedChangeListener { _, isChecked ->
                adapter.onItemCheckedChanged(item, isChecked)
                updateTextStyle(isChecked)
            }

            binding.imageButton.setOnClickListener {
                adapter.onDeleteItemClicked(item)
            }
        }

        private fun updateTextStyle(isBought: Boolean) {
            val paintFlagsStrikeThru = Paint.STRIKE_THRU_TEXT_FLAG
            val paintFlagsClear = paintFlagsStrikeThru.inv()

            if (isBought) {
                binding.itemNameTV.paintFlags = binding.itemNameTV.paintFlags or paintFlagsStrikeThru
                binding.itemQuantityTV.paintFlags = binding.itemQuantityTV.paintFlags or paintFlagsStrikeThru
                binding.itemDepartmentNameTV.paintFlags = binding.itemDepartmentNameTV.paintFlags or paintFlagsStrikeThru
                binding.itemQuantityUnitTV.paintFlags = binding.itemQuantityUnitTV.paintFlags or paintFlagsStrikeThru
            } else {
                binding.itemNameTV.paintFlags = binding.itemNameTV.paintFlags and paintFlagsClear
                binding.itemQuantityTV.paintFlags = binding.itemQuantityTV.paintFlags and paintFlagsClear
                binding.itemDepartmentNameTV.paintFlags = binding.itemDepartmentNameTV.paintFlags and paintFlagsClear
                binding.itemQuantityUnitTV.paintFlags = binding.itemQuantityUnitTV.paintFlags and paintFlagsClear
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

class DisplayableItemDiffCallback : DiffUtil.ItemCallback<DisplayableItem>() {
    override fun areItemsTheSame(oldItem: DisplayableItem, newItem: DisplayableItem): Boolean {
        if (oldItem.viewType != newItem.viewType) return false
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: DisplayableItem, newItem: DisplayableItem): Boolean {
        return oldItem == newItem
    }
}