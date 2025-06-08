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
    private var listSnapshotBeforeDrag: List<ShoppingList>? = null


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
        val listForBind = if (dragInProgress && internalList.isNotEmpty()) internalList else currentList
        if (position < 0 || position >= listForBind.size) return
        val shoppingList = listForBind[position]

        holder.bind(shoppingList)
        holder.itemView.setOnClickListener {
            onItemClicked(shoppingList)
        }
    }

    override fun getItemCount(): Int {
        return if (dragInProgress && internalList.isNotEmpty()) internalList.size else super.getItemCount()
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
        listSnapshotBeforeDrag = null
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
        return oldItem == newItem
    }
}