package com.shah.carty

import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
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
    private val onOrderChanged: (List<DisplayableItem>) -> Unit
) : ListAdapter<DisplayableItem, RecyclerView.ViewHolder>(DisplayableItemDiffCallback()),
    ItemTouchHelperAdapter {

    private var internalList: MutableList<DisplayableItem> = mutableListOf()
    private var dragInProgress = false
    private var listSnapshotBeforeDrag: List<DisplayableItem>? = null

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemCount(): Int {
        return if (dragInProgress && internalList.isNotEmpty()) internalList.size else super.getItemCount()
    }

    override fun isItemDraggable(position: Int): Boolean {
        if (position < 0 || position >= itemCount) return false
        // В ShoppingListItemAdapter перетаскивать можно только товары
        return getItemViewType(position) == VIEW_TYPE_ITEM
    }

    override fun getItemViewType(position: Int): Int {
        val listForType = if (dragInProgress && internalList.isNotEmpty()) internalList else currentList
        if (position < 0 || position >= listForType.size) return -1
        return listForType[position].viewType
    }

    override fun submitList(list: List<DisplayableItem>?) {
        val listToSubmit = list ?: emptyList()
        super.submitList(listToSubmit)
        if (!dragInProgress) {
            internalList = ArrayList(listToSubmit)
        }
    }

    override fun submitList(list: List<DisplayableItem>?, commitCallback: Runnable?) {
        val listToSubmit = list ?: emptyList()
        super.submitList(listToSubmit, commitCallback)
        if (!dragInProgress) {
            internalList = ArrayList(listToSubmit)
        }
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
        val listForBind = if (dragInProgress && internalList.isNotEmpty()) internalList else currentList
        if (position < 0 || position >= listForBind.size) return

        val displayableItem = listForBind[position]

        when (holder) {
            is DepartmentHeaderViewHolder -> {
                val headerItem = displayableItem as? DisplayableItem.DepartmentHeader ?: return
                holder.bind(headerItem)
                holder.itemView.setOnClickListener { onHeaderClicked(headerItem) }
            }
            is ItemViewHolder -> {
                val shoppingListItemRow = displayableItem as? DisplayableItem.ShoppingListItemRow ?: return
                holder.bind(shoppingListItemRow.item, onItemCheckedChanged, onDeleteItemClicked)
                holder.itemView.setOnClickListener {
                    onItemClicked(shoppingListItemRow.item)
                }
            }
        }
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (!dragInProgress) {
            listSnapshotBeforeDrag = ArrayList(currentList)
            internalList.clear()
            internalList.addAll(listSnapshotBeforeDrag!!)
            dragInProgress = true
        }

        if (fromPosition < 0 || fromPosition >= internalList.size ||
            toPosition < 0 || toPosition >= internalList.size) {
            return false
        }

        if (fromPosition == toPosition) return true

        val movingItem = internalList[fromPosition]
        val itemCurrentlyAtToPosition = internalList[toPosition]

        if (movingItem !is DisplayableItem.ShoppingListItemRow) {
            return false
        }
        if (itemCurrentlyAtToPosition is DisplayableItem.DepartmentHeader) {
            return false
        }

        if (itemCurrentlyAtToPosition is DisplayableItem.ShoppingListItemRow) {
            val movingItemActualDepartmentId = movingItem.item.departmentIdAtPurchase
            val targetItemActualDepartmentId = itemCurrentlyAtToPosition.item.departmentIdAtPurchase

            val normMoving = movingItemActualDepartmentId ?: ViewShoppingListViewModel.DEPARTMENT_ID_NO_DEPARTMENT
            val normTarget = targetItemActualDepartmentId ?: ViewShoppingListViewModel.DEPARTMENT_ID_NO_DEPARTMENT

            if (normMoving != normTarget) {
                return false
            }
        } else {
            return false
        }

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(internalList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(internalList, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onDragFinished() {
        if (dragInProgress) {
            onOrderChanged(ArrayList(internalList))
        }
        dragInProgress = false
        listSnapshotBeforeDrag = null
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

        fun bind(
            item: ShoppingListItem,
            onItemCheckedChanged: (ShoppingListItem, Boolean) -> Unit,
            onDeleteItemClicked: (ShoppingListItem) -> Unit
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