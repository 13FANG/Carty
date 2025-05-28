package com.shah.carty

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shah.carty.databinding.ItemShoppingListBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.ArrayList

class ShoppingListAdapter(
    private val onItemClicked: (ShoppingList) -> Unit,
    private val onOrderChanged: (List<ShoppingList>) -> Unit
) : ListAdapter<ShoppingList, ShoppingListAdapter.ShoppingListViewHolder>(ShoppingListDiffCallback()),
    ItemTouchHelperAdapter {

    private var internalList: MutableList<ShoppingList> = mutableListOf()
    private var dragInProgress = false

    override fun submitList(list: List<ShoppingList>?) {
        val listToSubmit = list ?: emptyList()
        super.submitList(listToSubmit)
        if (!dragInProgress) {
            internalList = ArrayList(listToSubmit)
        }
    }

    override fun submitList(list: List<ShoppingList>?, commitCallback: Runnable?) {
        val listToSubmit = list ?: emptyList()
        super.submitList(listToSubmit, commitCallback)
        if (!dragInProgress) {
            internalList = ArrayList(listToSubmit)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShoppingListViewHolder {
        val binding = ItemShoppingListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShoppingListViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShoppingListViewHolder, position: Int) {
        val shoppingList: ShoppingList = if (dragInProgress && position < internalList.size) {
            internalList[position]
        } else if (position < super.getItemCount()){
            getItem(position)
        } else {
            if (internalList.isNotEmpty()) internalList[0] else ShoppingList(shoppingListName = "Error")
        }
        holder.bind(shoppingList)
        holder.itemView.setOnClickListener {
            onItemClicked(shoppingList)
        }
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

    class ShoppingListViewHolder(private val binding: ItemShoppingListBinding) :
        RecyclerView.ViewHolder(binding.root), ItemTouchHelperViewHolder {

        private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        fun bind(shoppingList: ShoppingList) {
            binding.itemShoppingListNameTV.text = shoppingList.shoppingListName
            val dateString = dateFormat.format(Date(shoppingList.updatedAt))
            binding.infoProductItemTV.text = "Обновлен: $dateString"

            if (shoppingList.isFavorite) {
                binding.favIconIV.setImageResource(R.drawable.favoriteicon)
            } else {
                binding.favIconIV.setImageResource(R.drawable.notfavoriteicon)
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

class ShoppingListDiffCallback : DiffUtil.ItemCallback<ShoppingList>() {
    override fun areItemsTheSame(oldItem: ShoppingList, newItem: ShoppingList): Boolean {
        return oldItem.shoppingListId == newItem.shoppingListId
    }

    override fun areContentsTheSame(oldItem: ShoppingList, newItem: ShoppingList): Boolean {
        return oldItem.shoppingListName == newItem.shoppingListName &&
                oldItem.isFavorite == newItem.isFavorite &&
                oldItem.isCompleted == newItem.isCompleted &&
                oldItem.manualSortIndex == newItem.manualSortIndex &&
                oldItem.updatedAt == newItem.updatedAt &&
                oldItem.createdAt == newItem.createdAt
    }
}