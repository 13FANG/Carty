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

    override fun getItemViewType(position: Int): Int {
        val listForType = if (dragInProgress && internalList.isNotEmpty()) internalList else currentList
        if (position < 0 || position >= listForType.size) return -1
        return listForType[position].viewType
    }

    override fun submitList(list: List<DisplayableItem>?) {
        val listToSubmit = list ?: emptyList()
        Log.d("CartyDND", "Adapter submitList called. dragInProgress: $dragInProgress. List size: ${listToSubmit.size}. CurrentList size: ${currentList.size}")
        super.submitList(listToSubmit)
        if (!dragInProgress) {
            internalList = ArrayList(listToSubmit)
            Log.d("CartyDND", "Adapter submitList: dragInProgress is false. internalList updated from submitted list. internalList size: ${internalList.size}")
        } else {
            Log.d("CartyDND", "Adapter submitList: dragInProgress is true, internalList NOT updated from submitted list. internalList size: ${internalList.size}")
        }
    }

    override fun submitList(list: List<DisplayableItem>?, commitCallback: Runnable?) {
        val listToSubmit = list ?: emptyList()
        Log.d("CartyDND", "Adapter submitList (with callback) called. dragInProgress: $dragInProgress. List size: ${listToSubmit.size}. CurrentList size: ${currentList.size}")
        super.submitList(listToSubmit, commitCallback)
        if (!dragInProgress) {
            internalList = ArrayList(listToSubmit)
            Log.d("CartyDND", "Adapter submitList (with callback): dragInProgress is false. internalList updated. internalList size: ${internalList.size}")
        } else {
            Log.d("CartyDND", "Adapter submitList (with callback): dragInProgress is true, internalList NOT updated. internalList size: ${internalList.size}")
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

    override fun getItemCount(): Int {
        val count = if (dragInProgress && internalList.isNotEmpty()) internalList.size else super.getItemCount()
        Log.d("CartyDND_Count", "getItemCount: $count, dragInProgress: $dragInProgress, internalListSize: ${internalList.size}, super.getItemCount: ${super.getItemCount()}")
        return count
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (!dragInProgress) {
            internalList.clear()
            internalList.addAll(currentList)
            dragInProgress = true
        }

        if (fromPosition < 0 || fromPosition >= internalList.size ||
            toPosition < 0 || toPosition >= internalList.size) {
            return false
        }

        if (fromPosition == toPosition) return true

        val movingItem = internalList[fromPosition]
        val itemCurrentlyAtToPosition = internalList[toPosition]

        val movingItemName = if (movingItem is DisplayableItem.ShoppingListItemRow) movingItem.item.productName else if (movingItem is DisplayableItem.DepartmentHeader) movingItem.departmentName else "Unknown"
        val targetItemName = if (itemCurrentlyAtToPosition is DisplayableItem.ShoppingListItemRow) itemCurrentlyAtToPosition.item.productName else if (itemCurrentlyAtToPosition is DisplayableItem.DepartmentHeader) itemCurrentlyAtToPosition.departmentName else "Unknown"
        Log.d("CartyDND", "onItemMove: from=$fromPosition ($movingItemName), to=$toPosition ($targetItemName)")

        if (movingItem is DisplayableItem.DepartmentHeader) {
            if (itemCurrentlyAtToPosition !is DisplayableItem.DepartmentHeader) {
                Log.d("CartyDND", "  Reject: Header cannot move onto Item.")
                return false
            }
        } else if (movingItem is DisplayableItem.ShoppingListItemRow) {
            val movingItemActualDepartmentId = movingItem.item.departmentIdAtPurchase

            if (itemCurrentlyAtToPosition is DisplayableItem.DepartmentHeader) {
                Log.d("CartyDND", "  Reject: Item cannot move onto Header position.")
                return false
            } else if (itemCurrentlyAtToPosition is DisplayableItem.ShoppingListItemRow) {
                val targetItemActualDepartmentId = itemCurrentlyAtToPosition.item.departmentIdAtPurchase

                val normMoving = movingItemActualDepartmentId ?: ViewShoppingListViewModel.DEPARTMENT_ID_NO_DEPARTMENT
                val normTarget = targetItemActualDepartmentId ?: ViewShoppingListViewModel.DEPARTMENT_ID_NO_DEPARTMENT

                Log.d("CartyDND", "  ItemMove: Moving product from dept $normMoving. Target item is product in dept $normTarget.")

                if (normMoving != normTarget) {
                    Log.d("CartyDND", "  Reject: Product department mismatch ($normMoving != $normTarget).")
                    return false
                }
            } else {
                Log.e("CartyDND", "  ERROR: Target item at toPosition is unknown type for product move! Reject.")
                return false
            }
        }

        Log.d("CartyDND", "  Allowing move. Performing internal list update and notifying.")
        val itemToMove = internalList.removeAt(fromPosition)
        internalList.add(toPosition, itemToMove)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onDragFinished() {
        Log.d("CartyDND", "onDragFinished CALLED. dragInProgress: $dragInProgress")
        if (dragInProgress) {
            val finalList = ArrayList(internalList)
            Log.d("CartyDND", "onDragFinished: Calling onOrderChanged with finalList size: ${finalList.size}")
            onOrderChanged(finalList)

            // Немедленно обновляем адаптер этим же списком, чтобы зафиксировать визуальное состояние.
            // Это может помочь, если обновление от ViewModel приходит с задержкой или вызывает конфликт DiffUtil.
            // Но это также может вызвать двойное применение списка, если ViewModel быстро отреагирует.
            // super.submitList(finalList, null) // Пока закомментируем, посмотрим на эффект без этого.
        }
        dragInProgress = false
        listSnapshotBeforeDrag = null // Очищаем снимок
        Log.d("CartyDND", "onDragFinished: dragInProgress set to false.")
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